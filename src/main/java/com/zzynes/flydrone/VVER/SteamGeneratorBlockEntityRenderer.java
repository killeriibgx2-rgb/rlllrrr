package com.zzynes.flydrone.VVER;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

@OnlyIn(Dist.CLIENT)
public class SteamGeneratorBlockEntityRenderer extends GeoBlockRenderer<SteamGeneratorBlockEntity> {
    public SteamGeneratorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new SteamGeneratorGeoModel<>());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        super.rotateBlock(facing.getCounterClockWise(), poseStack);
    }

    @Override
    public boolean shouldRenderOffScreen(SteamGeneratorBlockEntity blockEntity) {
        return true;
    }
}
