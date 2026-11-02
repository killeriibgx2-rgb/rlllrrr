package com.zzynes.flydrone.WATER;

import com.zzynes.flydrone.WATER.mixin.FlowingFluidAccess;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

public final class FiniteWaterManager {
    public static final int BUCKET_UNITS = 10;
    public static final int MILLIBUCKETS_PER_UNIT = 100;
    private static final int MAX_UNITS = 10;
    private static final int MAX_BODY_UNITS = 20000;
    private static final int MAX_SCAN_CELLS = 40000;
    private static final int MAX_SETTLE_CELLS = 8192;
    private static final int MAX_SETTLE_RADIUS = 96;
    private static final int MAX_EQUALIZE_PASSES = 64;
    private static final int MAX_DOWN_PASSES = 128;
    private static final int TICK_DELAY = 1;
    private static final long LARGE_HINT_TICKS = 100L;
    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Map<Level, FiniteWaterData.Memory> CLIENT_MEMORY = new WeakHashMap<>();

    private FiniteWaterManager() {
    }

    public static void activateAround(Level level, BlockPos changedPos, EnumSet<Direction> notifiedSides) {
        Session session = session(level);
        activate(level, changedPos, session);
        for (Direction direction : notifiedSides) {
            activate(level, changedPos.relative(direction), session);
        }
    }

    public static boolean isFiniteAt(Level level, BlockPos pos) {
        if (!isOpenWater(level, pos)) {
            return false;
        }
        Session session = session(level);
        if (session.memory.finiteCells.contains(pos.asLong())) {
            return true;
        }
        return classify(level, pos, session) == BodyKind.FINITE;
    }

    public static boolean canAddBucket(Level level, BlockPos pos) {
        return canAddWaterUnits(level, pos, BUCKET_UNITS, true);
    }

    public static boolean canAddWaterUnits(Level level, BlockPos pos, int units) {
        return canAddWaterUnits(level, pos, units, false);
    }

    private static boolean canAddWaterUnits(Level level, BlockPos pos, int units, boolean requireAll) {
        if (units <= 0 || !isOpenWater(level, pos)) {
            return false;
        }
        Session session = session(level);
        Long2IntOpenHashMap component = collectComponent(level, pos, session);
        if (component == null) {
            return false;
        }
        int requested = Math.min(units, BUCKET_UNITS);
        Long2IntOpenHashMap planned = plannedAdd(level, component, session, requested);
        int added = componentTotal(planned) - componentTotal(component);
        return requireAll ? added >= requested : added > 0;
    }

    public static boolean canPlaceWaterUnits(Level level, BlockPos pos, int units) {
        if (units <= 0 || !level.isLoaded(pos)) {
            return false;
        }
        if (isOpenWater(level, pos)) {
            return canAddWaterUnits(level, pos, units) || !isFiniteAt(level, pos);
        }
        return canPlaceIntoEmpty(level, pos);
    }

    public static boolean tryAddBucket(Level level, BlockPos pos) {
        return tryAddWaterUnits(level, pos, BUCKET_UNITS) >= BUCKET_UNITS;
    }

    public static int tryAddWaterUnits(Level level, BlockPos pos, int units) {
        if (level.isClientSide || units <= 0 || !isOpenWater(level, pos)) {
            return 0;
        }
        Session session = session(level);
        Long2IntOpenHashMap component = collectComponent(level, pos, session);
        if (component == null) {
            return 0;
        }
        Long2IntOpenHashMap planned = plannedAdd(level, component, session, Math.min(units, BUCKET_UNITS));
        int added = componentTotal(planned) - componentTotal(component);
        if (added > 0) {
            applyVolumes(level, component, planned, session);
        }
        return added;
    }

    public static int tryPlaceWaterUnits(Level level, BlockPos pos, int units) {
        if (level.isClientSide || units <= 0 || !level.isLoaded(pos)) {
            return 0;
        }
        int requested = Math.min(units, BUCKET_UNITS);
        if (isOpenWater(level, pos)) {
            Session session = session(level);
            BodyKind kind = classify(level, pos, session);
            if (kind == BodyKind.LARGE) {
                return requested;
            }
            if (kind != BodyKind.FINITE) {
                return 0;
            }
            return tryAddWaterUnits(level, pos, requested);
        }
        if (!canPlaceIntoEmpty(level, pos)) {
            return 0;
        }
        Session session = session(level);
        setUnits(level, pos, requested, null, session);
        scheduleAround(level, pos);
        return requested;
    }

