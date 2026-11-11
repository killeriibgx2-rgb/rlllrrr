package com.zzynes.sarcophag.DRONE;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class HandheldSirenRenderer extends GeoItemRenderer<HandheldSirenItem> {
    public static final HandheldSirenRenderer INSTANCE = new HandheldSirenRenderer();

    public static final net.minecraftforge.client.extensions.common.IClientItemExtensions EXTENSIONS =
            new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return INSTANCE;
                }
            };

    public HandheldSirenRenderer() {
        super(new HandheldSirenModel());
    }

    @Override
    public long getInstanceId(HandheldSirenItem animatable) {
        ItemStack stack = this.getCurrentItemStack();
        int stackIdentity = stack != null ? System.identityHashCode(stack) : 0;
        int perspectiveId = this.renderPerspective != null ? this.renderPerspective.ordinal() : 0;
        return ((long) stackIdentity << 4) | (perspectiveId & 0xFL);
    }

    public static boolean isStackActivelyUsed(ItemStack stack, ItemDisplayContext perspective) {
        if (stack == null || stack.isEmpty() || perspective == null) {
            return false;
        }
        if (perspective != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && perspective != ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                && perspective != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                && perspective != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        for (Player player : mc.level.players()) {
            if (player.isUsingItem() && player.getItemInHand(player.getUsedItemHand()) == stack) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();

        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.translate(0.61D, 0.30D, 0.5D);
            poseStack.mulPose(Axis.XP.rotationDegrees(30.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(31.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(0.0F));
            poseStack.scale(1.76F, 1.76F, 1.76F);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
        }

        super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);

        poseStack.popPose();
    }
}
