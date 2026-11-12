package com.zzynes.sarcophag.WATER;

import com.zzynes.sarcophag.WATER.mixin.FlowingFluidAccess;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

// конечная вода: объём хранится по клеткам, всё остальное считается от него
public final class FiniteWaterManager {
    public static final int BUCKET_UNITS = 10;
    public static final int MILLIBUCKETS_PER_UNIT = 100;
    public static final int BUCKET_MILLIBUCKETS = BUCKET_UNITS * MILLIBUCKETS_PER_UNIT;
    private static final int MAX_UNITS = BUCKET_MILLIBUCKETS;
    private static final int MAX_BODY_UNITS = 20000 * MILLIBUCKETS_PER_UNIT;
    // потолок по клеткам скана, на большом озере без него сервер ложится
    private static final int MAX_SCAN_CELLS = 40000;
    // и по времени тоже - обход может быть мелким, но дорогим
    private static final long SCAN_BUDGET_NANOS = 3_000_000L;
    private static final int EQUALIZE_STEPS = 16;
    // поверхность выравниваем порциями, за тик столько успеваем
    private static final int EQUALIZE_MAX_CELLS = 512;
    private static final int EQUALIZE_MAX_MOVES = 16;
    private static final int EQUALIZE_TASKS_PER_TICK = 2;
    private static final int PRESSURE_SEARCHES_PER_TICK = 2;
    private static final long EQUALIZE_COOLDOWN_TICKS = 10L;
    private static final long EQUALIZE_CHANGED_COOLDOWN_TICKS = 4L;
    // Вода считается раз в 2 тика, как в ванили
    private static final int TICK_DELAY = 2;
    private static final long LARGE_HINT_TICKS = 100L;
    private static final int COMPONENT_WAKE_MAX_CELLS = 512;
    private static final long COMPONENT_WAKE_INTERVAL_TICKS = 10L;
    private static final long SAFETY_SCAN_INTERVAL_TICKS = 10L;
    //todo: можно поднять, но пока tps важнее
    private static final int SAFETY_SCAN_CELLS_PER_RUN = 256;
    private static final int SAFETY_WAKE_PER_RUN = 64;
    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    // кэш по уровням, weak - чтобы не течь при выходе из мира
    private static final Map<Level, FiniteWaterData.Memory> CLIENT_MEMORY = new WeakHashMap<>();

    private FiniteWaterManager() {
    }

    public static void activateAround(Level level, BlockPos changedPos, EnumSet<Direction> notifiedSides) {
        Session session = session(level);
        boolean externalChange = kindAt(level, changedPos) == null;
        activate(level, changedPos, session, externalChange);
        for (Direction direction : notifiedSides) {
            activate(level, changedPos.relative(direction), session, externalChange);
        }
    }

    public static boolean isFiniteAt(Level level, BlockPos pos) {
        FiniteFluidKind kind = kindAt(level, pos);
        if (kind == null) {
            return false;
        }
        Session session = session(level, kind);
        if (session.memory.finiteCells.contains(pos.asLong())) {
            return true;
        }
        return classify(level, pos, session) == BodyKind.FINITE;
    }

    public static boolean canAddBucket(Level level, BlockPos pos) {
        return canAddFluidUnits(level, pos, BUCKET_UNITS, true, FiniteFluidKind.WATER);
    }

    public static boolean canAddWaterUnits(Level level, BlockPos pos, int units) {
        return canAddFluidUnits(level, pos, units, false, FiniteFluidKind.WATER);
    }

    public static boolean canAddWaterMillibuckets(Level level, BlockPos pos, int millibuckets) {
        return canAddFluidMillibuckets(level, pos, millibuckets, FiniteFluidKind.WATER);
    }

    public static boolean canAddFluidMillibuckets(Level level, BlockPos pos, int millibuckets, FiniteFluidKind kind) {
        if (millibuckets <= 0 || !isOpenFluid(level, pos, kind)) {
            return false;
        }
        Session session = session(level, kind);
        Long2IntOpenHashMap component = collectComponent(level, pos, session);
        if (component == null) {
            return false;
        }
        int requested = Math.min(millibuckets, BUCKET_MILLIBUCKETS);
        Long2IntOpenHashMap planned = plannedAddExact(level, component, session, requested);
        return componentTotal(planned) > componentTotal(component);
    }

    private static boolean canAddFluidUnits(Level level, BlockPos pos, int units, boolean requireAll, FiniteFluidKind kind) {
        if (units <= 0 || !isOpenFluid(level, pos, kind)) {
            return false;
        }
        Session session = session(level, kind);
        Long2IntOpenHashMap component = collectComponent(level, pos, session);
        if (component == null) {
            return false;
        }
        int requested = Math.min(units, BUCKET_UNITS);
        Long2IntOpenHashMap planned = plannedAdd(level, component, session, requested * MILLIBUCKETS_PER_UNIT);
        int added = (componentTotal(planned) - componentTotal(component)) / MILLIBUCKETS_PER_UNIT;
        return requireAll ? added >= requested : added > 0;
    }

    public static boolean canPlaceWaterUnits(Level level, BlockPos pos, int units) {
        return canPlaceFluidUnits(level, pos, units, FiniteFluidKind.WATER);
    }

    public static boolean canPlaceFluidUnits(Level level, BlockPos pos, int units, FiniteFluidKind kind) {
        if (units <= 0 || !level.isLoaded(pos)) {
            return false;
        }
        if (isOpenFluid(level, pos, kind)) {
            return canAddFluidUnits(level, pos, units, false, kind) || !isFiniteAt(level, pos);
        }
        return canPlaceIntoEmpty(level, pos, kind);
    }

    public static boolean canPlaceWaterMillibuckets(Level level, BlockPos pos, int millibuckets) {
        return canPlaceFluidMillibuckets(level, pos, millibuckets, FiniteFluidKind.WATER);
    }

    public static boolean canPlaceFluidMillibuckets(Level level, BlockPos pos, int millibuckets, FiniteFluidKind kind) {
        if (millibuckets <= 0 || !level.isLoaded(pos)) {
            return false;
        }
        if (isOpenFluid(level, pos, kind)) {
            return canAddFluidMillibuckets(level, pos, millibuckets, kind) || !isFiniteAt(level, pos);
        }
        return canPlaceIntoEmpty(level, pos, kind);
    }

    public static boolean tryAddBucket(Level level, BlockPos pos) {
        return tryAddWaterUnits(level, pos, BUCKET_UNITS) >= BUCKET_UNITS;
    }

    public static int tryAddWaterUnits(Level level, BlockPos pos, int units) {
        return tryAddFluidUnits(level, pos, units, FiniteFluidKind.WATER);
    }

    public static int tryAddFluidUnits(Level level, BlockPos pos, int units, FiniteFluidKind kind) {
        if (level.isClientSide || units <= 0 || !isOpenFluid(level, pos, kind)) {
            return 0;
        }
        Session session = session(level, kind);
        Long2IntOpenHashMap component = collectComponent(level, pos, session);
        if (component == null) {
            return 0;
        }
        Long2IntOpenHashMap planned = plannedAdd(level, component, session, Math.min(units, BUCKET_UNITS) * MILLIBUCKETS_PER_UNIT);
        int added = (componentTotal(planned) - componentTotal(component)) / MILLIBUCKETS_PER_UNIT;
        if (added > 0) {
            applyVolumes(level, component, planned, session);
        }
        return added;
    }

    public static int tryAddWaterMillibuckets(Level level, BlockPos pos, int millibuckets) {
        return tryAddFluidMillibuckets(level, pos, millibuckets, FiniteFluidKind.WATER);
    }

    public static int tryAddFluidMillibuckets(Level level, BlockPos pos, int millibuckets, FiniteFluidKind kind) {
        if (level.isClientSide || millibuckets <= 0 || !isOpenFluid(level, pos, kind)) {
            return 0;
        }
        Session session = session(level, kind);
        Long2IntOpenHashMap component = collectComponent(level, pos, session);
        if (component == null) {
            return 0;
        }
        Long2IntOpenHashMap planned = plannedAddExact(level, component, session, Math.min(millibuckets, BUCKET_MILLIBUCKETS));
        int added = componentTotal(planned) - componentTotal(component);
        if (added > 0) {
            applyVolumes(level, component, planned, session);
        }
        return added;
    }

    public static int tryPlaceWaterUnits(Level level, BlockPos pos, int units) {
        return tryPlaceFluidUnits(level, pos, units, FiniteFluidKind.WATER);
    }

    public static int tryPlaceFluidUnits(Level level, BlockPos pos, int units, FiniteFluidKind fluidKind) {
        if (level.isClientSide || units <= 0 || !level.isLoaded(pos)) {
            return 0;
        }
        int requested = Math.min(units, BUCKET_UNITS);
        if (isOpenFluid(level, pos, fluidKind)) {
            Session session = session(level, fluidKind);
            BodyKind kind = classify(level, pos, session);
            if (kind == BodyKind.LARGE) {
                return requested;
            }
            if (kind != BodyKind.FINITE) {
                return 0;
            }
            return tryAddFluidUnits(level, pos, requested, fluidKind);
        }
        if (!canPlaceIntoEmpty(level, pos, fluidKind)) {
            return 0;
        }
        Session session = session(level, fluidKind);
        setUnits(level, pos, requested * MILLIBUCKETS_PER_UNIT, null, session);
        scheduleAround(level, pos);
        return requested;
    }

