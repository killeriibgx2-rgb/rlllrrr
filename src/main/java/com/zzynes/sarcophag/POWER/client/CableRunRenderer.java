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

// Кабель это квадратный в сечении брус. На изломе отрезки заходят друг в друга
// на половину толщины, поэтому угол закрывается сам, без отдельных кубиков.
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
        CableCore core = entity.core();
        double half = entity.tier().radius;
        poseStack.pushPose();
        poseStack.translate(-entity.getX(), -entity.getY(), -entity.getZ());
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        for (int i = 0; i < segments; i++) {
            Vec3 a = route.pointAt(i);
            Vec3 b = route.pointAt(i + 1);
            int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(a));
            bar(buffer, matrix, normal, a, b, half, core, light, i == 0, i == segments - 1);
        }
        // на изломе ставим вставку ровно по сечению: щели закрыты, лишних шипов нет
        for (int i = 1; i + 1 < route.size(); i++) {
            Vec3 point = route.pointAt(i);
            int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(point));
            joint(buffer, matrix, normal, point, half, core, light);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void bar(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 from, Vec3 to, double half, CableCore core, int light, boolean capStart, boolean capEnd) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < 1.0e-6) {
            return;
        }
        axis = axis.normalize();
        Vec3 reference = Math.abs(axis.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = axis.cross(reference).normalize().scale(half);
        Vec3 up = side.cross(axis).normalize().scale(half);
        Vec3 a0 = from.subtract(side).subtract(up);
        Vec3 a1 = from.subtract(side).add(up);
        Vec3 a2 = from.add(side).add(up);
        Vec3 a3 = from.add(side).subtract(up);
        Vec3 b0 = to.subtract(side).subtract(up);
        Vec3 b1 = to.subtract(side).add(up);
        Vec3 b2 = to.add(side).add(up);
        Vec3 b3 = to.add(side).subtract(up);

        // боковые грани: текстура тянется вдоль кабеля по одному тайлу на блок
        float u = (float) length;
        sideQuad(buffer, matrix, normals, a0, a1, b1, b0, side.normalize().scale(-1), core, light, u);
        sideQuad(buffer, matrix, normals, a3, a2, b2, b3, side.normalize(), core, light, u);
        sideQuad(buffer, matrix, normals, a0, a3, b3, b0, up.normalize().scale(-1), core, light, u);
        sideQuad(buffer, matrix, normals, a1, a2, b2, b1, up.normalize(), core, light, u);

        if (capStart) {
            cap(buffer, matrix, normals, a0, a1, a2, a3, axis.scale(-1), core, light);
        }
        if (capEnd) {
            cap(buffer, matrix, normals, b0, b1, b2, b3, axis, core, light);
        }
    }

    private static void joint(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 point, double half, CableCore core, int light) {
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
        cap(buffer, matrix, normals, p0, p1, p2, p3, new Vec3(0, 0, -1), core, light);
        cap(buffer, matrix, normals, p5, p4, p7, p6, new Vec3(0, 0, 1), core, light);
        cap(buffer, matrix, normals, p4, p5, p1, p0, new Vec3(0, -1, 0), core, light);
        cap(buffer, matrix, normals, p3, p2, p6, p7, new Vec3(0, 1, 0), core, light);
        cap(buffer, matrix, normals, p4, p0, p3, p7, new Vec3(-1, 0, 0), core, light);
        cap(buffer, matrix, normals, p1, p5, p6, p2, new Vec3(1, 0, 0), core, light);
    }

    private static void sideQuad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 face, CableCore core, int light, float u) {
        vertex(buffer, matrix, normals, a, 0f, 1f, face, core, light);
        vertex(buffer, matrix, normals, b, 0f, 0f, face, core, light);
        vertex(buffer, matrix, normals, c, u, 0f, face, core, light);
        vertex(buffer, matrix, normals, d, u, 1f, face, core, light);
    }

    private static void cap(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 face, CableCore core, int light) {
        vertex(buffer, matrix, normals, a, 0f, 1f, face, core, light);
        vertex(buffer, matrix, normals, b, 0f, 0f, face, core, light);
        vertex(buffer, matrix, normals, c, 1f, 0f, face, core, light);
        vertex(buffer, matrix, normals, d, 1f, 1f, face, core, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 point, float u, float v, Vec3 face, CableCore core, int light) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(core.red(), core.green(), core.blue(), 1.0f)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normals, (float) face.x, (float) face.y, (float) face.z)
                .endVertex();
    }
}
