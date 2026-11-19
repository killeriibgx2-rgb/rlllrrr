package com.zzynes.sarcophag.POWER.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzynes.sarcophag.POWER.CableCore;
import com.zzynes.sarcophag.POWER.CableRoute;
import com.zzynes.sarcophag.POWER.CableRunEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

// Кабель это полосы по граням: четыре боковые плоскости на отрезок плюс заглушки на изломах.
public class CableRunRenderer extends EntityRenderer<CableRunEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("sarcophag", "textures/entity/cable.png");

    public CableRunRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CableRunEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(CableRunEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        CableRoute route = entity.route();
        if (route.isEmpty()) {
            return;
        }
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        CableCore core = entity.core();
        double radius = entity.tier().radius;
        poseStack.pushPose();
        poseStack.translate(-entity.getX(), -entity.getY(), -entity.getZ());
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        for (int i = 0; i + 1 < route.size(); i++) {
            Vec3 a = route.pointAt(i);
            Vec3 b = route.pointAt(i + 1);
            Vec3 mid = a.add(b).scale(0.5);
            int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(mid.x, mid.y, mid.z));
            segment(buffer, matrix, normal, a, b, radius, core, light);
        }
        for (int i = 1; i + 1 < route.size(); i++) {
            Vec3 point = route.pointAt(i);
            int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(point.x, point.y, point.z));
            joint(buffer, matrix, normal, point, radius * 1.15, core, light);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void segment(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal, Vec3 a, Vec3 b, double radius, CableCore core, int light) {
        Vec3 axis = b.subtract(a).normalize();
        Vec3 reference = Math.abs(axis.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = axis.cross(reference).normalize().scale(radius);
        Vec3 up = side.cross(axis).normalize().scale(radius);
        Vec3 a1 = a.add(side);
        Vec3 a2 = a.subtract(side);
        Vec3 a3 = a.add(up);
        Vec3 a4 = a.subtract(up);
        Vec3 b1 = b.add(side);
        Vec3 b2 = b.subtract(side);
        Vec3 b3 = b.add(up);
        Vec3 b4 = b.subtract(up);
        quad(buffer, matrix, normal, a1, b1, b3, a3, up.normalize(), core, light);
        quad(buffer, matrix, normal, a4, b4, b2, a2, up.normalize().scale(-1), core, light);
        quad(buffer, matrix, normal, a3, b3, b4, a4, side.normalize(), core, light);
        quad(buffer, matrix, normal, a2, b2, b1, a1, side.normalize().scale(-1), core, light);
    }

    private static void joint(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal, Vec3 point, double radius, CableCore core, int light) {
        Vec3 x = new Vec3(radius, 0, 0);
        Vec3 y = new Vec3(0, radius, 0);
        Vec3 z = new Vec3(0, 0, radius);
        Vec3 p0 = point.subtract(x).subtract(y).subtract(z);
        Vec3 p1 = point.add(x).subtract(y).subtract(z);
        Vec3 p2 = point.add(x).add(y).subtract(z);
        Vec3 p3 = point.subtract(x).add(y).subtract(z);
        Vec3 p4 = point.subtract(x).subtract(y).add(z);
        Vec3 p5 = point.add(x).subtract(y).add(z);
        Vec3 p6 = point.add(x).add(y).add(z);
        Vec3 p7 = point.subtract(x).add(y).add(z);
        quad(buffer, matrix, normal, p0, p1, p2, p3, new Vec3(0, 0, -1), core, light);
        quad(buffer, matrix, normal, p4, p5, p6, p7, new Vec3(0, 0, 1), core, light);
        quad(buffer, matrix, normal, p0, p1, p5, p4, new Vec3(0, -1, 0), core, light);
        quad(buffer, matrix, normal, p3, p2, p6, p7, new Vec3(0, 1, 0), core, light);
        quad(buffer, matrix, normal, p0, p3, p7, p4, new Vec3(-1, 0, 0), core, light);
        quad(buffer, matrix, normal, p1, p2, p6, p5, new Vec3(1, 0, 0), core, light);
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 faceNormal, CableCore core, int light) {
        float r = core.red();
        float g = core.green();
        float bl = core.blue();
        vertex(buffer, matrix, normal, a, 0f, 1f, faceNormal, r, g, bl, light);
        vertex(buffer, matrix, normal, b, 1f, 1f, faceNormal, r, g, bl, light);
        vertex(buffer, matrix, normal, c, 1f, 0f, faceNormal, r, g, bl, light);
        vertex(buffer, matrix, normal, d, 0f, 0f, faceNormal, r, g, bl, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal, Vec3 point, float u, float v, Vec3 faceNormal, float r, float g, float b, int light) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(r, g, b, 1.0f)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, (float) faceNormal.x, (float) faceNormal.y, (float) faceNormal.z)
                .endVertex();
    }
}
