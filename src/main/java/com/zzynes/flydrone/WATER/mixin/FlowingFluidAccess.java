package com.zzynes.flydrone.WATER.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FlowingFluid.class)
public interface FlowingFluidAccess {
    @Invoker("canSpreadTo")
    boolean flyDrone$canSpreadTo(BlockGetter level, BlockPos fromPos, BlockState fromState, Direction direction, BlockPos toPos, BlockState toState, FluidState toFluidState, Fluid fluid);

    @Invoker("spreadTo")
    void flyDrone$spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluidState);
}
