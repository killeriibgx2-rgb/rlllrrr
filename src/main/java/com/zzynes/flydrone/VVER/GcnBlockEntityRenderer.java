package com.zzynes.flydrone.VVER;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

@OnlyIn(Dist.CLIENT)
public class GcnBlockEntityRenderer extends GeoBlockRenderer<GcnBlockEntity> {
    public GcnBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new GcnGeoModel<>());
    }

    @Override
    public void preRender(PoseStack poseStack, GcnBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        Direction back = animatable.getBlockState().getValue(GcnBlock.FACING).getOpposite();
        poseStack.translate(back.getStepX(), 0.0D, back.getStepZ());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        super.rotateBlock(facing.getOpposite(), poseStack);
    }

    @Override
    public boolean shouldRenderOffScreen(GcnBlockEntity blockEntity) {
        return true;
    }
}
