package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

public class PipeBlock extends RotatedPillarBlock {
    private static final VoxelShape X_SHAPE = Block.box(0.0D, 2.0D, 2.0D, 16.0D, 14.0D, 14.0D);
    private static final VoxelShape Y_SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    private static final VoxelShape Z_SHAPE = Block.box(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 16.0D);

    public PipeBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 30.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.BLOCK)
                .noOcclusion());
    }

    public static boolean hasPort(BlockState state, Direction direction) {
        return state.getBlock() instanceof PipeBlock && state.getValue(AXIS) == direction.getAxis();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        Direction.Axis fallback = state.getValue(AXIS);
        Direction.Axis chosen = chooseAxis(
                context.getLevel(),
                context.getClickedPos(),
                fallback,
                fallback,
                context.getClickedFace().getOpposite()
        );
        return state.setValue(AXIS, chosen);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis current = state.getValue(AXIS);
        Direction.Axis chosen = chooseAxis(level, pos, current, current, direction);
        BlockState updated = chosen == current ? state : state.setValue(AXIS, chosen);
        return super.updateShape(updated, direction, neighborState, level, pos, neighborPos);
    }

    private static Direction.Axis chooseAxis(BlockGetter level, BlockPos pos, Direction.Axis current, Direction.Axis fallback, Direction preferred) {
        List<Direction> connections = PipePorts.connections(level, pos);
        if (!connections.isEmpty()) {
            int[] counts = new int[Direction.Axis.values().length];
            for (Direction connection : connections) {
                counts[connection.getAxis().ordinal()]++;
            }
            for (Direction.Axis axis : Direction.Axis.values()) {
                if (counts[axis.ordinal()] >= 2) {
                    return axis;
                }
            }
            if (counts[current.ordinal()] > 0) {
                return current;
            }
            if (preferred != null && connections.contains(preferred)) {
                return preferred.getAxis();
            }
            return connections.get(0).getAxis();
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, axis);
            Direction negative = Direction.get(Direction.AxisDirection.NEGATIVE, axis);
            if (PipePorts.isPipe(level.getBlockState(pos.relative(positive)))
                    && PipePorts.isPipe(level.getBlockState(pos.relative(negative)))) {
                return axis;
            }
        }
        return fallback;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}
