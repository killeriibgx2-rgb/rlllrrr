package com.zzynes.flydrone.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzynes.flydrone.DRONE.FpvKillcamManager;
import com.zzynes.flydrone.DRONE.PipReplayRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    @Inject(
            method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At("HEAD")
    )
    private void flyDrone$onSetupRenderHead(Camera camera, Frustum frustum, boolean capturedFrustum, boolean isSpectator, CallbackInfo ci) {
        if (FpvKillcamManager.isActive()) {
            FpvKillcamManager.onBeginRenderWorld();
        }
    }

    @Redirect(
            method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D")
    )
    private double flyDrone$cameraEntityX(LocalPlayer player) {
        if (FpvKillcamManager.isActive()) {
            return FpvKillcamManager.getCurrentCameraPos().x;
        }
        if (PipReplayRenderer.isRendering()) return PipReplayRenderer.getCameraPosition().x;
        Entity cam = this.minecraft.getCameraEntity();
        return cam != null ? cam.getX() : player.getX();
    }

    @Redirect(
            method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D")
    )
    private double flyDrone$cameraEntityY(LocalPlayer player) {
        if (FpvKillcamManager.isActive()) {
            return FpvKillcamManager.getCurrentCameraPos().y;
        }
        if (PipReplayRenderer.isRendering()) return PipReplayRenderer.getCameraPosition().y;
        Entity cam = this.minecraft.getCameraEntity();
        return cam != null ? cam.getY() : player.getY();
    }

    @Redirect(
            method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D")
    )
    private double flyDrone$cameraEntityZ(LocalPlayer player) {
        if (FpvKillcamManager.isActive()) {
            return FpvKillcamManager.getCurrentCameraPos().z;
        }
        if (PipReplayRenderer.isRendering()) return PipReplayRenderer.getCameraPosition().z;
        Entity cam = this.minecraft.getCameraEntity();
        return cam != null ? cam.getZ() : player.getZ();
    }

    @Redirect(
            method = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 flyDrone$cameraEyePos(LocalPlayer player, float partialTick) {
        if (FpvKillcamManager.isActive()) {
            return FpvKillcamManager.getCurrentCameraPos();
        }
        if (PipReplayRenderer.isRendering()) return PipReplayRenderer.getCameraPosition();
        Entity cam = this.minecraft.getCameraEntity();
        return cam != null ? cam.getEyePosition(partialTick) : player.getEyePosition(partialTick);
    }

    @Inject(
            method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void flyDrone$renderEntityTimeShifted(
            Entity entity,
            double camX,
            double camY,
            double camZ,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci
    ) {
        if (PipReplayRenderer.isRendering()) {
            ci.cancel();
            return;
        }
        if (!FpvKillcamManager.isActive()) {
            return;
        }
        FpvKillcamManager.renderLiveEntityInKillcam(
                entity,
                camX,
                camY,
                camZ,
                partialTick,
                poseStack,
                bufferSource,
                this.entityRenderDispatcher
        );
        ci.cancel();
    }
}
