package com.zzynes.flydrone.VVER;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nullable;

public class ValveBlockEntityRenderer implements BlockEntityRenderer<ValveBlockEntity> {
    public static final ResourceLocation WHEEL_MODEL_LOC =
            new ResourceLocation(FlyDroneMod.MOD_ID, "block/vver_valve_wheel");

    private static final double CENTER = 0.5D;
    private static final double PIVOT_Y = 14.975D / 16.0D;

    public ValveBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            ValveBlockEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        BlockState state = entity.getBlockState();
        if (!(state.getBlock() instanceof ValveBlock)) {
            return;
        }
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(WHEEL_MODEL_LOC);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());
        RandomSource random = RandomSource.create(42L);

        poseStack.pushPose();
        poseStack.translate(CENTER, CENTER, CENTER);
        switch (state.getValue(PipeBlock.AXIS)) {
            case X -> {
            }
            case Y -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            case Z -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        }
        poseStack.translate(-CENTER, -CENTER, -CENTER);
        poseStack.translate(CENTER, PIVOT_Y, CENTER);
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getInterpolatedAngle(partialTick)));
        poseStack.translate(-CENTER, -PIVOT_Y, -CENTER);

        PoseStack.Pose pose = poseStack.last();
        for (Direction direction : Direction.values()) {
            renderQuads(model, direction, random, pose, consumer, packedLight, packedOverlay);
        }
        renderQuads(model, null, random, pose, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void renderQuads(
            BakedModel model,
            @Nullable Direction direction,
            RandomSource random,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay
    ) {
        random.setSeed(42L);
        for (BakedQuad quad : model.getQuads(null, direction, random, ModelData.EMPTY, RenderType.solid())) {
            consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
        }
    }
}
