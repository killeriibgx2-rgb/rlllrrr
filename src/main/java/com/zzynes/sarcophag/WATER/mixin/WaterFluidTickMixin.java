package com.zzynes.sarcophag.WATER.mixin;

import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaterFluid.class)
public abstract class WaterFluidTickMixin {
    @Inject(method = "getTickDelay", at = @At("HEAD"), cancellable = true)
    private void sarcophag$finiteWaterTickDelay(LevelReader level, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(2);
    }
}