    public static boolean tryPickupBucket(Level level, BlockPos pos) {
        return tryPickupWaterUnits(level, pos, BUCKET_UNITS) >= BUCKET_UNITS;
    }

    public static int tryPickupWaterUnits(Level level, BlockPos pos, int maxUnits) {
        if (level.isClientSide || maxUnits <= 0 || !isOpenWater(level, pos)) {
            return 0;
        }
        Session session = session(level);
        BodyKind kind = classify(level, pos, session);
        int requested = Math.min(maxUnits, BUCKET_UNITS);
        if (kind == BodyKind.LARGE) {
            return requested;
        }
        if (kind != BodyKind.FINITE) {
            return 0;
        }
        Long2IntOpenHashMap component = collectComponent(level, pos, session);
        if (component == null) {
            return 0;
        }
        int removeUnits = Math.min(requested, componentTotal(component));
        if (removeUnits <= 0) {
            return 0;
        }
        Long2IntOpenHashMap desired = new Long2IntOpenHashMap(component);
        desired.defaultReturnValue(0);
        for (int removed = 0; removed < removeUnits; removed++) {
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
            int next = desired.get(bestKey) - 1;
            if (next <= 0) {
                desired.remove(bestKey);
            } else {
                desired.put(bestKey, next);
            }
        }
        applyVolumes(level, component, desired, session);
        return removeUnits;
    }

    public static boolean handleTick(Level level, BlockPos pos, FluidState state) {
        if (!state.is(FluidTags.WATER)) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        Session session = session(level);
        if (!isOpenWater(level, pos)) {
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

    private static void activate(Level level, BlockPos pos, Session session) {
        if (!level.isLoaded(pos) || !isOpenWater(level, pos)) {
            return;
        }
        long key = pos.asLong();
        if (session.memory.finiteCells.contains(key)) {
            return;
        }
        if (session.memory.largeCells.get(key) > level.getGameTime()) {
            return;
        }
        classify(level, pos, session);
    }

    private static BodyKind classify(Level level, BlockPos start, Session session) {
        if (!isOpenWater(level, start)) {
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

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!isOpenWater(level, pos)) {
                continue;
            }
            long key = pos.asLong();
            if (session.memory.largeCells.get(key) > now) {
                overLimit = true;
            }
            int cellUnits = volumeAt(level, pos, session, false);
            if (cellUnits > 0) {
                scannedVolumes.put(key, cellUnits);
            }
            if (!overLimit) {
                volume += cellUnits;
                if (volume >= MAX_BODY_UNITS || visited.size() > MAX_SCAN_CELLS) {
                    overLimit = true;
                }
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                long neighborKey = neighbor.asLong();
                if (visited.contains(neighborKey) || !level.isLoaded(neighbor) || !isOpenWater(level, neighbor)) {
                    continue;
                }
                if (overLimit && !session.memory.finiteCells.contains(neighborKey)) {
                    continue;
                }
                visited.add(neighborKey);
                queue.add(neighbor);
            }
        }

        if (overLimit) {
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

    private static Long2IntOpenHashMap collectComponent(Level level, BlockPos start, Session session) {
        if (!isOpenWater(level, start) || classify(level, start, session) != BodyKind.FINITE) {
            return null;
        }
        LongOpenHashSet visited = new LongOpenHashSet();
        Long2IntOpenHashMap component = new Long2IntOpenHashMap();
        component.defaultReturnValue(0);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);
        int total = 0;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!isOpenWater(level, pos)) {
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
                        && isOpenWater(level, neighbor)) {
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
        for (int added = 0; added < unitsToAdd; added++) {
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
                desired.put(bestExpansion.asLong(), 1);
            } else if (found) {
                desired.put(bestKey, desired.get(bestKey) + 1);
            } else {
                break;
            }
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
                        || isOpenWater(level, target)
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
                    && isOpenWater(level, neighbor)
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
        if (settleFinite(level, pos, session)) {
            return;
        }
        if (tryFlowDown(level, pos, units, session)) {
            return;
        }
        if (tryFlowSideways(level, pos, units, session)) {
            return;
        }
        tryPressureRise(level, pos, units, session);
    }

    private static boolean settleFinite(Level level, BlockPos start, Session session) {
        long now = level.getGameTime();
        if (session.memory.equalizedAt.get(start.asLong()) == now) {
            return true;
        }
        Long2IntOpenHashMap desired = collectLocalVolumes(level, start, session);
        if (desired == null) {
            return false;
        }
        Long2IntOpenHashMap original = new Long2IntOpenHashMap(desired);
        original.defaultReturnValue(0);
        settleDown(level, desired, original);
        for (int pass = 0; pass < MAX_EQUALIZE_PASSES; pass++) {
            boolean changed = equalizeColumns(level, desired, original);
            if (levelFreeSurfacesOnce(level, desired, original)) {
                changed = true;
            }
            if (!changed) {
                break;
            }
        }
        settleDown(level, desired, original);
        applyVolumes(level, original, desired, session);
        LongOpenHashSet keys = new LongOpenHashSet(original.keySet());
        keys.addAll(desired.keySet());
        LongIterator iterator = keys.iterator();
        while (iterator.hasNext()) {
            session.memory.equalizedAt.put(iterator.nextLong(), now);
        }
        return true;
    }

    private static Long2IntOpenHashMap collectLocalVolumes(Level level, BlockPos start, Session session) {
        Long2IntOpenHashMap desired = new Long2IntOpenHashMap();
        desired.defaultReturnValue(0);
        LongOpenHashSet visited = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!isOpenWater(level, pos)) {
                continue;
            }
            int units = volumeAt(level, pos, session, true);
            if (units <= 0) {
                continue;
            }
            desired.put(pos.asLong(), units);
            if (desired.size() > MAX_SETTLE_CELLS) {
                return null;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                long neighborKey = neighbor.asLong();
                if (visited.contains(neighborKey)
                        || !level.isLoaded(neighbor)
                        || !isOpenWater(level, neighbor)) {
                    continue;
                }
                int distance = Math.abs(neighbor.getX() - start.getX())
                        + Math.abs(neighbor.getY() - start.getY())
                        + Math.abs(neighbor.getZ() - start.getZ());
                if (distance > MAX_SETTLE_RADIUS) {
                    return null;
                }
                visited.add(neighborKey);
                queue.add(neighbor);
            }
        }
        return desired.isEmpty() ? null : desired;
    }

