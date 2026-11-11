package com.zzynes.sarcophag.BLOCK;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

@OnlyIn(Dist.CLIENT)
public class BatteryCabinetBlockEntityRenderer extends GeoBlockRenderer<BatteryCabinetBlockEntity> {
    public BatteryCabinetBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new BatteryCabinetGeoModel<>());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        super.rotateBlock(facing.getOpposite(), poseStack);
    }

    @Override
    public boolean shouldRenderOffScreen(BatteryCabinetBlockEntity blockEntity) {
        return true;
    }
}
