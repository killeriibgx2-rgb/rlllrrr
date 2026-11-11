package com.zzynes.sarcophag.VVER;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@OnlyIn(Dist.CLIENT)
public class BshuItemRenderer extends GeoItemRenderer<BshuBlockItem> {
    public static final BshuItemRenderer INSTANCE = new BshuItemRenderer();
    public static final net.minecraftforge.client.extensions.common.IClientItemExtensions EXTENSIONS =
            new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return INSTANCE;
                }
            };

    public BshuItemRenderer() {
        super(new BshuGeoModel<>());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (stack.getItem() instanceof BshuBlockItem item) {
            BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(item));
            BshuClientGeometry.ensureBuilt(model);
        }
        Vec3 min = BshuClientGeometry.getModelMin();
        Vec3 max = BshuClientGeometry.getModelMax();
        Vec3 center = min.add(max).scale(0.5);
        double size = Math.max(Math.max(max.x - min.x, max.y - min.y), max.z - min.z);
        float scale = size <= 0.0 ? 0.05f : (float) (0.82 / size);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(18.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(-32.0f));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-center.x, -center.y, -center.z);
        super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