    private static boolean settleDown(Level level, Long2IntOpenHashMap desired, Long2IntOpenHashMap original) {
        boolean any = false;
        for (int pass = 0; pass < MAX_DOWN_PASSES; pass++) {
            List<Long> keys = new ArrayList<>(desired.keySet());
            keys.sort(Comparator.comparingInt(key -> BlockPos.of(key).getY()));
            boolean changed = false;
            for (long key : keys) {
                int units = desired.get(key);
                if (units <= 0) {
                    continue;
                }
                BlockPos pos = BlockPos.of(key);
                BlockPos below = pos.below();
                long belowKey = below.asLong();
                int belowUnits = desired.get(belowKey);
                int capacity;
                if (belowUnits > 0) {
                    capacity = MAX_UNITS - belowUnits;
                } else if (isOpenWater(level, below)) {
                    if (!original.containsKey(belowKey)) {
                        continue;
                    }
                    capacity = MAX_UNITS;
                } else {
                    if (!canEnter(level, pos, Direction.DOWN, below)) {
                        continue;
                    }
                    capacity = MAX_UNITS;
                }
                int move = Math.min(units, capacity);
                if (move <= 0) {
                    continue;
                }
                putDesired(desired, pos, units - move);
                putDesired(desired, below, belowUnits + move);
                changed = true;
                any = true;
            }
            if (!changed) {
                break;
            }
        }
        return any;
    }