    public static int tryPlaceWaterMillibuckets(Level level, BlockPos pos, int millibuckets) {
        return tryPlaceFluidMillibuckets(level, pos, millibuckets, FiniteFluidKind.WATER);
    }

    public static int tryPlaceFluidMillibuckets(Level level, BlockPos pos, int millibuckets, FiniteFluidKind fluidKind) {
        if (level.isClientSide || millibuckets <= 0 || !level.isLoaded(pos)) {
            return 0;
        }
        int requested = Math.min(millibuckets, BUCKET_MILLIBUCKETS);
        if (isOpenFluid(level, pos, fluidKind)) {
            Session session = session(level, fluidKind);
            BodyKind kind = classify(level, pos, session);
            if (kind == BodyKind.LARGE) {
                return requested;
            }
            if (kind != BodyKind.FINITE) {
                return 0;
            }
            return tryAddFluidMillibuckets(level, pos, requested, fluidKind);
        }
        if (!canPlaceIntoEmpty(level, pos, fluidKind)) {
            return 0;
        }
        Session session = session(level, fluidKind);
        setUnits(level, pos, requested, null, session);
        scheduleAround(level, pos);
        return requested;
    }

    public static boolean tryPickupBucket(Level level, BlockPos pos) {
        return tryPickupWaterUnits(level, pos, BUCKET_UNITS) >= BUCKET_UNITS;
    }

    public static int tryPickupWaterUnits(Level level, BlockPos pos, int maxUnits) {
        return tryPickupFluidUnits(level, pos, maxUnits, FiniteFluidKind.WATER);
    }

    public static int tryPickupFluidUnits(Level level, BlockPos pos, int maxUnits, FiniteFluidKind kind) {
        int requested = Math.min(maxUnits, BUCKET_UNITS) * MILLIBUCKETS_PER_UNIT;
        return tryPickupFluid(level, pos, requested, true, kind) / MILLIBUCKETS_PER_UNIT;
    }

    public static int tryPickupWaterMillibuckets(Level level, BlockPos pos, int maxMillibuckets) {
        return tryPickupFluidMillibuckets(level, pos, maxMillibuckets, FiniteFluidKind.WATER);
    }

    public static int tryPickupFluidMillibuckets(Level level, BlockPos pos, int maxMillibuckets, FiniteFluidKind kind) {
        return tryPickupFluid(level, pos, maxMillibuckets, false, kind);
    }

    private static int tryPickupFluid(Level level, BlockPos pos, int maxMillibuckets, boolean wholePackets, FiniteFluidKind kind) {
        if (level.isClientSide || maxMillibuckets <= 0 || !isOpenFluid(level, pos, kind)) {
            return 0;
        }
        Session session = session(level, kind);
        BodyKind bodyKind = classify(level, pos, session);
        int requested = Math.min(maxMillibuckets, BUCKET_MILLIBUCKETS);
        if (bodyKind == BodyKind.LARGE) {
            return requested;
        }
        if (bodyKind != BodyKind.FINITE) {
            return 0;
        }
        Long2IntOpenHashMap component = collectComponent(level, pos, session);
        if (component == null) {
            return 0;
        }
        int total = componentTotal(component);
        int available = wholePackets ? total / MILLIBUCKETS_PER_UNIT * MILLIBUCKETS_PER_UNIT : total;
        int removeUnits = Math.min(requested, available);
        if (removeUnits <= 0) {
            return 0;
        }
        Long2IntOpenHashMap desired = new Long2IntOpenHashMap(component);
        desired.defaultReturnValue(0);
        int remaining = removeUnits;
        while (remaining > 0) {
            long bestKey = 0L;
            int bestSurface = Integer.MIN_VALUE;
            boolean found = false;
            for (long key : shuffledKeys(desired, level)) {
                int units = desired.get(key);
                if (units <= 0) {
                    continue;
                }
                int surface = surface(key, units);
                if (!found || surface > bestSurface) {
                    bestKey = key;
                    bestSurface = surface;
                    found = true;
                }
            }
            if (!found) {
                return 0;
            }
            int amount = Math.min(MILLIBUCKETS_PER_UNIT, Math.min(remaining, desired.get(bestKey)));
            int next = desired.get(bestKey) - amount;
            if (next <= 0) {
                desired.remove(bestKey);
            } else {
                desired.put(bestKey, next);
            }
            remaining -= amount;
        }
        applyVolumes(level, component, desired, session);
        return removeUnits;
    }

    public static BlockPos findBucketPickupPos(Level level, Player player, BlockHitResult hit, boolean scanCells) {
        return findBucketPickupPos(level, player, hit, scanCells, FiniteFluidKind.WATER);
    }

