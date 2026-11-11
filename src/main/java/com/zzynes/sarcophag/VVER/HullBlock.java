package com.zzynes.sarcophag.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class HullBlock extends Block {
    private final HullType hullType;

    public HullBlock(HullType hullType) {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(hullType.hardness(), hullType.resistance())
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.BLOCK));
        this.hullType = hullType;
    }

    public HullType hullType() {
        return this.hullType;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel serverLevel && !oldState.is(state.getBlock())) {
            ReactorVesselData.get(serverLevel).markDirty(pos);
            ReactorVesselData.get(serverLevel).repairAt(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level instanceof ServerLevel serverLevel && !(newState.getBlock() instanceof HullBlock)) {
            ReactorVesselData.get(serverLevel).onHullRemoved(pos, state);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
