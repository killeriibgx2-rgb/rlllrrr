package com.zzynes.sarcophag.mixin;

import com.zzynes.sarcophag.DRONE.FpvKillcamManager;
import com.zzynes.sarcophag.DRONE.PipReplayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = LevelRenderer.class, priority = 1001)
public abstract class LevelRendererVanillaOriginMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Redirect(
            method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"),
            require = 0
    )
    private double sarcophag$cameraEntityX(LocalPlayer player) {
        if (FpvKillcamManager.isActive()) {
            return FpvKillcamManager.getCurrentCameraPos().x;
        }
        if (PipReplayRenderer.isRendering()) return PipReplayRenderer.getCameraPosition().x;
        Entity cam = this.minecraft.getCameraEntity();
        return cam != null ? cam.getX() : player.getX();
    }

    @Redirect(
            method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"),
            require = 0
    )
    private double sarcophag$cameraEntityY(LocalPlayer player) {
        if (FpvKillcamManager.isActive()) {
            return FpvKillcamManager.getCurrentCameraPos().y;
        }
        if (PipReplayRenderer.isRendering()) return PipReplayRenderer.getCameraPosition().y;
        Entity cam = this.minecraft.getCameraEntity();
        return cam != null ? cam.getY() : player.getY();
    }

    @Redirect(
            method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"),
            require = 0
    )
    private double sarcophag$cameraEntityZ(LocalPlayer player) {
        if (FpvKillcamManager.isActive()) {
            return FpvKillcamManager.getCurrentCameraPos().z;
        }
        if (PipReplayRenderer.isRendering()) return PipReplayRenderer.getCameraPosition().z;
        Entity cam = this.minecraft.getCameraEntity();
        return cam != null ? cam.getZ() : player.getZ();
    }

    @Redirect(
            method = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"),
            require = 0
    )
    private Vec3 sarcophag$cameraEyePos(LocalPlayer player, float partialTick) {
        if (FpvKillcamManager.isActive()) {
            return FpvKillcamManager.getCurrentCameraPos();
        }
        if (PipReplayRenderer.isRendering()) return PipReplayRenderer.getCameraPosition();
        Entity cam = this.minecraft.getCameraEntity();
        return cam != null ? cam.getEyePosition(partialTick) : player.getEyePosition(partialTick);
    }
}
