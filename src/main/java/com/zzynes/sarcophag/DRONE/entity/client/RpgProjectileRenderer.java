package com.zzynes.sarcophag.DRONE.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zzynes.sarcophag.DRONE.entity.RpgProjectileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class RpgProjectileRenderer extends EntityRenderer<RpgProjectileEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("sarcophag", "textures/entity/rpg_snaryad_texture.png");
    private final RpgShellModel<RpgProjectileEntity> model;

    public RpgProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new RpgShellModel<>(context.bakeLayer(RpgShellModel.LAYER_LOCATION));
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(RpgProjectileEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.25, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        poseStack.scale(-0.5f, -0.5f, 0.5f);
        poseStack.translate(0.0, -1.5, 0.0);
        this.model.setupAnim(entity, 0.0f, 0.0f, entity.tickCount + partialTick, 0.0f, 0.0f);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight, 0, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RpgProjectileEntity entity) {
        return TEXTURE;
    }
}
