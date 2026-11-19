package com.zzynes.sarcophag.POWER.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

// Кабель это квадратный брус. Развёртка: U идёт вдоль оси и копится по всей трассе,
// V это доля периметра, поэтому рисунок не зависит от поворота.
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
        int segments = route.size() - 1;
        if (segments < 1) {
            return;
        }
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        double half = entity.tier().radius;
        poseStack.pushPose();
        poseStack.translate(-entity.getX(), -entity.getY(), -entity.getZ());
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        double along = 0.0;
        for (int i = 0; i < segments; i++) {
            Vec3 a = route.pointAt(i);
            Vec3 b = route.pointAt(i + 1);
            double length = a.distanceTo(b);
            int lightA = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(a));
            int lightB = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(b));
            bar(buffer, matrix, normal, a, b, half, lightA, lightB, along, along + length, i == 0, i == segments - 1);
            along += length;
        }
        for (int i = 1; i + 1 < route.size(); i++) {
            Vec3 point = route.pointAt(i);
            int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(point));
            joint(buffer, matrix, normal, point, half, light);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    // Оси сечения жёстко привязаны к направлению: на вертикали больше нет вырождения.
    private static Vec3[] frame(Vec3 axis) {
        if (Math.abs(axis.y) > 0.9) {
            return new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        }
        if (Math.abs(axis.x) > 0.9) {
            return new Vec3[]{new Vec3(0, 0, 1), new Vec3(0, 1, 0)};
        }
        if (Math.abs(axis.z) > 0.9) {
            return new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 1, 0)};
        }
        Vec3 reference = new Vec3(0, 1, 0);
        Vec3 side = axis.cross(reference).normalize();
        return new Vec3[]{side, side.cross(axis).normalize()};
    }

    private static void bar(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 a, Vec3 b, double half,
                            int lightA, int lightB, double uStart, double uEnd, boolean capStart, boolean capEnd) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0e-8) {
            return;
        }
        axis = axis.normalize();
        Vec3[] basis = frame(axis);
        Vec3 side = basis[0].scale(half);
        Vec3 up = basis[1].scale(half);

        Vec3 a0 = a.subtract(side).subtract(up);
        Vec3 a1 = a.subtract(side).add(up);
        Vec3 a2 = a.add(side).add(up);
        Vec3 a3 = a.add(side).subtract(up);
        Vec3 b0 = b.subtract(side).subtract(up);
        Vec3 b1 = b.subtract(side).add(up);
        Vec3 b2 = b.add(side).add(up);
        Vec3 b3 = b.add(side).subtract(up);

        sideFace(buffer, matrix, normals, a1, a2, b2, b1, basis[1], 0.00f, 0.25f, lightA, lightB, uStart, uEnd);
        sideFace(buffer, matrix, normals, a0, a3, b3, b0, basis[1].scale(-1), 0.50f, 0.75f, lightA, lightB, uStart, uEnd);
        sideFace(buffer, matrix, normals, a2, a3, b3, b2, basis[0], 0.25f, 0.50f, lightA, lightB, uStart, uEnd);
        sideFace(buffer, matrix, normals, a0, a1, b1, b0, basis[0].scale(-1), 0.75f, 1.00f, lightA, lightB, uStart, uEnd);

        if (capStart) {
            cap(buffer, matrix, normals, a0, a1, a2, a3, axis.scale(-1), lightA, 0.0);
        }
        if (capEnd) {
            cap(buffer, matrix, normals, b0, b1, b2, b3, axis, lightB, 0.0);
        }
    }

    private static void sideFace(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                                 Vec3 face, float v0, float v1, int lightA, int lightB, double uStart, double uEnd) {
        vertex(buffer, matrix, normals, a, (float) uStart, v1, face, lightA);
        vertex(buffer, matrix, normals, b, (float) uStart, v0, face, lightA);
        vertex(buffer, matrix, normals, c, (float) uEnd, v0, face, lightB);
        vertex(buffer, matrix, normals, d, (float) uEnd, v1, face, lightB);
    }

    private static void cap(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 face, int light, double shift) {
        float s = (float) shift;
        vertex(buffer, matrix, normals, a, s + 0.00f, 1.00f, face, light);
        vertex(buffer, matrix, normals, b, s + 0.00f, 0.00f, face, light);
        vertex(buffer, matrix, normals, c, s + 0.25f, 0.00f, face, light);
        vertex(buffer, matrix, normals, d, s + 0.25f, 1.00f, face, light);
    }

    private static void joint(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 point, double half, int light) {
        Vec3 x = new Vec3(half, 0, 0);
        Vec3 y = new Vec3(0, half, 0);
        Vec3 z = new Vec3(0, 0, half);
        Vec3 p0 = point.subtract(x).subtract(y).subtract(z);
        Vec3 p1 = point.add(x).subtract(y).subtract(z);
        Vec3 p2 = point.add(x).add(y).subtract(z);
        Vec3 p3 = point.subtract(x).add(y).subtract(z);
        Vec3 p4 = point.subtract(x).subtract(y).add(z);
        Vec3 p5 = point.add(x).subtract(y).add(z);
        Vec3 p6 = point.add(x).add(y).add(z);
        Vec3 p7 = point.subtract(x).add(y).add(z);
        float size = (float) (half * 2.0);
        jointFace(buffer, matrix, normals, p0, p1, p2, p3, new Vec3(0, 0, -1), light, size);
        jointFace(buffer, matrix, normals, p5, p4, p7, p6, new Vec3(0, 0, 1), light, size);
        jointFace(buffer, matrix, normals, p4, p5, p1, p0, new Vec3(0, -1, 0), light, size);
        jointFace(buffer, matrix, normals, p3, p2, p6, p7, new Vec3(0, 1, 0), light, size);
        jointFace(buffer, matrix, normals, p4, p0, p3, p7, new Vec3(-1, 0, 0), light, size);
        jointFace(buffer, matrix, normals, p1, p5, p6, p2, new Vec3(1, 0, 0), light, size);
    }

    private static void jointFace(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 face, int light, float size) {
        vertex(buffer, matrix, normals, a, 0f, size, face, light);
        vertex(buffer, matrix, normals, b, 0f, 0f, face, light);
        vertex(buffer, matrix, normals, c, size, 0f, face, light);
        vertex(buffer, matrix, normals, d, size, size, face, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 point, float u, float v, Vec3 face, int light) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(1.0f, 1.0f, 1.0f, 1.0f)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normals, (float) face.x, (float) face.y, (float) face.z)
                .endVertex();
    }
}
