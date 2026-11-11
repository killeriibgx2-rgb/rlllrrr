package com.zzynes.sarcophag.VVER;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

@OnlyIn(Dist.CLIENT)
public class BshuBlockEntityRenderer extends GeoBlockRenderer<BshuBlockEntity> {

    public BshuBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new BshuGeoModel<>());
    }

    @Override
    public void preRender(PoseStack poseStack, BshuBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        BshuClientGeometry.ensureBuilt(model);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void renderFinal(PoseStack poseStack, BshuBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                            float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        BshuClientGeometry.ensureBuilt(model);
        BshuScreenTextures.renderScreens(animatable, partialTick, poseStack, bufferSource);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, BshuBlockEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        float useRed = red;
        float useGreen = green;
        float useBlue = blue;
        int useLight = packedLight;
        long gameTime = animatable.getLevel() == null ? 0L : animatable.getLevel().getGameTime();
        String parentName = bone.getParent() == null ? "" : bone.getParent().getName();
        if ("lamp".equals(parentName)) {
            BshuLampState state = animatable.getLampState(bone.getName(), gameTime);
            useRed = state.red();
            useGreen = state.green();
            useBlue = state.blue();
            if (state.lit()) {
                useLight = LightTexture.FULL_BRIGHT;
            }
        } else if ("kvitir".equals(bone.getName()) && animatable.isKvitirLit(gameTime)) {
            useRed = 1.0f;
            useGreen = 0.16f;
            useBlue = 0.16f;
            useLight = LightTexture.FULL_BRIGHT;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                useLight, packedOverlay, useRed, useGreen, useBlue, alpha);
    }

    @Override
    public boolean shouldRenderOffScreen(BshuBlockEntity blockEntity) {
        return true;
    }
}
