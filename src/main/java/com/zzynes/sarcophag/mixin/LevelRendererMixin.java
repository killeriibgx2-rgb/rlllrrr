package com.zzynes.sarcophag.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzynes.sarcophag.DRONE.FpvKillcamManager;
import com.zzynes.sarcophag.DRONE.PipReplayRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    @Inject(
            method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At("HEAD"),
            require = 0
    )
    private void sarcophag$onSetupRenderHead(Camera camera, Frustum frustum, boolean capturedFrustum, boolean isSpectator, CallbackInfo ci) {
        if (FpvKillcamManager.isActive()) {
            FpvKillcamManager.onBeginRenderWorld();
        }
    }

    @Inject(
            method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void sarcophag$renderEntityTimeShifted(
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
