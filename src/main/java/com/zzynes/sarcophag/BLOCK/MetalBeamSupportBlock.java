package com.zzynes.sarcophag.BLOCK;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

public class MetalBeamSupportBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape SHAPE_X = Shapes.or(
            Shapes.box(0.0, 0.0, 2.0 / 16.0, 1.0, 4.0 / 16.0, 14.0 / 16.0),
            Shapes.box(0.0, 4.0 / 16.0, 6.0 / 16.0, 1.0, 12.0 / 16.0, 10.0 / 16.0),
            Shapes.box(0.0, 12.0 / 16.0, 2.0 / 16.0, 1.0, 1.0, 14.0 / 16.0));
    private static final VoxelShape SHAPE_Z = Shapes.or(
            Shapes.box(2.0 / 16.0, 0.0, 0.0, 14.0 / 16.0, 4.0 / 16.0, 1.0),
            Shapes.box(6.0 / 16.0, 4.0 / 16.0, 0.0, 10.0 / 16.0, 12.0 / 16.0, 1.0),
            Shapes.box(2.0 / 16.0, 12.0 / 16.0, 0.0, 14.0 / 16.0, 1.0, 1.0));

    public MetalBeamSupportBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(4.0f, 8.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }
}