    private static boolean equalizeColumns(Level level, Long2IntOpenHashMap desired, Long2IntOpenHashMap original) {
        List<Long> keys = new ArrayList<>(desired.keySet());
        Set<ColumnPair> handled = new HashSet<>();
        boolean changed = false;
        for (long key : keys) {
            if (desired.get(key) <= 0) {
                continue;
            }
            BlockPos from = BlockPos.of(key);
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos to = from.relative(direction);
                long toKey = to.asLong();
                int toUnits = desired.get(toKey);
                boolean toWater = toUnits > 0;
                if (!toWater) {
                    if (isOpenWater(level, to) && !original.containsKey(toKey)) {
                        continue;
                    }
                    if (!canEnter(level, from, direction, to)) {
                        continue;
                    }
                }
                long fromColumn = BlockPos.asLong(from.getX(), 0, from.getZ());
                long toColumn = BlockPos.asLong(to.getX(), 0, to.getZ());
                ColumnPair pair = fromColumn < toColumn
                        ? new ColumnPair(fromColumn, toColumn)
                        : new ColumnPair(toColumn, fromColumn);
                if (!handled.add(pair)) {
                    continue;
                }
                int fromSurface = columnSurfaceFromMap(desired, from);
                BlockPos toReceiver;
                int toSurface;
                if (toWater) {
                    toSurface = columnSurfaceFromMap(desired, to);
                    toReceiver = columnReceiver(level, desired, original, to);
                } else {
                    toReceiver = emptyColumnReceiver(level, desired, original, to);
                    if (toReceiver == null) {
                        continue;
                    }
                    toSurface = surfaceForReceiver(desired, toReceiver);
                }
                BlockPos donor;
                BlockPos receiver;
                int difference;
                if (fromSurface > toSurface) {
                    donor = columnTop(desired, from);
                    receiver = toReceiver;
                    difference = fromSurface - toSurface;
                } else if (toSurface > fromSurface && toWater) {
                    donor = columnTop(desired, to);
                    receiver = columnReceiver(level, desired, original, from);
                    difference = toSurface - fromSurface;
                } else {
                    continue;
                }
                if (difference <= 1 || donor.equals(receiver)) {
                    continue;
                }
                int donorUnits = desired.get(donor.asLong());
                int receiverUnits = desired.get(receiver.asLong());
                int move = Math.min(difference / 2, Math.min(donorUnits, MAX_UNITS - receiverUnits));
                if (move <= 0) {
                    continue;
                }
                putDesired(desired, donor, donorUnits - move);
                putDesired(desired, receiver, receiverUnits + move);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean levelFreeSurfacesOnce(Level level, Long2IntOpenHashMap desired, Long2IntOpenHashMap original) {
        BlockPos donor = null;
        int donorSurface = Integer.MIN_VALUE;
        int donorUnits = 0;
        BlockPos receiver = null;
        int receiverSurface = Integer.MAX_VALUE;
        int receiverCapacity = 0;
        for (var entry : desired.long2IntEntrySet()) {
            int units = entry.getIntValue();
            if (units <= 0) {
                continue;
            }
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (desired.get(pos.above().asLong()) > 0) {
                continue;
            }
            int surface = pos.getY() * MAX_UNITS + units;
            if (surface > donorSurface) {
                donor = pos;
                donorSurface = surface;
                donorUnits = units;
            }
            BlockPos candidateReceiver = columnReceiver(level, desired, original, pos);
            int capacity = MAX_UNITS - desired.get(candidateReceiver.asLong());
            if (capacity <= 0) {
                continue;
            }
            int candidateSurface = surfaceForReceiver(desired, candidateReceiver);
            if (candidateSurface < receiverSurface) {
                receiver = candidateReceiver;
                receiverSurface = candidateSurface;
                receiverCapacity = capacity;
            }
        }
        if (donor == null || receiver == null || donor.equals(receiver)) {
            return false;
        }
        int difference = donorSurface - receiverSurface;
        if (difference <= 1) {
            return false;
        }
        int receiverUnits = desired.get(receiver.asLong());
        int move = Math.min(difference / 2, Math.min(donorUnits, receiverCapacity));
        if (move <= 0) {
            return false;
        }
        putDesired(desired, donor, donorUnits - move);
        putDesired(desired, receiver, receiverUnits + move);
        return true;
    }

    private static void putDesired(Long2IntOpenHashMap desired, BlockPos pos, int units) {
        long key = pos.asLong();
        if (units <= 0) {
            desired.remove(key);
        } else {
            desired.put(key, Math.min(MAX_UNITS, units));
        }
    }

    private static BlockPos columnTop(Long2IntOpenHashMap desired, BlockPos pos) {
        BlockPos top = pos;
        for (int i = 0; i < 128; i++) {
            if (desired.get(top.asLong()) < MAX_UNITS) {
                break;
            }
            BlockPos above = top.above();
            if (desired.get(above.asLong()) <= 0) {
                break;
            }
            top = above;
        }
        return top;
    }

    private static int columnSurfaceFromMap(Long2IntOpenHashMap desired, BlockPos pos) {
        int surface = pos.getY() * MAX_UNITS;
        BlockPos cursor = pos;
        for (int i = 0; i < 128; i++) {
            int units = desired.get(cursor.asLong());
            if (units <= 0) {
                break;
            }
            surface += units;
            if (units < MAX_UNITS) {
                break;
            }
            cursor = cursor.above();
        }
        return surface;
    }

    private static BlockPos columnReceiver(Level level, Long2IntOpenHashMap desired, Long2IntOpenHashMap original, BlockPos pos) {
        BlockPos top = columnTop(desired, pos);
        if (desired.get(top.asLong()) < MAX_UNITS) {
            return top;
        }
        BlockPos above = top.above();
        long aboveKey = above.asLong();
        if (desired.get(aboveKey) > 0) {
            return columnTop(desired, above);
        }
        if (isOpenWater(level, above) && !original.containsKey(aboveKey)) {
            return top;
        }
        return canEnter(level, top, Direction.UP, above) ? above : top;
    }

    private static BlockPos emptyColumnReceiver(Level level, Long2IntOpenHashMap desired, Long2IntOpenHashMap original, BlockPos empty) {
        BlockPos cursor = empty;
        for (int i = 0; i < 128; i++) {
            BlockPos below = cursor.below();
            long belowKey = below.asLong();
            if (desired.get(belowKey) > 0) {
                return columnReceiver(level, desired, original, below);
            }
            if (isOpenWater(level, below)) {
                if (!original.containsKey(belowKey)) {
                    return null;
                }
            } else if (!canPlaceIntoEmpty(level, below)) {
                break;
            }
            cursor = below;
        }
        return cursor;
    }

    private static int surfaceForReceiver(Long2IntOpenHashMap desired, BlockPos receiver) {
        return desired.get(receiver.asLong()) > 0
                ? columnSurfaceFromMap(desired, receiver)
                : receiver.getY() * MAX_UNITS;
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
        int move = Math.min(units, MAX_UNITS - belowUnits);
        return move > 0 && transfer(level, pos, below, Direction.DOWN, move, session);
    }

    private static boolean tryFlowSideways(Level level, BlockPos pos, int units, Session session) {
        int ownHead = columnHead(level, pos, session);
        int ownDistanceToEmpty = -1;
        List<FlowCandidate> candidates = new ArrayList<>();
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos target = pos.relative(direction);
            if (!canEnter(level, pos, direction, target)) {
                continue;
            }
            int targetUnits = volumeAt(level, target, session, false);
            if (targetUnits >= MAX_UNITS) {
                continue;
            }
            int targetHead = targetUnits > 0 ? columnHead(level, target, session) : 0;
            int difference = ownHead - targetHead;
            int requiredDifference = ownHead > units ? 1 : 2;
            if (difference >= requiredDifference) {
                candidates.add(new FlowCandidate(target.immutable(), direction, targetHead));
            } else if (difference == 1 && units > 1 && targetUnits > 0) {
                if (ownDistanceToEmpty < 0) {
                    ownDistanceToEmpty = distanceToEmpty(level, pos, session);
                }
                if (distanceToEmpty(level, target, session) < ownDistanceToEmpty) {
                    candidates.add(new FlowCandidate(target.immutable(), direction, targetHead));
                }
            }
        }
        if (candidates.isEmpty()) {
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
        FlowCandidate chosen = best.get(level.getRandom().nextInt(best.size()));
        return transfer(level, pos, chosen.pos(), chosen.direction(), 1, session);
    }

    private static int distanceToEmpty(Level level, BlockPos start, Session session) {
        if (hasEmptyHorizontalNeighbor(level, start, session)) {
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
                    if (hasEmptyHorizontalNeighbor(level, neighbor, session)) {
                        return distance;
                    }
                    visited.add(neighborKey);
                    queue.add(neighbor);
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    private static boolean hasEmptyHorizontalNeighbor(Level level, BlockPos pos, Session session) {
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos target = pos.relative(direction);
            if (canEnter(level, pos, direction, target)
                    && volumeAt(level, target, session, false) <= 0) {
                return true;
            }
        }
        return false;
    }

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
        if (aboveUnits >= MAX_UNITS) {
            return false;
        }
        int ownHead = columnHead(level, pos, session);
        Direction bestDirection = null;
        BlockPos bestPos = null;
        int bestHead = ownHead + 1;
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.isLoaded(neighbor) || !isOpenWater(level, neighbor)) {
                continue;
            }
            int neighborHead = columnHead(level, neighbor, session);
            if (neighborHead > bestHead) {
                bestDirection = direction;
                bestPos = neighbor;
                bestHead = neighborHead;
            }
        }
        if (bestDirection == null || bestPos == null) {
            return false;
        }
        int neighborUnits = volumeAt(level, bestPos, session, false);
        if (neighborUnits <= 0) {
            return false;
        }
        setUnits(level, above, aboveUnits + 1, Direction.UP, session);
        setUnits(level, bestPos, neighborUnits - 1, bestDirection.getOpposite(), session);
        schedule(level, pos);
        schedule(level, above);
        schedule(level, bestPos);
        return true;
    }

