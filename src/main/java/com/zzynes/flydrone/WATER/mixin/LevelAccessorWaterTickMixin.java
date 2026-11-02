package com.zzynes.flydrone.WATER.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelAccessor.class)
public interface LevelAccessorWaterTickMixin {
    @ModifyVariable(method = "scheduleTick(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/Fluid;I)V", at = @At("HEAD"), index = 3, argsOnly = true)
    private int flyDrone$clampWaterTickDelay(int delay, BlockPos pos, Fluid fluid, int originalDelay) {
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            return Math.min(delay, 2);
        }
        return delay;
    }
}
