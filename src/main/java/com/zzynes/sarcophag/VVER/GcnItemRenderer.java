package com.zzynes.sarcophag.VVER;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@OnlyIn(Dist.CLIENT)
public class GcnItemRenderer extends GeoItemRenderer<GcnBlockItem> {
    public static final GcnItemRenderer INSTANCE = new GcnItemRenderer();
    public static final net.minecraftforge.client.extensions.common.IClientItemExtensions EXTENSIONS =
            new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return INSTANCE;
                }
            };

    public GcnItemRenderer() {
        super(new GcnGeoModel<>());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(18.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(-32.0f));
        poseStack.scale(0.25f, 0.25f, 0.25f);
        poseStack.translate(0.068f, -0.613f, 0.131f);
        super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
