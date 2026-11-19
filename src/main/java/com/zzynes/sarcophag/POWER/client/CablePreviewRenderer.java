package com.zzynes.sarcophag.POWER.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzynes.sarcophag.DRONE.SarcophagMod;
import com.zzynes.sarcophag.POWER.Anchor;
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

// Призрак: только сама полоска по толщине кабеля и крестик на точке отсчёта.
// Никаких крупных кубов: то, что видно, совпадает с тем, что встанет.
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
        Anchor hover = active ? CablePlacer.startAnchor() : CablePlacer.hoverAnchor();
        if (!active && hover == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        double half = CablePlacer.tier().radius;

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        if (hover != null) {
            double arm = Math.max(0.05, half * 2.5);
            Vec3 point = hover.point();
            Vec3 nudge = new Vec3(hover.face().getStepX(), hover.face().getStepY(), hover.face().getStepZ()).scale(half + 0.004);
            point = point.add(nudge);
            float r = active ? 1.0f : 0.85f;
            float g = active ? 0.85f : 0.9f;
            float b = active ? 0.25f : 1.0f;
            segment(poseStack, lines, point.add(-arm, 0, 0), point.add(arm, 0, 0), r, g, b);
            segment(poseStack, lines, point.add(0, -arm, 0), point.add(0, arm, 0), r, g, b);
            segment(poseStack, lines, point.add(0, 0, -arm), point.add(0, 0, arm), r, g, b);
        }
        poseStack.popPose();

        if (!active) {
            buffers.endBatch(RenderType.lines());
            return;
        }

        float red = CablePlacer.valid() ? 0.35f : 1.0f;
        float green = CablePlacer.valid() ? 1.0f : 0.25f;
        float blue = CablePlacer.valid() ? 0.40f : 0.20f;
        CableRoute preview = CablePlacer.preview();
        for (int i = 0; i + 1 < preview.size(); i++) {
            Vec3 a = preview.pointAt(i);
            Vec3 b = preview.pointAt(i + 1);
            box(poseStack, lines, new AABB(a, b).inflate(half), red, green, blue);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void box(PoseStack poseStack, VertexConsumer buffer, AABB box, float r, float g, float b) {
        Matrix4f matrix = poseStack.last().pose();
        double x0 = box.minX;
        double y0 = box.minY;
        double z0 = box.minZ;
        double x1 = box.maxX;
        double y1 = box.maxY;
        double z1 = box.maxZ;
        line(poseStack, buffer, matrix, x0, y0, z0, x1, y0, z0, r, g, b);
        line(poseStack, buffer, matrix, x1, y0, z0, x1, y0, z1, r, g, b);
        line(poseStack, buffer, matrix, x1, y0, z1, x0, y0, z1, r, g, b);
        line(poseStack, buffer, matrix, x0, y0, z1, x0, y0, z0, r, g, b);
        line(poseStack, buffer, matrix, x0, y1, z0, x1, y1, z0, r, g, b);
        line(poseStack, buffer, matrix, x1, y1, z0, x1, y1, z1, r, g, b);
        line(poseStack, buffer, matrix, x1, y1, z1, x0, y1, z1, r, g, b);
        line(poseStack, buffer, matrix, x0, y1, z1, x0, y1, z0, r, g, b);
        line(poseStack, buffer, matrix, x0, y0, z0, x0, y1, z0, r, g, b);
        line(poseStack, buffer, matrix, x1, y0, z0, x1, y1, z0, r, g, b);
        line(poseStack, buffer, matrix, x1, y0, z1, x1, y1, z1, r, g, b);
        line(poseStack, buffer, matrix, x0, y0, z1, x0, y1, z1, r, g, b);
    }

    private static void segment(PoseStack poseStack, VertexConsumer buffer, Vec3 a, Vec3 b, float r, float g, float bl) {
        line(poseStack, buffer, poseStack.last().pose(), a.x, a.y, a.z, b.x, b.y, b.z, r, g, bl);
    }

    private static void line(PoseStack poseStack, VertexConsumer buffer, Matrix4f matrix, double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b) {
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
        buffer.vertex(matrix, (float) x0, (float) y0, (float) z0).color(r, g, b, 1.0f).normal(poseStack.last().normal(), nx, ny, nz).endVertex();
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, 1.0f).normal(poseStack.last().normal(), nx, ny, nz).endVertex();
    }
}
