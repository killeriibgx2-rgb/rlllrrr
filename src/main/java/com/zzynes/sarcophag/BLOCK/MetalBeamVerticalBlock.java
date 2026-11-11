package com.zzynes.sarcophag.BLOCK;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MetalBeamVerticalBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 4.0 / 16.0),
            Shapes.box(0.0, 0.0, 12.0 / 16.0, 1.0, 1.0, 1.0),
            Shapes.box(6.0 / 16.0, 0.0, 4.0 / 16.0, 10.0 / 16.0, 1.0, 12.0 / 16.0));

    public MetalBeamVerticalBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(4.0f, 8.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