    public static BlockPos findBucketPickupPos(Level level, Player player, BlockHitResult hit, boolean scanCells, FiniteFluidKind kind) {
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hit.getBlockPos();
            if (isOpenFluid(level, hitPos, kind)) {
                return hitPos.immutable();
            }
            BlockPos adjacent = hitPos.relative(hit.getDirection());
            if (isOpenFluid(level, adjacent, kind)) {
                return adjacent.immutable();
            }
        }
        if (!scanCells) {
            return null;
        }
        double reach = player.isCreative() ? 5.0D : 4.5D;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 view = player.getViewVector(1.0F);
        HitResult solidHit = player.pick(reach, 1.0F, false);
        double limit = reach;
        if (solidHit.getType() == HitResult.Type.BLOCK) {
            limit = Math.min(limit, eye.distanceTo(solidHit.getLocation()) + 0.0625D);
        }
        BlockPos previous = null;
        for (double distance = 0.0D; distance <= limit; distance += 0.0625D) {
            BlockPos pos = BlockPos.containing(eye.add(view.scale(distance)));
            if (pos.equals(previous)) {
                continue;
            }
            previous = pos;
            if (isOpenFluid(level, pos, kind)) {
                return pos.immutable();
            }
        }
        return null;
    }

    public static boolean handleTick(Level level, BlockPos pos, FluidState state) {
        FiniteFluidKind stateKind = kindOf(state);
        if (stateKind == null) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        FiniteFluidKind currentKind = kindAt(level, pos);
        Session session = session(level, currentKind == null ? stateKind : currentKind);
        if (!isOpenFluid(level, pos, session.kind())) {
            long key = pos.asLong();
            boolean changed = session.memory.finiteCells.remove(key);
            changed |= session.memory.volumes.remove(key) != 0;
            session.memory.largeCells.remove(key);
            if (changed) {
                session.markDirty();
            }
            return true;
        }
        BodyKind kind;
        if (session.memory.finiteCells.contains(pos.asLong()) && !hasAdjacentUnknownWater(level, pos, session)) {
            kind = BodyKind.FINITE;
        } else {
            kind = classify(level, pos, session);
        }
        if (kind == BodyKind.FINITE) {
            processCell(level, pos, session);
        } else if (kind == BodyKind.LARGE) {
            processInfiniteCell(level, pos, session);
        }
        return true;
    }

    private static void activate(Level level, BlockPos pos, Session baseSession, boolean externalChange) {
        FiniteFluidKind kind = kindAt(level, pos);
        if (!level.isLoaded(pos) || kind == null) {
            return;
        }
        Session session = baseSession.withKind(kind);
        long key = pos.asLong();
        session.memory.equalizationCooldown.remove(key);
        schedule(level, pos, 1);
        if (session.memory.finiteCells.contains(key)) {
            if (externalChange) {
                wakeComponent(level, pos, session);
            }
            return;
        }
        if (session.memory.largeCells.get(key) > level.getGameTime()) {
            return;
        }
        if (classify(level, pos, session) == BodyKind.FINITE && externalChange) {
            wakeComponent(level, pos, session);
        }
    }

    private static void wakeComponent(Level level, BlockPos start, Session session) {
        long now = level.getGameTime();
        if (session.memory.componentWakeAt.get(start.asLong()) > now - COMPONENT_WAKE_INTERVAL_TICKS) {
            return;
        }
        LongOpenHashSet visited = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);
        while (!queue.isEmpty() && visited.size() <= COMPONENT_WAKE_MAX_CELLS) {
            BlockPos current = queue.removeFirst();
            long key = current.asLong();
            session.memory.componentWakeAt.put(key, now);
            session.memory.equalizationCooldown.remove(key);
            schedule(level, current, 1);
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                long neighborKey = neighbor.asLong();
                if (!visited.contains(neighborKey)
                        && level.isLoaded(neighbor)
                        && isOpenFluid(level, neighbor, session.kind())) {
                    visited.add(neighborKey);
                    queue.add(neighbor);
                }
            }
        }
    }

    public static void tickSafetyWake(ServerLevel level) {
        Session baseSession = session(level);
        long now = level.getGameTime();
        if (baseSession.memory.safetyScanTick != Long.MIN_VALUE
                && now - baseSession.memory.safetyScanTick < SAFETY_SCAN_INTERVAL_TICKS) {
            return;
        }
        baseSession.memory.safetyScanTick = now;
        if (baseSession.memory.finiteCells.isEmpty()) {
            return;
        }
        long[] cells = baseSession.memory.finiteCells.toLongArray();
        int index = baseSession.memory.safetyScanOffset;
        if (index < 0 || index >= cells.length) {
            index = 0;
        }
        int scanned = 0;
        int woken = 0;
        while (scanned < SAFETY_SCAN_CELLS_PER_RUN && scanned < cells.length && woken < SAFETY_WAKE_PER_RUN) {
            long key = cells[index];
            index = (index + 1) % cells.length;
            scanned++;
            BlockPos pos = BlockPos.of(key);
            if (!level.isLoaded(pos)) {
                continue;
            }
            FiniteFluidKind kind = kindAt(level, pos);
            if (kind == null) {
                continue;
            }
            Session session = baseSession.withKind(kind);
            if (needsSafetyWake(level, pos, session)) {
                session.memory.equalizationCooldown.remove(key);
                schedule(level, pos, 1);
                woken++;
            }
        }
        baseSession.memory.safetyScanOffset = index;
    }

    private static boolean needsSafetyWake(Level level, BlockPos pos, Session session) {
        int units = volumeAt(level, pos, session, false);
        if (units <= 0) {
            return false;
        }
        if (canDrainDown(level, pos, session)) {
            return true;
        }
        int ownSurface = pos.getY() * MAX_UNITS + columnHead(level, pos, session);
        boolean surfaceCell = volumeAt(level, pos.above(), session, false) <= 0;
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.isLoaded(neighbor)) {
                continue;
            }
            if (isOpenFluid(level, neighbor, session.kind())) {
                int neighborSurface = neighbor.getY() * MAX_UNITS + columnHead(level, neighbor, session);
                if (Math.abs(neighborSurface - ownSurface) > 1) {
                    return true;
                }
            } else if (surfaceCell
                    && kindAt(level, neighbor) == null
                    && volumeAt(level, neighbor, session, false) <= 0
                    && canEnter(level, pos, direction, neighbor)) {
                int ownHead = ownSurface - pos.getY() * MAX_UNITS;
                int requiredDifference = ownHead > units ? MILLIBUCKETS_PER_UNIT : 2 * MILLIBUCKETS_PER_UNIT;
                if (ownHead - sideTargetHead(level, neighbor, 0, session) >= requiredDifference) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BodyKind classify(Level level, BlockPos start, Session session) {
        if (!isOpenFluid(level, start, session.kind())) {
            long key = start.asLong();
            boolean changed = session.memory.finiteCells.remove(key);
            changed |= session.memory.largeCells.remove(key) != Long.MIN_VALUE;
            changed |= session.memory.volumes.remove(key) != 0;
            if (changed) {
                session.markDirty();
            }
            return BodyKind.NOT_WATER;
        }
        long now = level.getGameTime();
        if (session.memory.largeCells.get(start.asLong()) > now) {
            return BodyKind.LARGE;
        }

        LongOpenHashSet visited = new LongOpenHashSet();
        Long2IntOpenHashMap scannedVolumes = new Long2IntOpenHashMap();
        scannedVolumes.defaultReturnValue(0);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);
        int volume = 0;
        boolean overLimit = false;
        boolean scanLimit = false;
        boolean grounded = hasGroundedColumn(level, start, session);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!isOpenFluid(level, pos, session.kind())) {
                continue;
            }
            long key = pos.asLong();
            if (session.memory.largeCells.get(key) > now) {
                overLimit = true;
            }
            if (!grounded) {
                BlockPos below = pos.below();
                grounded = level.isLoaded(below)
                        && !isOpenFluid(level, below, session.kind())
                        && !canDrainDown(level, pos, session);
            }
            int cellUnits = volumeAt(level, pos, session, false);
            if (cellUnits > 0) {
                scannedVolumes.put(key, cellUnits);
            }
            if (!overLimit) {
                volume += cellUnits;
                if (volume >= MAX_BODY_UNITS) {
                    overLimit = true;
                }
            }
            if (visited.size() > MAX_SCAN_CELLS) {
                scanLimit = true;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                long neighborKey = neighbor.asLong();
                if (visited.contains(neighborKey) || !level.isLoaded(neighbor) || !isOpenFluid(level, neighbor, session.kind())) {
                    continue;
                }
                if (scanLimit || (overLimit && grounded && !session.memory.finiteCells.contains(neighborKey))) {
                    continue;
                }
                visited.add(neighborKey);
                queue.add(neighbor);
            }
        }

        if (scanLimit || (overLimit && grounded)) {
            LongIterator iterator = visited.iterator();
            while (iterator.hasNext()) {
                long cell = iterator.nextLong();
                session.memory.finiteCells.remove(cell);
                session.memory.largeCells.put(cell, now + LARGE_HINT_TICKS);
                session.memory.volumes.remove(cell);
            }
            session.markDirty();
            return BodyKind.LARGE;
        }

        session.memory.finiteCells.addAll(visited);
        LongIterator iterator = visited.iterator();
        while (iterator.hasNext()) {
            session.memory.largeCells.remove(iterator.nextLong());
        }
        session.memory.volumes.putAll(scannedVolumes);
        session.markDirty();
        return BodyKind.FINITE;
    }

    private static boolean hasGroundedColumn(Level level, BlockPos start, Session session) {
        BlockPos cursor = start;
        for (int i = 0; i < 512; i++) {
            if (!level.isLoaded(cursor) || !isOpenFluid(level, cursor, session.kind())) {
                return false;
            }
            BlockPos below = cursor.below();
            if (!level.isLoaded(below)) {
                return false;
            }
            if (!isOpenFluid(level, below, session.kind())) {
                return !canDrainDown(level, cursor, session);
            }
            cursor = below;
        }
        return false;
    }

    private static Long2IntOpenHashMap collectComponent(Level level, BlockPos start, Session session) {
        if (!isOpenFluid(level, start, session.kind()) || classify(level, start, session) != BodyKind.FINITE) {
            return null;
        }
        LongOpenHashSet visited = new LongOpenHashSet();
        Long2IntOpenHashMap component = new Long2IntOpenHashMap();
        component.defaultReturnValue(0);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);
        int total = 0;
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        int checked = 0;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if ((++checked & 63) == 0 && System.nanoTime() > deadline) {
                return null;
            }
            if (!isOpenFluid(level, pos, session.kind())) {
                continue;
            }
            int units = volumeAt(level, pos, session, true);
            if (units <= 0) {
                continue;
            }
            component.put(pos.asLong(), units);
            total += units;
            if (total >= MAX_BODY_UNITS || visited.size() > MAX_SCAN_CELLS) {
                return null;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                long neighborKey = neighbor.asLong();
                if (!visited.contains(neighborKey)
                        && level.isLoaded(neighbor)
                        && isOpenFluid(level, neighbor, session.kind())) {
                    visited.add(neighborKey);
                    queue.add(neighbor);
                }
            }
        }
        return component;
    }

    private static Long2IntOpenHashMap plannedAdd(Level level, Long2IntOpenHashMap component, Session session, int unitsToAdd) {
        Long2IntOpenHashMap desired = new Long2IntOpenHashMap(component);
        desired.defaultReturnValue(0);
        int remainingToAdd = unitsToAdd;
        while (remainingToAdd >= MILLIBUCKETS_PER_UNIT) {
            long bestKey = 0L;
            int bestSurface = Integer.MAX_VALUE;
            boolean found = false;
            for (long key : shuffledKeys(desired, level)) {
                int units = desired.get(key);
                if (units <= 0 || units > MAX_UNITS - MILLIBUCKETS_PER_UNIT) {
                    continue;
                }
                int surface = surface(key, units);
                if (!found || surface < bestSurface) {
                    bestKey = key;
                    bestSurface = surface;
                    found = true;
                }
            }

            BlockPos bestExpansion = null;
            int bestExpansionSurface = Integer.MAX_VALUE;
            for (BlockPos expansion : expansionCandidates(level, desired, session)) {
                int expansionSurface = expansion.getY() * MAX_UNITS;
                if (expansionSurface < bestExpansionSurface) {
                    bestExpansion = expansion;
                    bestExpansionSurface = expansionSurface;
                }
            }

            boolean useExpansion = bestExpansion != null
                    && (!found
                    || bestExpansionSurface < bestSurface
                    || bestExpansionSurface == bestSurface && level.getRandom().nextBoolean());
            if (useExpansion) {
                desired.put(bestExpansion.asLong(), MILLIBUCKETS_PER_UNIT);
                remainingToAdd -= MILLIBUCKETS_PER_UNIT;
            } else if (found) {
                desired.put(bestKey, desired.get(bestKey) + MILLIBUCKETS_PER_UNIT);
                remainingToAdd -= MILLIBUCKETS_PER_UNIT;
            } else {
                break;
            }
        }
        return desired;
    }

    private static Long2IntOpenHashMap plannedAddExact(Level level, Long2IntOpenHashMap component, Session session, int millibucketsToAdd) {
        int packetAmount = millibucketsToAdd / MILLIBUCKETS_PER_UNIT * MILLIBUCKETS_PER_UNIT;
        Long2IntOpenHashMap desired = plannedAdd(level, component, session, packetAmount);
        int remaining = millibucketsToAdd - (componentTotal(desired) - componentTotal(component));
        while (remaining > 0) {
            long bestKey = 0L;
            int bestSurface = Integer.MAX_VALUE;
            boolean found = false;
            for (long key : shuffledKeys(desired, level)) {
                int units = desired.get(key);
                if (units <= 0 || units >= MAX_UNITS) {
                    continue;
                }
                int surface = surface(key, units);
                if (!found || surface < bestSurface) {
                    bestKey = key;
                    bestSurface = surface;
                    found = true;
                }
            }
            if (found) {
                int amount = Math.min(remaining, MAX_UNITS - desired.get(bestKey));
                desired.put(bestKey, desired.get(bestKey) + amount);
                remaining -= amount;
                continue;
            }
            BlockPos bestExpansion = null;
            int bestExpansionY = Integer.MAX_VALUE;
            for (BlockPos expansion : expansionCandidates(level, desired, session)) {
                if (expansion.getY() < bestExpansionY) {
                    bestExpansion = expansion;
                    bestExpansionY = expansion.getY();
                }
            }
            if (bestExpansion == null) {
                break;
            }
            int amount = Math.min(remaining, MAX_UNITS);
            desired.put(bestExpansion.asLong(), amount);
            remaining -= amount;
        }
        return desired;
    }

    private static List<BlockPos> expansionCandidates(Level level, Long2IntOpenHashMap desired, Session session) {
        LongOpenHashSet seen = new LongOpenHashSet();
        List<BlockPos> candidates = new ArrayList<>();
        for (long key : desired.keySet()) {
            BlockPos from = BlockPos.of(key);
            for (Direction direction : Direction.values()) {
                BlockPos target = from.relative(direction);
                long targetKey = target.asLong();
                if (desired.containsKey(targetKey)
                        || seen.contains(targetKey)
                        || kindAt(level, target) != null
                        || !canEnter(level, from, direction, target)) {
                    continue;
                }
                seen.add(targetKey);
                candidates.add(target.immutable());
            }
        }
        Collections.shuffle(candidates, new Random(level.getRandom().nextLong()));
        return candidates;
    }

    private static void applyVolumes(Level level, Long2IntOpenHashMap original, Long2IntOpenHashMap desired, Session session) {
        LongOpenHashSet keys = new LongOpenHashSet(original.keySet());
        keys.addAll(desired.keySet());
        LongIterator iterator = keys.iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            int oldUnits = original.get(key);
            int newUnits = desired.get(key);
            if (oldUnits == newUnits) {
                continue;
            }
            BlockPos pos = BlockPos.of(key);
            Direction entryDirection = oldUnits == 0 ? entryDirectionForNewCell(original, pos) : null;
            setUnits(level, pos, newUnits, entryDirection, session);
            scheduleAround(level, pos);
        }
    }

    private static Direction entryDirectionForNewCell(Long2IntOpenHashMap original, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (original.containsKey(pos.relative(direction.getOpposite()).asLong())) {
                return direction;
            }
        }
        return null;
    }

    private static List<Long> shuffledKeys(Long2IntOpenHashMap volumes, Level level) {
        List<Long> keys = new ArrayList<>(volumes.keySet());
        Collections.shuffle(keys, new Random(level.getRandom().nextLong()));
        return keys;
    }

    private static int componentTotal(Long2IntOpenHashMap component) {
        int total = 0;
        for (int units : component.values()) {
            total += units;
        }
        return total;
    }

    private static int surface(long key, int units) {
        return BlockPos.of(key).getY() * MAX_UNITS + units;
    }

    private static boolean hasAdjacentUnknownWater(Level level, BlockPos pos, Session session) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level.isLoaded(neighbor)
                    && isOpenFluid(level, neighbor, session.kind())
                    && !session.memory.finiteCells.contains(neighbor.asLong())) {
                return true;
            }
        }
        return false;
    }

    private static void processCell(Level level, BlockPos pos, Session session) {
        int units = volumeAt(level, pos, session, true);
        if (units <= 0) {
            boolean changed = session.memory.finiteCells.remove(pos.asLong());
            changed |= session.memory.volumes.remove(pos.asLong()) != 0;
            if (changed) {
                session.markDirty();
            }
            return;
        }
        if (level.getFluidState(pos).getValue(FlowingFluid.FALLING)) {
            setUnits(level, pos, units, null, session);
        }
        long now = level.getGameTime();
        if (session.memory.equalizedAt.get(pos.asLong()) == now
                || session.memory.flowOutAt.get(pos.asLong()) == now) {
            schedule(level, pos);
            return;
        }
        if (tryFlowDown(level, pos, units, session)) {
            return;
        }
        if (tryFlowSideways(level, pos, units, session)) {
            return;
        }
        if (tryPressureRise(level, pos, units, session)) {
            return;
        }
        if (units >= MAX_UNITS && trySaturatedPressureEqualize(level, pos, session)) {
            return;
        }
        tryEqualizeSurface(level, pos, session);
    }

    private static boolean changedByFlowThisTick(Session session, long key, long now) {
        return session.memory.equalizedAt.get(key) == now
                || session.memory.flowInAt.get(key) == now
                || session.memory.flowOutAt.get(key) == now;
    }

    private static boolean tryEqualizeSurface(Level level, BlockPos pos, Session session) {
        if (volumeAt(level, pos.above(), session, false) > 0) {
            return false;
        }
        BlockPos below = pos.below();
        if (canEnter(level, pos, Direction.DOWN, below)
                && volumeAt(level, below, session, false) < MAX_UNITS) {
            return false;
        }
        long now = level.getGameTime();
        if (changedByFlowThisTick(session, pos.asLong(), now)) {
            schedule(level, pos, 1);
            return false;
        }
        long cooldownUntil = session.memory.equalizationCooldown.get(pos.asLong());
        if (cooldownUntil > now) {
            long remaining = cooldownUntil - now;
            schedule(level, pos, (int) Math.max(1L, remaining));
            return false;
        }
        if (session.memory.equalizationTaskTick != now) {
            session.memory.equalizationTaskTick = now;
            session.memory.equalizationTasksThisTick = 0;
        }
        if (session.memory.equalizationTasksThisTick >= EQUALIZE_TASKS_PER_TICK) {
            schedule(level, pos, 2);
            return false;
        }
        session.memory.equalizationTasksThisTick++;
        Long2IntOpenHashMap fluids = collectEqualizationWindow(level, pos, session, now);
        if (fluids == null || fluids.size() < 2) {
            session.memory.equalizationCooldown.put(pos.asLong(), now + EQUALIZE_COOLDOWN_TICKS);
            return false;
        }
        for (long key : fluids.keySet()) {
            if (key != pos.asLong() && changedByFlowThisTick(session, key, now)) {
                schedule(level, pos, 1);
                return false;
            }
        }
        int y = pos.getY();
        int upperCount = 0;
        int lowerCount = 0;
        int total = 0;
        int min = MAX_UNITS;
        int max = 0;
        int wetUpperCount = 0;
        int wetLowerCount = 0;
        int wetTotal = 0;
        int wetMin = MAX_UNITS;
        int wetMax = 0;
        Long2IntOpenHashMap wetFluids = new Long2IntOpenHashMap();
        wetFluids.defaultReturnValue(0);
        for (var entry : fluids.long2IntEntrySet()) {
            BlockPos cell = BlockPos.of(entry.getLongKey());
            int units = entry.getIntValue();
            total += units;
            if (units > 0) {
                wetFluids.put(entry.getLongKey(), units);
                wetTotal += units;
            }
            if (cell.getY() == y) {
                upperCount++;
                min = Math.min(min, units);
                max = Math.max(max, units);
                if (units > 0) {
                    wetUpperCount++;
                    wetMin = Math.min(wetMin, units);
                    wetMax = Math.max(wetMax, units);
                }
            } else if (cell.getY() == y - 1) {
                lowerCount++;
                if (units > 0) {
                    wetLowerCount++;
                }
            }
        }
        if (upperCount == 0) {
            markEqualizationCooldown(session, fluids, now + EQUALIZE_COOLDOWN_TICKS);
            return false;
        }
        Long2IntOpenHashMap source = fluids;
        Long2IntOpenHashMap desired;
        if (max - min >= 2 * MILLIBUCKETS_PER_UNIT) {
            desired = steppedEqualizationTarget(fluids, y, upperCount, lowerCount, total);
            if (desired.equals(source) && wetUpperCount >= 2 && wetMax - wetMin > 1) {
                source = wetFluids;
                desired = steppedEqualizationTarget(source, y, wetUpperCount, wetLowerCount, wetTotal);
            }
        } else {
            if (wetUpperCount < 2 || wetMax - wetMin <= 1) {
                markEqualizationCooldown(session, fluids, now + EQUALIZE_COOLDOWN_TICKS);
                return false;
            }
            source = wetFluids;
            desired = steppedEqualizationTarget(source, y, wetUpperCount, wetLowerCount, wetTotal);
        }
        if (desired.equals(source)) {
            markEqualizationCooldown(session, fluids, now + EQUALIZE_COOLDOWN_TICKS);
            return false;
        }
        applyVolumes(level, source, desired, session);
        LongOpenHashSet keys = new LongOpenHashSet(source.keySet());
        keys.addAll(desired.keySet());
        LongIterator iterator = keys.iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            session.memory.equalizationCooldown.put(key, now + EQUALIZE_CHANGED_COOLDOWN_TICKS);
            if (source.get(key) != desired.get(key)) {
                session.memory.equalizedAt.put(key, now);
            }
        }
        return true;
    }

    private static Long2IntOpenHashMap collectEqualizationWindow(Level level, BlockPos start, Session session, long now) {
        Long2IntOpenHashMap fluids = new Long2IntOpenHashMap();
        fluids.defaultReturnValue(0);
        LongOpenHashSet frontier = new LongOpenHashSet();
        LongOpenHashSet next = new LongOpenHashSet();
        fluids.put(start.asLong(), volumeAt(level, start, session, true));
        frontier.add(start.asLong());
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        int checked = 0;
        for (int step = 0; step < EQUALIZE_STEPS; step++) {
            next.clear();
            LongIterator frontierIterator = frontier.iterator();
            while (frontierIterator.hasNext()) {
                BlockPos current = BlockPos.of(frontierIterator.nextLong());
                if ((++checked & 63) == 0 && System.nanoTime() > deadline) {
                    return fluids;
                }
                boolean walls = false;
                for (Direction direction : HORIZONTAL_DIRECTIONS) {
                    BlockPos neighbor = current.relative(direction);
                    long neighborKey = neighbor.asLong();
                    if (fluids.containsKey(neighborKey)) {
                        continue;
                    }
                    if (!level.isLoaded(neighbor) || !canEnter(level, current, direction, neighbor)) {
                        walls = true;
                        continue;
                    }
                    int units = volumeAt(level, neighbor, session, false);
                    fluids.put(neighborKey, units);
                    if (fluids.size() > EQUALIZE_MAX_CELLS) {
                        return fluids;
                    }
                    if (units > 0) {
                        next.add(neighborKey);
                    } else {
                        walls = true;
                    }
                }
                if (walls && current.getY() == start.getY()) {
                    BlockPos below = current.below();
                    long belowKey = below.asLong();
                    if (!fluids.containsKey(belowKey)
                            && level.isLoaded(below)
                            && canEnter(level, current, Direction.DOWN, below)) {
                        int units = volumeAt(level, below, session, false);
                        fluids.put(belowKey, units);
                        if (fluids.size() > EQUALIZE_MAX_CELLS) {
                            return fluids;
                        }
                        if (units > 0) {
                            next.add(belowKey);
                        }
                    }
                }
            }
            if (next.isEmpty()) {
                break;
            }
            frontier.clear();
            frontier.addAll(next);
        }
        return fluids;
    }

    private static Long2IntOpenHashMap steppedEqualizationTarget(Long2IntOpenHashMap fluids, int y, int upperCount, int lowerCount, int total) {
        Long2IntOpenHashMap target = new Long2IntOpenHashMap();
        target.defaultReturnValue(0);
        List<Long> upperKeys = new ArrayList<>();
        List<Long> lowerKeys = new ArrayList<>();
        for (long key : fluids.keySet()) {
            if (BlockPos.of(key).getY() == y) {
                upperKeys.add(key);
            } else {
                lowerKeys.add(key);
            }
        }
        upperKeys.sort((a, b) -> Integer.compare(fluids.get(b), fluids.get(a)));
        lowerKeys.sort((a, b) -> Integer.compare(fluids.get(b), fluids.get(a)));
        boolean bottom = lowerCount > 0 && total <= MAX_UNITS * lowerCount;
        if (bottom) {
            int mid = total / lowerCount;
            int remainder = total % lowerCount;
            for (int i = 0; i < lowerKeys.size(); i++) {
                target.put(lowerKeys.get(i).longValue(), mid + (i < remainder ? 1 : 0));
            }
            for (long key : upperKeys) {
                target.put(key, 0);
            }
        } else {
            for (long key : lowerKeys) {
                target.put(key, MAX_UNITS);
            }
            int remaining = total - MAX_UNITS * lowerCount;
            int mid = remaining / upperCount;
            int remainder = remaining % upperCount;
            for (int i = 0; i < upperKeys.size(); i++) {
                target.put(upperKeys.get(i).longValue(), mid + (i < remainder ? 1 : 0));
            }
        }
        Long2IntOpenHashMap desired = new Long2IntOpenHashMap(fluids);
        desired.defaultReturnValue(0);
        List<Long> donors = new ArrayList<>();
        List<Long> receivers = new ArrayList<>();
        for (long key : fluids.keySet()) {
            int current = fluids.get(key);
            int wanted = target.get(key);
            if (current > wanted) {
                donors.add(key);
            } else if (current < wanted) {
                receivers.add(key);
            }
        }
        donors.sort((a, b) -> Integer.compare(fluids.get(b) - target.get(b), fluids.get(a) - target.get(a)));
        receivers.sort((a, b) -> Integer.compare(target.get(b) - fluids.get(b), target.get(a) - fluids.get(a)));
        int moves = 0;
        int donorIndex = 0;
        int donorBudget = 0;
        for (long receiver : receivers) {
            if (moves >= EQUALIZE_MAX_MOVES) {
                break;
            }
            int receiverNeed = target.get(receiver) - desired.get(receiver);
            if (receiverNeed <= 0) {
                continue;
            }
            boolean dryReceiver = fluids.get(receiver) == 0;
            if (dryReceiver && receiverNeed < MILLIBUCKETS_PER_UNIT) {
                continue;
            }
            int receiverBudget = Math.min(MILLIBUCKETS_PER_UNIT, receiverNeed);
            while (receiverBudget > 0 && donorIndex < donors.size() && moves < EQUALIZE_MAX_MOVES) {
                long donor = donors.get(donorIndex);
                int donorAvailable = desired.get(donor) - target.get(donor);
                if (donorAvailable <= 0) {
                    donorIndex++;
                    donorBudget = 0;
                    continue;
                }
                if (donorBudget <= 0) {
                    donorBudget = Math.min(MILLIBUCKETS_PER_UNIT, donorAvailable);
                }
                if (dryReceiver && donorBudget < MILLIBUCKETS_PER_UNIT) {
                    donorIndex++;
                    donorBudget = 0;
                    continue;
                }
                int amount = Math.min(receiverBudget, Math.min(donorAvailable, donorBudget));
                if (amount <= 0) {
                    donorIndex++;
                    donorBudget = 0;
                    continue;
                }
                int donorNext = desired.get(donor) - amount;
                if (donorNext <= 0) {
                    desired.remove(donor);
                } else {
                    desired.put(donor, donorNext);
                }
                desired.put(receiver, desired.get(receiver) + amount);
                receiverBudget -= amount;
                donorBudget -= amount;
                moves++;
                if (donorBudget <= 0 || desired.get(donor) - target.get(donor) <= 0) {
                    donorIndex++;
                    donorBudget = 0;
                }
            }
        }
        return desired;
    }

    private static void markEqualizationCooldown(Session session, Long2IntOpenHashMap fluids, long until) {
        for (long key : fluids.keySet()) {
            session.memory.equalizationCooldown.put(key, until);
        }
    }

    private static void processInfiniteCell(Level level, BlockPos pos, Session session) {
        int units = volumeAt(level, pos, session, false);
        if (units <= 0) {
            return;
        }
        if (level.getFluidState(pos).getValue(FlowingFluid.FALLING)) {
            setUnits(level, pos, units, null, session, false);
        }
        if (units < MAX_UNITS) {
            return;
        }
        BlockPos below = pos.below();
        if (canEnter(level, pos, Direction.DOWN, below) && volumeAt(level, below, session, false) < MAX_UNITS) {
            setUnits(level, below, MAX_UNITS, Direction.DOWN, session, false);
            schedule(level, pos);
            schedule(level, below);
            return;
        }
        List<FlowCandidate> candidates = new ArrayList<>();
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos target = pos.relative(direction);
            if (!canEnter(level, pos, direction, target)) {
                continue;
            }
            int targetUnits = volumeAt(level, target, session, false);
            if (targetUnits < MAX_UNITS) {
                candidates.add(new FlowCandidate(target.immutable(), direction, targetUnits));
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        int bestHead = Integer.MAX_VALUE;
        for (FlowCandidate candidate : candidates) {
            bestHead = Math.min(bestHead, candidate.targetHead());
        }
        List<FlowCandidate> best = new ArrayList<>();
        for (FlowCandidate candidate : candidates) {
            if (candidate.targetHead() == bestHead) {
                best.add(candidate);
            }
        }
        FlowCandidate chosen = best.get(level.getRandom().nextInt(best.size()));
        setUnits(level, chosen.pos(), MAX_UNITS, chosen.direction(), session, false);
        schedule(level, pos);
        schedule(level, chosen.pos());
    }

    private static boolean tryFlowDown(Level level, BlockPos pos, int units, Session session) {
        BlockPos below = pos.below();
        if (!canEnter(level, pos, Direction.DOWN, below)) {
            return false;
        }
        int belowUnits = volumeAt(level, below, session, false);
        if (belowUnits >= MAX_UNITS) {
            return false;
        }
        if (session.memory.equalizedAt.get(below.asLong()) == level.getGameTime()) {
            schedule(level, pos);
            return true;
        }
        int move = Math.min(units, MAX_UNITS - belowUnits);
        if (move >= MILLIBUCKETS_PER_UNIT) {
            move -= move % MILLIBUCKETS_PER_UNIT;
            if (units >= 2 * MILLIBUCKETS_PER_UNIT && units - move < MILLIBUCKETS_PER_UNIT) {
                move -= MILLIBUCKETS_PER_UNIT;
            }
        }
        return move > 0 && transfer(level, pos, below, Direction.DOWN, move, session);
    }

    private static boolean tryFlowSideways(Level level, BlockPos pos, int units, Session session) {
        int ownHead = columnHead(level, pos, session);
        int ownDistanceToSink = -1;
        List<FlowCandidate> candidates = new ArrayList<>();
        boolean retryNextTick = false;
        long now = level.getGameTime();
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos target = pos.relative(direction);
            if (session.memory.equalizedAt.get(target.asLong()) == now
                    || session.memory.flowOutAt.get(target.asLong()) == now) {
                retryNextTick = true;
                continue;
            }
            if (!canEnter(level, pos, direction, target)) {
                continue;
            }
            int targetUnits = volumeAt(level, target, session, false);
            if (targetUnits >= MAX_UNITS) {
                continue;
            }
            int targetHead = sideTargetHead(level, target, targetUnits, session);
            int difference = ownHead - targetHead;
            int requiredDifference = ownHead > units ? MILLIBUCKETS_PER_UNIT : 2 * MILLIBUCKETS_PER_UNIT;
            if (difference >= requiredDifference) {
                candidates.add(new FlowCandidate(target.immutable(), direction, targetHead));
            } else if (difference >= MILLIBUCKETS_PER_UNIT && units > MILLIBUCKETS_PER_UNIT && targetUnits > 0) {
                if (ownDistanceToSink < 0) {
                    ownDistanceToSink = distanceToSink(level, pos, session);
                }
                if (distanceToSink(level, target, session) < ownDistanceToSink) {
                    candidates.add(new FlowCandidate(target.immutable(), direction, targetHead));
                }
            }
        }
        if (candidates.isEmpty()) {
            if (retryNextTick) {
                schedule(level, pos, 1);
            }
            return false;
        }
        int bestHead = Integer.MAX_VALUE;
        for (FlowCandidate candidate : candidates) {
            bestHead = Math.min(bestHead, candidate.targetHead());
        }
        List<FlowCandidate> best = new ArrayList<>();
        for (FlowCandidate candidate : candidates) {
            if (candidate.targetHead() == bestHead) {
                best.add(candidate);
            }
        }
        int difference = ownHead - bestHead;
        int totalMove = difference >= 2 * MILLIBUCKETS_PER_UNIT ? difference / 2 : MILLIBUCKETS_PER_UNIT;
        totalMove -= totalMove % MILLIBUCKETS_PER_UNIT;
        totalMove = Math.min(totalMove, units / MILLIBUCKETS_PER_UNIT * MILLIBUCKETS_PER_UNIT);
        if (totalMove <= 0) {
            return false;
        }
        Collections.shuffle(best, new Random(level.getRandom().nextLong()));
        int totalPackets = totalMove / MILLIBUCKETS_PER_UNIT;
        int baseMove = totalPackets / best.size();
        int remainder = totalPackets % best.size();
        boolean moved = false;
        for (int i = 0; i < best.size(); i++) {
            int amount = (baseMove + (i < remainder ? 1 : 0)) * MILLIBUCKETS_PER_UNIT;
            if (amount <= 0) {
                continue;
            }
            FlowCandidate candidate = best.get(i);
            int targetUnits = volumeAt(level, candidate.pos(), session, false);
            amount = Math.min(amount, (MAX_UNITS - targetUnits) / MILLIBUCKETS_PER_UNIT * MILLIBUCKETS_PER_UNIT);
            if (amount > 0 && transfer(level, pos, candidate.pos(), candidate.direction(), amount, session)) {
                moved = true;
            }
        }
        return moved;
    }

    private static int sideTargetHead(Level level, BlockPos target, int targetUnits, Session session) {
        BlockPos below = target.below();
        if (canEnter(level, target, Direction.DOWN, below)) {
            int belowUnits = volumeAt(level, below, session, false);
            if (belowUnits < MAX_UNITS) {
                return targetUnits + belowUnits - MAX_UNITS;
            }
        }
        return targetUnits > 0 ? columnHead(level, target, session) : 0;
    }

    private static boolean canDrainDown(Level level, BlockPos pos, Session session) {
        BlockPos below = pos.below();
        return canEnter(level, pos, Direction.DOWN, below)
                && volumeAt(level, below, session, false) < MAX_UNITS;
    }

    private static int distanceToSink(Level level, BlockPos start, Session session) {
        if (hasSinkAccess(level, start, session)) {
            return 0;
        }
        LongOpenHashSet visited = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);
        for (int distance = 1; distance <= 64 && !queue.isEmpty(); distance++) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                BlockPos current = queue.removeFirst();
                for (Direction direction : HORIZONTAL_DIRECTIONS) {
                    BlockPos neighbor = current.relative(direction);
                    long neighborKey = neighbor.asLong();
                    if (visited.contains(neighborKey)
                            || !level.isLoaded(neighbor)
                            || volumeAt(level, neighbor, session, false) <= 0) {
                        continue;
                    }
                    if (hasSinkAccess(level, neighbor, session)) {
                        return distance;
                    }
                    visited.add(neighborKey);
                    queue.add(neighbor);
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    private static boolean hasSinkAccess(Level level, BlockPos pos, Session session) {
        if (canDrainDown(level, pos, session)) {
            return true;
        }
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos target = pos.relative(direction);
            if (canEnter(level, pos, direction, target)
                    && (volumeAt(level, target, session, false) <= 0
                    || canDrainDown(level, target, session))) {
                return true;
            }
        }
        return false;
    }

    // вода лезет вверх только если снизу есть опора, иначе фонтан из ничего
    private static boolean tryPressureRise(Level level, BlockPos pos, int units, Session session) {
        if (units < MAX_UNITS) {
            return false;
        }
        BlockPos above = pos.above();
        for (int i = 0; i < 128 && level.isLoaded(above) && volumeAt(level, above, session, false) >= MAX_UNITS; i++) {
            above = above.above();
        }
        if (!level.isLoaded(above) || !canEnter(level, above.below(), Direction.UP, above)) {
            return false;
        }
        int aboveUnits = volumeAt(level, above, session, false);
        if (aboveUnits >= MAX_UNITS
                || session.memory.equalizedAt.get(above.asLong()) == level.getGameTime()
                || session.memory.flowOutAt.get(above.asLong()) == level.getGameTime()) {
            return false;
        }
        int ownHead = columnHead(level, pos, session);
        int ownSurface = pos.getY() * MAX_UNITS + ownHead;
        Direction bestDirection = null;
        BlockPos bestPos = null;
        int bestSurface = ownSurface + MILLIBUCKETS_PER_UNIT;
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.isLoaded(neighbor)
                    || !isOpenFluid(level, neighbor, session.kind())
                    || session.memory.equalizedAt.get(neighbor.asLong()) == level.getGameTime()
                    || session.memory.flowOutAt.get(neighbor.asLong()) == level.getGameTime()) {
                continue;
            }
            int neighborSurface = neighbor.getY() * MAX_UNITS + columnHead(level, neighbor, session);
            if (neighborSurface > bestSurface) {
                bestDirection = direction;
                bestPos = neighbor;
                bestSurface = neighborSurface;
            }
        }
        if (bestPos == null) {
            bestPos = findSaturatedPressureDonor(level, pos, ownSurface, session);
            if (bestPos != null) {
                bestSurface = bestPos.getY() * MAX_UNITS + columnHead(level, bestPos, session);
            }
        }
        if (bestPos == null) {
            return false;
        }
        int neighborUnits = volumeAt(level, bestPos, session, false);
        if (neighborUnits <= 0) {
            return false;
        }
        int move = Math.min(MILLIBUCKETS_PER_UNIT, (bestSurface - ownSurface + 1) / 2);
        int donorLimit = neighborUnits >= MAX_UNITS
                ? neighborUnits
                : neighborUnits / MILLIBUCKETS_PER_UNIT * MILLIBUCKETS_PER_UNIT;
        move = Math.min(move, Math.min(donorLimit, MAX_UNITS - aboveUnits));
        if (move <= 0) {
            return false;
        }
        setUnits(level, above, aboveUnits + move, Direction.UP, session);
        setUnits(level, bestPos, neighborUnits - move, bestDirection == null ? null : bestDirection.getOpposite(), session);
        long now = level.getGameTime();
        session.memory.flowInAt.put(above.asLong(), now);
        session.memory.flowOutAt.put(pos.asLong(), now);
        session.memory.flowOutAt.put(bestPos.asLong(), now);
        session.memory.equalizedAt.put(pos.asLong(), now);
        session.memory.equalizedAt.put(above.asLong(), now);
        session.memory.equalizedAt.put(bestPos.asLong(), now);
        schedule(level, pos);
        schedule(level, above);
        schedule(level, bestPos);
        schedule(level, bestPos.above());
        return true;
    }

    private static boolean consumePressureSearch(Level level, BlockPos start, Session session) {
        long now = level.getGameTime();
        if (session.memory.pressureSearchTick != now) {
            session.memory.pressureSearchTick = now;
            session.memory.pressureSearchesThisTick = 0;
        }
        if (session.memory.pressureSearchesThisTick >= PRESSURE_SEARCHES_PER_TICK) {
            schedule(level, start, 2);
            return false;
        }
        session.memory.pressureSearchesThisTick++;
        return true;
    }

    private static boolean trySaturatedPressureEqualize(Level level, BlockPos start, Session session) {
        if (!consumePressureSearch(level, start, session)) {
            return false;
        }
        long now = level.getGameTime();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongOpenHashSet evaluatedColumns = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);
        BlockPos donorNode = null;
        BlockPos receiverNode = null;
        BlockPos receiverAbove = null;
        int highestSurface = Integer.MIN_VALUE;
        int lowestSurface = Integer.MAX_VALUE;
        while (!queue.isEmpty() && visited.size() <= EQUALIZE_MAX_CELLS) {
            BlockPos current = queue.removeFirst();
            long columnKey = BlockPos.asLong(current.getX(), 0, current.getZ());
            if (!evaluatedColumns.contains(columnKey)) {
                BlockPos above = current.above();
                for (int i = 0; i < 128 && level.isLoaded(above) && volumeAt(level, above, session, false) >= MAX_UNITS; i++) {
                    above = above.above();
                }
                if (level.isLoaded(above)
                        && canEnter(level, above.below(), Direction.UP, above)
                        && !changedByFlowThisTick(session, current.asLong(), now)
                        && !changedByFlowThisTick(session, above.asLong(), now)) {
                    evaluatedColumns.add(columnKey);
                    int surface = current.getY() * MAX_UNITS + columnHead(level, current, session);
                    if (surface > highestSurface) {
                        highestSurface = surface;
                        donorNode = current.immutable();
                    }
                    if (surface < lowestSurface) {
                        lowestSurface = surface;
                        receiverNode = current.immutable();
                        receiverAbove = above.immutable();
                    }
                }
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                long neighborKey = neighbor.asLong();
                if (visited.contains(neighborKey)
                        || !level.isLoaded(neighbor)
                        || !canEnter(level, current, direction, neighbor)
                        || volumeAt(level, neighbor, session, false) < MAX_UNITS) {
                    continue;
                }
                visited.add(neighborKey);
                queue.add(neighbor);
            }
        }
        if (donorNode == null || receiverNode == null || receiverAbove == null) {
            return false;
        }
        if (donorNode.getX() == receiverNode.getX() && donorNode.getZ() == receiverNode.getZ()) {
            return false;
        }
        int difference = highestSurface - lowestSurface;
        if (difference <= 1) {
            return false;
        }
        int donorUnits = volumeAt(level, donorNode, session, false);
        int receiverUnits = volumeAt(level, receiverAbove, session, false);
        if (donorUnits <= 0 || receiverUnits >= MAX_UNITS) {
            return false;
        }
        int move = Math.min(MILLIBUCKETS_PER_UNIT, (difference + 1) / 2);
        move = Math.min(move, Math.min(donorUnits, MAX_UNITS - receiverUnits));
        if (move <= 0) {
            return false;
        }
        setUnits(level, receiverAbove, receiverUnits + move, Direction.UP, session);
        setUnits(level, donorNode, donorUnits - move, null, session);
        session.memory.flowInAt.put(receiverAbove.asLong(), now);
        session.memory.flowOutAt.put(donorNode.asLong(), now);
        session.memory.flowOutAt.put(receiverNode.asLong(), now);
        session.memory.flowOutAt.put(start.asLong(), now);
        session.memory.equalizedAt.put(donorNode.asLong(), now);
        session.memory.equalizedAt.put(receiverNode.asLong(), now);
        session.memory.equalizedAt.put(receiverAbove.asLong(), now);
        session.memory.equalizedAt.put(start.asLong(), now);
        schedule(level, start);
        schedule(level, donorNode);
        schedule(level, donorNode.above());
        schedule(level, receiverNode);
        schedule(level, receiverAbove);
        return true;
    }

    private static BlockPos findSaturatedPressureDonor(Level level, BlockPos start, int ownSurface, Session session) {
        long now = level.getGameTime();
        if (!consumePressureSearch(level, start, session)) {
            return null;
        }
        LongOpenHashSet visited = new LongOpenHashSet();
        LongOpenHashSet evaluatedColumns = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        evaluatedColumns.add(BlockPos.asLong(start.getX(), 0, start.getZ()));
        queue.add(start);
        BlockPos best = null;
        int bestSurface = ownSurface + 1;
        while (!queue.isEmpty() && visited.size() <= EQUALIZE_MAX_CELLS) {
            BlockPos current = queue.removeFirst();
            long columnKey = BlockPos.asLong(current.getX(), 0, current.getZ());
            if (!current.equals(start)
                    && !evaluatedColumns.contains(columnKey)
                    && !changedByFlowThisTick(session, current.asLong(), now)) {
                evaluatedColumns.add(columnKey);
                int surface = current.getY() * MAX_UNITS + columnHead(level, current, session);
                if (surface > bestSurface) {
                    bestSurface = surface;
                    best = current.immutable();
                }
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                long neighborKey = neighbor.asLong();
                if (visited.contains(neighborKey)
                        || !level.isLoaded(neighbor)
                        || !canEnter(level, current, direction, neighbor)
                        || volumeAt(level, neighbor, session, false) < MAX_UNITS) {
                    continue;
                }
                visited.add(neighborKey);
                queue.add(neighbor);
            }
        }
        return best;
    }

    private static boolean transfer(Level level, BlockPos from, BlockPos to, Direction direction, int units, Session session) {
        int fromUnits = volumeAt(level, from, session, true);
        int toUnits = volumeAt(level, to, session, true);
        if (units <= 0 || fromUnits < units || toUnits + units > MAX_UNITS) {
            return false;
        }
        setUnits(level, to, toUnits + units, direction, session);
        setUnits(level, from, fromUnits - units, direction.getOpposite(), session);
        long now = level.getGameTime();
        session.memory.flowOutAt.put(from.asLong(), now);
        session.memory.flowInAt.put(to.asLong(), now);
        if (direction == Direction.DOWN) {
            schedule(level, from);
            schedule(level, to);
        } else {
            scheduleHorizontalAround(level, from, 1);
            scheduleHorizontalAround(level, to, 1);
        }
        return true;
    }

    private static void setUnits(Level level, BlockPos pos, int units, Direction entryDirection, Session session) {
        setUnits(level, pos, units, entryDirection, session, true);
    }

    private static void setUnits(Level level, BlockPos pos, int units, Direction entryDirection, Session session, boolean trackFinite) {
        long key = pos.asLong();
        if (units <= 0) {
            boolean changed = session.memory.finiteCells.remove(key);
            changed |= session.memory.volumes.remove(key) != 0;
            session.memory.largeCells.remove(key);
            BlockState dryState = level.getBlockState(pos);
            if (session.kind() == FiniteFluidKind.WATER && dryState.getBlock() instanceof DrainBlock) {
                if (dryState.getValue(DrainBlock.LEVEL) != 0) {
                    level.setBlock(pos, dryState.setValue(DrainBlock.LEVEL, 0), 3);
                }
            } else if (isOpenFluid(level, pos, session.kind())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            if (changed) {
                session.markDirty();
            }
            return;
        }

        int clamped = Math.min(units, MAX_UNITS);
        boolean changed;
        if (trackFinite) {
            changed = session.memory.finiteCells.add(key);
            changed |= session.memory.volumes.put(key, clamped) != clamped;
            session.memory.largeCells.remove(key);
        } else {
            changed = session.memory.finiteCells.remove(key);
            changed |= session.memory.volumes.remove(key) != 0;
            session.memory.largeCells.put(key, level.getGameTime() + LARGE_HINT_TICKS);
        }
        FluidState newFluidState = fluidStateForUnits(session.kind(), clamped);
        BlockState oldState = level.getBlockState(pos);
        BlockState newState = newFluidState.createLegacyBlock();
        if (session.kind() == FiniteFluidKind.WATER && oldState.getBlock() instanceof DrainBlock) {
            int displayAmount = clamped >= MAX_UNITS
                    ? 8
                    : displayAmountForUnits(Math.max(1, Math.min(BUCKET_UNITS, clamped / MILLIBUCKETS_PER_UNIT)));
            BlockState drainState = oldState.setValue(DrainBlock.LEVEL, displayAmount);
            if (!oldState.equals(drainState)) {
                level.setBlock(pos, drainState, 3);
            }
        } else if (!oldState.equals(newState)) {
            if (entryDirection != null && !isOpenFluid(level, pos, session.kind()) && !oldState.isAir()) {
                ((FlowingFluidAccess) (Object) flowingFluid(session.kind())).sarcophag$spreadTo(level, pos, oldState, entryDirection, newFluidState);
            } else {
                level.setBlock(pos, newState, 3);
            }
        }
        if (changed) {
            session.markDirty();
        }
    }

    private static FluidState fluidStateForUnits(FiniteFluidKind kind, int units) {
        if (units >= MAX_UNITS) {
            return sourceFluid(kind).getSource(false);
        }
        int displayUnits = Math.max(1, Math.min(BUCKET_UNITS, units / MILLIBUCKETS_PER_UNIT));
        return flowingFluid(kind).getFlowing(displayAmountForUnits(displayUnits), false);
    }

    private static int displayAmountForUnits(int units) {
        return Math.max(1, Math.min(8, (units * 8 + 9) / 10));
    }

    private static int inferredUnits(FluidState state) {
        if (state.isSource()) {
            return MAX_UNITS;
        }
        int displayUnits = switch (state.getAmount()) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 5;
            case 5 -> 6;
            case 6 -> 7;
            case 7 -> 8;
            case 8 -> 9;
            default -> Math.max(1, Math.min(BUCKET_UNITS, state.getAmount()));
        };
        return displayUnits * MILLIBUCKETS_PER_UNIT;
    }

    private static int volumeAt(Level level, BlockPos pos, Session session, boolean remember) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DrainBlock && session.kind() != FiniteFluidKind.WATER) {
            return 0;
        }
        if (state.getBlock() instanceof DrainBlock) {
            long drainKey = pos.asLong();
            if (state.getValue(DrainBlock.LEVEL) <= 0) {
                if (remember && session.memory.volumes.remove(drainKey) != 0) {
                    session.markDirty();
                }
                return 0;
            }
            int storedDrain = Math.min(MAX_UNITS, session.memory.volumes.get(drainKey));
            if (storedDrain > 0) {
                return storedDrain;
            }
            int inferredDrain = inferredUnits(state.getFluidState());
            if (remember) {
                session.memory.volumes.put(drainKey, inferredDrain);
                session.markDirty();
            }
            return inferredDrain;
        }
        FiniteFluidKind stateKind = kindOf(state);
        if (stateKind != session.kind()) {
            if (remember && stateKind == null && session.memory.volumes.remove(pos.asLong()) != 0) {
                session.markDirty();
            }
            return 0;
        }
        long key = pos.asLong();
        int stored = Math.min(MAX_UNITS, session.memory.volumes.get(key));
        if (stored > 0 && state.equals(fluidStateForUnits(session.kind(), stored).createLegacyBlock())) {
            return stored;
        }
        int inferred = inferredUnits(state.getFluidState());
        if (remember && stored != inferred) {
            session.memory.volumes.put(key, inferred);
            session.markDirty();
        }
        return inferred;
    }

    private static boolean canEnter(Level level, BlockPos from, Direction direction, BlockPos to) {
        if (!level.isLoaded(to)) {
            return false;
        }
        BlockState toState = level.getBlockState(to);
        FiniteFluidKind kind = kindAt(level, from);
        if (kind == null) {
            kind = kindOf(toState);
        }
        if (kind == null) {
            kind = FiniteFluidKind.WATER;
        }
        if (isOpenFluid(level, to, kind)) {
            return true;
        }
        if (kind == FiniteFluidKind.WATER && toState.getBlock() instanceof DrainBlock) {
            return true;
        }
        FluidState toFluidState = toState.getFluidState();
        if (!toFluidState.isEmpty() || toState.getBlock() instanceof LiquidBlockContainer) {
            return false;
        }
        BlockState fromState = level.getBlockState(from);
        return ((FlowingFluidAccess) (Object) flowingFluid(kind))
                .sarcophag$canSpreadTo(level, from, fromState, direction, to, toState, toFluidState, sourceFluid(kind));
    }

    private static int columnHead(Level level, BlockPos pos, Session session) {
        int head = 0;
        BlockPos cursor = pos;
        for (int i = 0; i < 128; i++) {
            if (!level.isLoaded(cursor)) {
                break;
            }
            int units = volumeAt(level, cursor, session, false);
            if (units <= 0) {
                break;
            }
            head += units;
            if (units < MAX_UNITS) {
                break;
            }
            cursor = cursor.above();
        }
        return head;
    }

    private static void schedule(Level level, BlockPos pos) {
        schedule(level, pos, TICK_DELAY);
    }

    private static void schedule(Level level, BlockPos pos, int delay) {
        FluidState state = level.getFluidState(pos);
        if (isManagedFluid(state)) {
            level.scheduleTick(pos, state.getType(), delay);
        }
    }

    private static void scheduleAround(Level level, BlockPos pos) {
        schedule(level, pos);
        for (Direction direction : Direction.values()) {
            schedule(level, pos.relative(direction));
        }
    }

    private static void scheduleHorizontalAround(Level level, BlockPos pos, int delay) {
        schedule(level, pos, delay);
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            schedule(level, pos.relative(direction), delay);
        }
    }

    private static boolean canPlaceIntoEmpty(Level level, BlockPos pos, FiniteFluidKind kind) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DrainBlock) {
            return kind == FiniteFluidKind.WATER && state.getValue(DrainBlock.LEVEL) == 0;
        }
        return state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof LiquidBlockContainer)
                && (state.isAir() || state.canBeReplaced());
    }

    public static FiniteFluidKind kindAt(BlockGetter level, BlockPos pos) {
        return kindOf(level.getBlockState(pos));
    }

    public static boolean isManagedFluid(FluidState state) {
        return kindOf(state) != null;
    }

    private static FiniteFluidKind kindOf(BlockState state) {
        if (state.is(Blocks.WATER) || isWetDrain(state)) {
            return FiniteFluidKind.WATER;
        }
        return kindOf(state.getFluidState());
    }

    private static FiniteFluidKind kindOf(FluidState state) {
        if (state.is(FluidTags.WATER)) {
            return FiniteFluidKind.WATER;
        }
        if (!state.isEmpty()
                && (state.getType() == RadioactiveWater.SOURCE.get()
                || state.getType() == RadioactiveWater.FLOWING.get())) {
            return FiniteFluidKind.RADIOACTIVE_WATER;
        }
        return null;
    }

    private static boolean isOpenFluid(BlockGetter level, BlockPos pos, FiniteFluidKind kind) {
        return kind != null && kindAt(level, pos) == kind;
    }

    private static boolean isOpenWater(BlockGetter level, BlockPos pos) {
        return isOpenFluid(level, pos, FiniteFluidKind.WATER);
    }

    private static boolean isWetDrain(BlockState state) {
        return state.getBlock() instanceof DrainBlock && state.getValue(DrainBlock.LEVEL) > 0;
    }

    private static FlowingFluid sourceFluid(FiniteFluidKind kind) {
        return kind == FiniteFluidKind.RADIOACTIVE_WATER ? RadioactiveWater.SOURCE.get() : Fluids.WATER;
    }

    private static FlowingFluid flowingFluid(FiniteFluidKind kind) {
        return kind == FiniteFluidKind.RADIOACTIVE_WATER ? RadioactiveWater.FLOWING.get() : Fluids.FLOWING_WATER;
    }

    private static Session session(Level level) {
        return session(level, FiniteFluidKind.WATER);
    }

    private static Session session(Level level, FiniteFluidKind kind) {
        FiniteFluidKind actualKind = kind == null ? FiniteFluidKind.WATER : kind;
        if (level instanceof ServerLevel serverLevel) {
            FiniteWaterData data = FiniteWaterData.get(serverLevel);
            return new Session(data.memory(), data, actualKind);
        }
        synchronized (CLIENT_MEMORY) {
            return new Session(CLIENT_MEMORY.computeIfAbsent(level, ignored -> new FiniteWaterData.Memory()), null, actualKind);
        }
    }

    public enum FiniteFluidKind {
        WATER,
        RADIOACTIVE_WATER
    }

    private enum BodyKind {
        FINITE,
        LARGE,
        NOT_WATER
    }

    private record FlowCandidate(BlockPos pos, Direction direction, int targetHead) {
    }

    private record Session(FiniteWaterData.Memory memory, FiniteWaterData data, FiniteFluidKind kind) {
        Session withKind(FiniteFluidKind newKind) {
            return new Session(this.memory, this.data, newKind == null ? FiniteFluidKind.WATER : newKind);
        }

        void markDirty() {
            if (this.data != null) {
                this.data.setDirty();
            }
        }
    }
}