    private static boolean transfer(Level level, BlockPos from, BlockPos to, Direction direction, int units, Session session) {
        int fromUnits = volumeAt(level, from, session, true);
        int toUnits = volumeAt(level, to, session, true);
        if (units <= 0 || fromUnits < units || toUnits + units > MAX_UNITS) {
            return false;
        }
        setUnits(level, to, toUnits + units, direction, session);
        setUnits(level, from, fromUnits - units, direction.getOpposite(), session);
        schedule(level, from);
        schedule(level, to);
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
            if (isOpenWater(level, pos)) {
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
        FluidState newFluidState = fluidStateForUnits(clamped);
        BlockState oldState = level.getBlockState(pos);
        BlockState newState = newFluidState.createLegacyBlock();
        if (!oldState.equals(newState)) {
            if (entryDirection != null && !oldState.is(Blocks.WATER) && !oldState.isAir()) {
                ((FlowingFluidAccess) (Object) Fluids.FLOWING_WATER).flyDrone$spreadTo(level, pos, oldState, entryDirection, newFluidState);
            } else {
                level.setBlock(pos, newState, 3);
            }
        }
        if (changed) {
            session.markDirty();
        }
    }

    private static FluidState fluidStateForUnits(int units) {
        if (units >= MAX_UNITS) {
            return Fluids.WATER.getSource(false);
        }
        return Fluids.FLOWING_WATER.getFlowing(displayAmountForUnits(units), false);
    }

    private static int displayAmountForUnits(int units) {
        return Math.max(1, Math.min(8, (units * 8 + 9) / 10));
    }

    private static int inferredUnits(FluidState state) {
        if (state.isSource()) {
            return MAX_UNITS;
        }
        return switch (state.getAmount()) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 5;
            case 5 -> 6;
            case 6 -> 7;
            case 7 -> 8;
            case 8 -> 9;
            default -> Math.max(1, Math.min(MAX_UNITS, state.getAmount()));
        };
    }

