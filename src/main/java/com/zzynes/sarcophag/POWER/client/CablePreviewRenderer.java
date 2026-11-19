package com.zzynes.sarcophag.POWER.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzynes.sarcophag.DRONE.SarcophagMod;
import com.zzynes.sarcophag.POWER.Anchor;
import com.zzynes.sarcophag.POWER.CableRoute;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

// Призрак: полупрозрачная трубка ровно той толщины, что встанет, и крестик точки отсчёта.
@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = Dist.CLIENT)
public final class CablePreviewRenderer {

    private CablePreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        boolean active = CablePlacer.active();
        Anchor marker = active ? CablePlacer.startAnchor() : CablePlacer.hoverAnchor();
        CableRoute preview = CablePlacer.preview();
        if (marker == null && preview.size() < 2) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        double half = CablePlacer.tier().radius;
        if (preview.size() >= 2) {
            float red = CablePlacer.valid() ? 0.35f : 1.0f;
            float green = CablePlacer.valid() ? 1.0f : 0.30f;
            float blue = CablePlacer.valid() ? 0.45f : 0.25f;
            VertexConsumer solid = buffers.getBuffer(RenderType.debugQuads());
            for (int i = 0; i + 1 < preview.size(); i++) {
                Vec3 a = preview.pointAt(i);
                Vec3 b = preview.pointAt(i + 1);
                if (a.distanceToSqr(b) < 1.0e-8) {
                    continue;
                }
                box(solid, matrix, a, b, half, red, green, blue, 0.55f);
            }
            buffers.endBatch(RenderType.debugQuads());
        }
        if (marker != null) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            double arm = Math.max(0.06, half * 3.0);
            Vec3 point = marker.point();
            Vec3 shift = new Vec3(marker.face().getStepX(), marker.face().getStepY(), marker.face().getStepZ()).scale(half + 0.003);
            point = point.add(shift);
            segment(lines, matrix, poseStack, point.add(-arm, 0, 0), point.add(arm, 0, 0), 1.0f, 0.85f, 0.25f);
            segment(lines, matrix, poseStack, point.add(0, -arm, 0), point.add(0, arm, 0), 1.0f, 0.85f, 0.25f);
            segment(lines, matrix, poseStack, point.add(0, 0, -arm), point.add(0, 0, arm), 1.0f, 0.85f, 0.25f);
            buffers.endBatch(RenderType.lines());
        }
        poseStack.popPose();
    }

    // Коробка вокруг отрезка: расширяем только по двум осям, вдоль оси остаётся точный отрезок.
    private static void box(VertexConsumer buffer, Matrix4f matrix, Vec3 a, Vec3 b, double half, float r, float g, float bl, float alpha) {
        float x0 = (float) (Math.min(a.x, b.x) - (Math.abs(a.x - b.x) < 1.0e-6 ? half : 0.0));
        float x1 = (float) (Math.max(a.x, b.x) + (Math.abs(a.x - b.x) < 1.0e-6 ? half : 0.0));
        float y0 = (float) (Math.min(a.y, b.y) - (Math.abs(a.y - b.y) < 1.0e-6 ? half : 0.0));
        float y1 = (float) (Math.max(a.y, b.y) + (Math.abs(a.y - b.y) < 1.0e-6 ? half : 0.0));
        float z0 = (float) (Math.min(a.z, b.z) - (Math.abs(a.z - b.z) < 1.0e-6 ? half : 0.0));
        float z1 = (float) (Math.max(a.z, b.z) + (Math.abs(a.z - b.z) < 1.0e-6 ? half : 0.0));
        quad(buffer, matrix, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, bl, alpha);
        quad(buffer, matrix, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, bl, alpha);
        quad(buffer, matrix, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, bl, alpha);
        quad(buffer, matrix, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r, g, bl, alpha);
        quad(buffer, matrix, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, r, g, bl, alpha);
        quad(buffer, matrix, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, bl, alpha);
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        buffer.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x3, y3, z3).color(r, g, b, a).endVertex();
    }

    private static void segment(VertexConsumer buffer, Matrix4f matrix, PoseStack poseStack, Vec3 a, Vec3 b, float r, float g, float bl) {
        buffer.vertex(matrix, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, 1.0f).normal(poseStack.last().normal(), 0.0f, 1.0f, 0.0f).endVertex();
        buffer.vertex(matrix, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, 1.0f).normal(poseStack.last().normal(), 0.0f, 1.0f, 0.0f).endVertex();
    }
}
