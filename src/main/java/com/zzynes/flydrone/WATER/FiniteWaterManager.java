package com.zzynes.flydrone.WATER;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

public final class FiniteWaterManager {
    private static final int MAX_AMOUNT = 8;
    private static final int MAX_BODY_UNITS = 16000;
    private static final int MAX_SCAN_CELLS = 40000;
    private static final int TICK_DELAY = 5;
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

    public static boolean handleTick(Level level, BlockPos pos, FluidState state) {
        if (!state.is(FluidTags.WATER) || !isOpenWater(level, pos)) {
            return false;
        }
        Session session = session(level);
        long key = pos.asLong();
        if (!session.memory.finiteCells.contains(key)) {
            if (!level.isClientSide) {
                return false;
            }
            if (classify(level, pos, session) != BodyKind.FINITE) {
                return false;
            }
        } else if (hasAdjacentUnknownWater(level, pos, session)
                && classify(level, pos, session) != BodyKind.FINITE) {
            return false;
        }
        processCell(level, pos, session);
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
            if (session.memory.largeCells.get(pos.asLong()) > now) {
                overLimit = true;
            }
            if (!overLimit) {
                volume += amountAt(level, pos);
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
            }
            session.markDirty();
            return BodyKind.LARGE;
        }

        session.memory.finiteCells.addAll(visited);
        LongIterator iterator = visited.iterator();
        while (iterator.hasNext()) {
            session.memory.largeCells.remove(iterator.nextLong());
        }
        session.markDirty();
        return BodyKind.FINITE;
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
        int amount = amountAt(level, pos);
        if (amount <= 0) {
            if (session.memory.finiteCells.remove(pos.asLong())) {
                session.markDirty();
            }
            return;
        }
        if (tryFlowDown(level, pos, amount, session)) {
            return;
        }
        if (tryFlowSideways(level, pos, amount, session)) {
            return;
        }
        tryPressureRise(level, pos, amount, session);
    }

    private static boolean tryFlowDown(Level level, BlockPos pos, int amount, Session session) {
        BlockPos below = pos.below();
        if (!canEnter(level, pos, Direction.DOWN, below)) {
            return false;
        }
        int belowAmount = amountAt(level, below);
        if (belowAmount >= MAX_AMOUNT) {
            return false;
        }
        int transfer = Math.min(amount, MAX_AMOUNT - belowAmount);
        return transfer(level, pos, below, Direction.DOWN, transfer, session);
    }

    private static boolean tryFlowSideways(Level level, BlockPos pos, int amount, Session session) {
        int ownHead = columnHead(level, pos);
        int startIndex = Math.floorMod(pos.hashCode() ^ (int) (level.getGameTime() / TICK_DELAY), HORIZONTAL_DIRECTIONS.length);
        Direction bestDirection = null;
        BlockPos bestPos = null;
        int bestDifference = Integer.MIN_VALUE;
        for (int i = 0; i < HORIZONTAL_DIRECTIONS.length; i++) {
            Direction direction = HORIZONTAL_DIRECTIONS[(startIndex + i) % HORIZONTAL_DIRECTIONS.length];
            BlockPos target = pos.relative(direction);
            if (!canEnter(level, pos, direction, target)) {
                continue;
            }
            int targetAmount = amountAt(level, target);
            if (targetAmount >= MAX_AMOUNT) {
                continue;
            }
            int targetHead = targetAmount > 0 ? columnHead(level, target) : 0;
            int difference = ownHead - targetHead;
            int requiredDifference = ownHead > amount ? 1 : 2;
            if (difference >= requiredDifference && difference > bestDifference) {
                bestDirection = direction;
                bestPos = target;
                bestDifference = difference;
            }
        }
        return bestDirection != null && transfer(level, pos, bestPos, bestDirection, 1, session);
    }

    private static boolean tryPressureRise(Level level, BlockPos pos, int amount, Session session) {
        if (amount < MAX_AMOUNT) {
            return false;
        }
        BlockPos above = pos.above();
        for (int i = 0; i < 128 && level.isLoaded(above) && amountAt(level, above) >= MAX_AMOUNT; i++) {
            above = above.above();
        }
        if (!level.isLoaded(above) || !canEnter(level, above.below(), Direction.UP, above)) {
            return false;
        }
        int aboveAmount = amountAt(level, above);
        if (aboveAmount >= MAX_AMOUNT) {
            return false;
        }
        int ownHead = columnHead(level, pos);
        Direction bestDirection = null;
        BlockPos bestPos = null;
        int bestHead = ownHead + 1;
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.isLoaded(neighbor) || !isOpenWater(level, neighbor)) {
                continue;
            }
            int neighborHead = columnHead(level, neighbor);
            if (neighborHead > bestHead) {
                bestDirection = direction;
                bestPos = neighbor;
                bestHead = neighborHead;
            }
        }
        if (bestDirection == null || bestPos == null) {
            return false;
        }
        int neighborAmount = amountAt(level, bestPos);
        if (neighborAmount <= 0) {
            return false;
        }
        setAmount(level, above, aboveAmount + 1, Direction.UP, session);
        setAmount(level, bestPos, neighborAmount - 1, bestDirection.getOpposite(), session);
        schedule(level, pos);
        schedule(level, above);
        schedule(level, bestPos);
        return true;
    }

    private static boolean transfer(Level level, BlockPos from, BlockPos to, Direction direction, int units, Session session) {
        int fromAmount = amountAt(level, from);
        int toAmount = amountAt(level, to);
        if (units <= 0 || fromAmount < units || toAmount + units > MAX_AMOUNT) {
            return false;
        }
        setAmount(level, to, toAmount + units, direction, session);
        setAmount(level, from, fromAmount - units, direction.getOpposite(), session);
        schedule(level, from);
        schedule(level, to);
        return true;
    }

    private static void setAmount(Level level, BlockPos pos, int amount, Direction entryDirection, Session session) {
        long key = pos.asLong();
        if (amount <= 0) {
            boolean changed = session.memory.finiteCells.remove(key);
            session.memory.largeCells.remove(key);
            if (isOpenWater(level, pos)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            if (changed) {
                session.markDirty();
            }
            return;
        }

        boolean changed = session.memory.finiteCells.add(key);
        session.memory.largeCells.remove(key);
        FluidState newFluidState = fluidStateForAmount(level, pos, amount);
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

    private static FluidState fluidStateForAmount(Level level, BlockPos pos, int amount) {
        if (amount >= MAX_AMOUNT) {
            boolean falling = level.isLoaded(pos.above())
                    && level.isLoaded(pos.below())
                    && isOpenWater(level, pos.above())
                    && !isOpenWater(level, pos.below())
                    && level.getFluidState(pos.below()).isEmpty();
            return falling ? Fluids.FLOWING_WATER.getFlowing(MAX_AMOUNT, true) : Fluids.WATER.getSource(false);
        }
        return Fluids.FLOWING_WATER.getFlowing(amount, false);
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

    private static int columnHead(Level level, BlockPos pos) {
        int head = 0;
        BlockPos cursor = pos;
        for (int i = 0; i < 128; i++) {
            if (!level.isLoaded(cursor)) {
                break;
            }
            int amount = amountAt(level, cursor);
            if (amount <= 0) {
                break;
            }
            head += amount;
            if (amount < MAX_AMOUNT) {
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

    private static boolean isOpenWater(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.WATER);
    }

    private static int amountAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.WATER) ? state.getFluidState().getAmount() : 0;
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

    private record Session(FiniteWaterData.Memory memory, FiniteWaterData data) {
        void markDirty() {
            if (this.data != null) {
                this.data.setDirty();
            }
        }
    }
}
