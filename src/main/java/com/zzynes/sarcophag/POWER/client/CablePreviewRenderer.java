package com.zzynes.sarcophag.POWER.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzynes.sarcophag.DRONE.SarcophagMod;
import com.zzynes.sarcophag.POWER.CableRoute;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

// Призрак трассы: тонкие рамки по отрезкам. Зелёный если путь есть, красный если упёрся.
@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = Dist.CLIENT)
public final class CablePreviewRenderer {

    private CablePreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !CablePlacer.active()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        float red = CablePlacer.valid() ? 0.35f : 1.0f;
        float green = CablePlacer.valid() ? 1.0f : 0.25f;
        float blue = CablePlacer.valid() ? 0.40f : 0.20f;

        double half = CablePlacer.tier().radius;
        if (CablePlacer.startAnchor() != null) {
            Vec3 anchor = CablePlacer.startAnchor().point();
            box(poseStack, buffer, new AABB(anchor, anchor).inflate(half * 1.5), 1.0f, 0.85f, 0.3f, 1.0f);
        }
        CableRoute preview = CablePlacer.preview();
        for (int i = 0; i + 1 < preview.size(); i++) {
            Vec3 a = preview.pointAt(i);
            Vec3 b = preview.pointAt(i + 1);
            box(poseStack, buffer, new AABB(a, b).inflate(half), red, green, blue, 1.0f);
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void box(PoseStack poseStack, VertexConsumer buffer, AABB box, float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();
        double x0 = box.minX;
        double y0 = box.minY;
        double z0 = box.minZ;
        double x1 = box.maxX;
        double y1 = box.maxY;
        double z1 = box.maxZ;
        line(poseStack, buffer, matrix, x0, y0, z0, x1, y0, z0, r, g, b, a);
        line(poseStack, buffer, matrix, x1, y0, z0, x1, y0, z1, r, g, b, a);
        line(poseStack, buffer, matrix, x1, y0, z1, x0, y0, z1, r, g, b, a);
        line(poseStack, buffer, matrix, x0, y0, z1, x0, y0, z0, r, g, b, a);
        line(poseStack, buffer, matrix, x0, y1, z0, x1, y1, z0, r, g, b, a);
        line(poseStack, buffer, matrix, x1, y1, z0, x1, y1, z1, r, g, b, a);
        line(poseStack, buffer, matrix, x1, y1, z1, x0, y1, z1, r, g, b, a);
        line(poseStack, buffer, matrix, x0, y1, z1, x0, y1, z0, r, g, b, a);
        line(poseStack, buffer, matrix, x0, y0, z0, x0, y1, z0, r, g, b, a);
        line(poseStack, buffer, matrix, x1, y0, z0, x1, y1, z0, r, g, b, a);
        line(poseStack, buffer, matrix, x1, y0, z1, x1, y1, z1, r, g, b, a);
        line(poseStack, buffer, matrix, x0, y0, z1, x0, y1, z1, r, g, b, a);
    }

    private static void line(PoseStack poseStack, VertexConsumer buffer, Matrix4f matrix, double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b, float a) {
        float dx = (float) (x1 - x0);
        float dy = (float) (y1 - y0);
        float dz = (float) (z1 - z0);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-5f) {
            return;
        }
        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;
        buffer.vertex(matrix, (float) x0, (float) y0, (float) z0).color(r, g, b, a).normal(poseStack.last().normal(), nx, ny, nz).endVertex();
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(poseStack.last().normal(), nx, ny, nz).endVertex();
    }
}
