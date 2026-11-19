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

// Кабель это ряд кубиков по клеткам блока. Каждая грань получает полную текстуру,
// поэтому рисунок читается как блочный, без растяжений и без заглушек на углах.
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
        double thickness = entity.tier().radius * 2.0;
        Vec3 shift = entity.renderShift();
        poseStack.pushPose();
        poseStack.translate(-entity.getX(), -entity.getY(), -entity.getZ());
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        for (int i = 0; i + 1 < route.size(); i++) {
            Vec3 a = route.pointAt(i).add(shift);
            Vec3 b = route.pointAt(i + 1).add(shift);
            int pieces = Math.max(1, (int) Math.round(a.distanceTo(b)));
            for (int p = 0; p < pieces; p++) {
                Vec3 from = a.lerp(b, p / (double) pieces);
                Vec3 to = a.lerp(b, (p + 1) / (double) pieces);
                int light = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(from));
                box(buffer, matrix, normal, from, to, thickness, core, light);
            }
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void box(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 from, Vec3 to, double thickness, CableCore core, int light) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1.0e-8) {
            return;
        }
        axis = axis.normalize();
        Vec3 reference = Math.abs(axis.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = axis.cross(reference).normalize().scale(thickness * 0.5);
        Vec3 up = side.cross(axis).normalize().scale(thickness * 0.5);
        Vec3 a0 = from.subtract(side).subtract(up);
        Vec3 a1 = from.subtract(side).add(up);
        Vec3 a2 = from.add(side).add(up);
        Vec3 a3 = from.add(side).subtract(up);
        Vec3 b0 = to.subtract(side).subtract(up);
        Vec3 b1 = to.subtract(side).add(up);
        Vec3 b2 = to.add(side).add(up);
        Vec3 b3 = to.add(side).subtract(up);
        quad(buffer, matrix, normals, a0, a1, a2, a3, axis.scale(-1), core, light);
        quad(buffer, matrix, normals, b0, b1, b2, b3, axis, core, light);
        quad(buffer, matrix, normals, a0, a1, b1, b0, side.normalize().scale(-1), core, light);
        quad(buffer, matrix, normals, a3, a2, b2, b3, side.normalize(), core, light);
        quad(buffer, matrix, normals, a0, a3, b3, b0, up.normalize().scale(-1), core, light);
        quad(buffer, matrix, normals, a1, a2, b2, b1, up.normalize(), core, light);
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 face, CableCore core, int light) {
        float r = core.red();
        float g = core.green();
        float bl = core.blue();
        vertex(buffer, matrix, normals, a, 0f, 1f, face, r, g, bl, light);
        vertex(buffer, matrix, normals, b, 0f, 0f, face, r, g, bl, light);
        vertex(buffer, matrix, normals, c, 1f, 0f, face, r, g, bl, light);
        vertex(buffer, matrix, normals, d, 1f, 1f, face, r, g, bl, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 point, float u, float v, Vec3 face, float r, float g, float b, int light) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(r, g, b, 1.0f)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normals, (float) face.x, (float) face.y, (float) face.z)
                .endVertex();
    }
}
