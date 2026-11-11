package com.zzynes.sarcophag.mixin;

import com.zzynes.sarcophag.DRONE.FpvKillcamManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(
            method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("RETURN"),
            require = 0
    )
    private void sarcophag$applyKillcamOrbit(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        if (!FpvKillcamManager.isActive()) {
            return;
        }
        FpvKillcamManager.CameraPose pose = FpvKillcamManager.computeCameraPose(partialTick);
        this.detached = true;
        this.setPosition(pose.pos().x, pose.pos().y, pose.pos().z);
        this.setRotation(pose.yaw(), pose.pitch());
    }
}
