package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class PipePorts {
    private PipePorts() {
    }

    static boolean isPipe(BlockState state) {
        return state.getBlock() instanceof PipeBlock
                || state.getBlock() instanceof CornerPipeBlock
                || state.getBlock() instanceof TeePipeBlock
                || state.getBlock() instanceof CrossPipeBlock;
    }

    static boolean hasPipePort(BlockState state, Direction towardNeighborCell) {
        if (state.getBlock() instanceof ValveBlock) {
            return ValveBlock.hasPort(state, towardNeighborCell);
        }
        return PipeBlock.hasPort(state, towardNeighborCell)
                || CornerPipeBlock.hasPort(state, towardNeighborCell)
                || TeePipeBlock.hasPort(state, towardNeighborCell)
                || CrossPipeBlock.hasPort(state, towardNeighborCell);
    }

    static boolean isClosedValve(BlockState state) {
        return state.getBlock() instanceof ValveBlock && !state.getValue(ValveBlock.OPEN);
    }

    static boolean connectsToward(BlockState state, Direction towardNeighborCell) {
        if (state.getBlock() instanceof ValveBlock) {
            return ValveBlock.hasPhysicalPort(state, towardNeighborCell);
        }
        if (hasPipePort(state, towardNeighborCell)
                || SteamGeneratorBlock.hasPort(state, towardNeighborCell)
                || GcnBlock.hasFluidPort(state, towardNeighborCell)) {
            return true;
        }
        return state.getBlock() instanceof HullNozzleBlock
                && state.getValue(HullNozzleBlock.FACING) == towardNeighborCell;
    }

    static List<Direction> connections(BlockGetter level, BlockPos pos) {
        List<Direction> connections = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (connectsToward(neighbor, direction.getOpposite())) {
                connections.add(direction);
            }
        }
        return connections;
    }

    static Direction chooseTowardPlayer(BlockPlaceContext context, Direction towardClickedBlock) {
        Vec3 towardPlayer = towardPlayerVector(context);
        Direction best = null;
        double bestValue = 0.0D;
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == towardClickedBlock.getAxis()) {
                continue;
            }
            double value = score(towardPlayer, direction);
            if (value > bestValue) {
                bestValue = value;
                best = direction;
            }
        }
        if (best != null) {
            return best;
        }
        if (towardClickedBlock.getAxis() == Direction.Axis.Y) {
            return context.getHorizontalDirection().getOpposite();
        }
        return towardPlayer.y < 0.0D ? Direction.DOWN : Direction.UP;
    }

    static Vec3 towardPlayerVector(BlockPlaceContext context) {
        Vec3 placedCenter = Vec3.atCenterOf(context.getClickedPos());
        Player player = context.getPlayer();
        return player != null
                ? player.getEyePosition().subtract(placedCenter)
                : Vec3.atLowerCornerOf(context.getNearestLookingDirection().getOpposite().getNormal());
    }

    static double score(Vec3 vector, Direction direction) {
        return vector.x * direction.getStepX()
                + vector.y * direction.getStepY()
                + vector.z * direction.getStepZ();
    }
}
