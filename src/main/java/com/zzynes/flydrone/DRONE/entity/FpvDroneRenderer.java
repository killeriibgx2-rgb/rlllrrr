package com.zzynes.flydrone.DRONE.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zzynes.flydrone.DRONE.FpvKillcamManager;
import com.zzynes.flydrone.DRONE.FpvOverlay;
import com.zzynes.flydrone.DRONE.entity.client.DronePayloadRenderLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class FpvDroneRenderer extends GeoEntityRenderer<FpvDroneEntity> {

    public FpvDroneRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new FpvDroneModel());
        this.shadowRadius = 0.0f;
        this.addRenderLayer(new DronePayloadRenderLayer(this));
    }

    @Override
    protected void applyRotations(FpvDroneEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        Quaternionf q = animatable.getRenderOrientation(partialTick);
        poseStack.translate(0.0f, 0.25f, 0.0f);
        poseStack.mulPose(q);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.translate(0.0f, -0.25f, 0.0f);
    }

    @Override
    public boolean shouldShowName(FpvDroneEntity entity) {
        return !FpvOverlay.fpvActive && !FpvKillcamManager.isActive();
    }

    @Override
    protected void renderNameTag(FpvDroneEntity entity, Component content, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (FpvOverlay.fpvActive || FpvKillcamManager.isActive()) return;
        super.renderNameTag(entity, Component.literal("HP: " + entity.getDroneHp() + " / 5"), poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FpvDroneEntity entity) {
        return new ResourceLocation("fly_drone", "textures/entity/fpv_drone.png");
    }
}
