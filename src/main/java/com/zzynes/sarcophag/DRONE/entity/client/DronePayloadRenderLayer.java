package com.zzynes.sarcophag.DRONE.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zzynes.sarcophag.DRONE.entity.FpvDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

@OnlyIn(Dist.CLIENT)
public class DronePayloadRenderLayer extends GeoRenderLayer<FpvDroneEntity> {

    private static final ResourceLocation RPG_TEXTURE = new ResourceLocation("sarcophag", "textures/entity/rpg_snaryad_texture.png");
    private static final ResourceLocation CI4_TEXTURE = new ResourceLocation("sarcophag", "textures/entity/ci4_texture.png");
    private final RpgShellModel<FpvDroneEntity> rpgModel;
    private final Ci4Model<FpvDroneEntity> ci4Model;

    public DronePayloadRenderLayer(GeoRenderer<FpvDroneEntity> renderer) {
        super(renderer);
        this.rpgModel = new RpgShellModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(RpgShellModel.LAYER_LOCATION));
        this.ci4Model = new Ci4Model<>(Minecraft.getInstance().getEntityModels().bakeLayer(Ci4Model.LAYER_LOCATION));
    }

    @Override
    public void renderForBone(
            PoseStack poseStack,
            FpvDroneEntity drone,
            GeoBone bone,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            float partialTick,
            int packedLight,
            int packedOverlay
    ) {
        int payload = drone.getPayloadType();
        boolean rpg = payload == 1 && bone.getName().equals("snar");
        boolean ci4 = payload == 2 && bone.getName().equals("ci4_snar");
        if (!rpg && !ci4) {
            return;
        }
        poseStack.pushPose();
        if (rpg) {
            poseStack.translate(0.0, -23.5 / 32.0, -5.0 / 32.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
        } else {
            poseStack.translate(0.0, -21.0 / 32.0, 1.0 / 32.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
        }
        poseStack.scale(0.5f, 0.5f, 0.5f);
        ResourceLocation texture = rpg ? RPG_TEXTURE : CI4_TEXTURE;
        VertexConsumer itemBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
        if (rpg) {
            rpgModel.renderToBuffer(poseStack, itemBuffer, packedLight, packedOverlay, 1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            ci4Model.renderToBuffer(poseStack, itemBuffer, packedLight, packedOverlay, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        poseStack.popPose();
    }
}
