package com.zzynes.flydrone.WATER.mixin;

import com.zzynes.flydrone.WATER.FiniteWaterManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "getFlow", at = @At("HEAD"), cancellable = true)
    private void flyDrone$finiteWaterFlow(BlockGetter level, BlockPos pos, FluidState state, CallbackInfoReturnable<Vec3> callback) {
        if (state.is(FluidTags.WATER)) {
            callback.setReturnValue(Vec3.ZERO);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void flyDrone$finiteWaterTick(Level level, BlockPos pos, FluidState state, CallbackInfo callback) {
        if (state.is(FluidTags.WATER)) {
            FiniteWaterManager.handleTick(level, pos, state);
            callback.cancel();
        }
    }
}
