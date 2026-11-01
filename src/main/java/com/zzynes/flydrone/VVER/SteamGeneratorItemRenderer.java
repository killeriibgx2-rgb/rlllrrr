package com.zzynes.flydrone.VVER;

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
public class SteamGeneratorItemRenderer extends GeoItemRenderer<SteamGeneratorBlockItem> {
    public static final SteamGeneratorItemRenderer INSTANCE = new SteamGeneratorItemRenderer();
    public static final net.minecraftforge.client.extensions.common.IClientItemExtensions EXTENSIONS =
            new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return INSTANCE;
                }
            };

    public SteamGeneratorItemRenderer() {
        super(new SteamGeneratorGeoModel<>());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(10.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(-35.0f));
        poseStack.scale(0.16f, 0.16f, 0.16f);
        poseStack.translate(0.0f, -2.5f, 0.0f);
        super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