    private static int volumeAt(Level level, BlockPos pos, Session session, boolean remember) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.WATER)) {
            if (remember && session.memory.volumes.remove(pos.asLong()) != 0) {
                session.markDirty();
            }
            return 0;
        }
        long key = pos.asLong();
        int stored = Math.min(MAX_UNITS, session.memory.volumes.get(key));
        if (stored > 0 && state.equals(fluidStateForUnits(stored).createLegacyBlock())) {
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
        if (toState.is(Blocks.WATER)) {
            return true;
        }
        FluidState toFluidState = toState.getFluidState();
        if (!toFluidState.isEmpty() || toState.getBlock() instanceof LiquidBlockContainer) {
            return false;
        }
        BlockState fromState = level.getBlockState(from);
        return ((FlowingFluidAccess) (Object) Fluids.FLOWING_WATER)
                .flyDrone$canSpreadTo(level, from, fromState, direction, to, toState, toFluidState, Fluids.WATER);
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
        FluidState state = level.getFluidState(pos);
        if (state.is(FluidTags.WATER)) {
            level.scheduleTick(pos, state.getType(), TICK_DELAY);
        }
    }

    private static void scheduleAround(Level level, BlockPos pos) {
        schedule(level, pos);
        for (Direction direction : Direction.values()) {
            schedule(level, pos.relative(direction));
        }
    }

    private static boolean canPlaceIntoEmpty(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof LiquidBlockContainer)
                && (state.isAir() || state.canBeReplaced());
    }

    private static boolean isOpenWater(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.WATER);
    }

    private static Session session(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            FiniteWaterData data = FiniteWaterData.get(serverLevel);
            return new Session(data.memory(), data);
        }
        synchronized (CLIENT_MEMORY) {
            return new Session(CLIENT_MEMORY.computeIfAbsent(level, ignored -> new FiniteWaterData.Memory()), null);
        }
    }

    private enum BodyKind {
        FINITE,
        LARGE,
        NOT_WATER
    }

    private record FlowCandidate(BlockPos pos, Direction direction, int targetHead) {
    }

    private record ColumnPair(long first, long second) {
    }

    private record Session(FiniteWaterData.Memory memory, FiniteWaterData data) {
        void markDirty() {
            if (this.data != null) {
                this.data.setDirty();
            }
        }
    }
}
