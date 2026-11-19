package com.zzynes.sarcophag.POWER.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzynes.sarcophag.POWER.CableBlockEntity;
import com.zzynes.sarcophag.POWER.CableStrand;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

// Рисуем жилы внутри клетки: кубик сечения плюс полоски до граней.
// Соседние клетки стыкуются в тех же координатах, поэтому швов нет.
public class CableBlockRenderer implements BlockEntityRenderer<CableBlockEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("sarcophag", "textures/entity/cable.png");

    public CableBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CableBlockEntity cable, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (cable.strands().isEmpty()) {
            return;
        }
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normals = poseStack.last().normal();
        for (CableStrand strand : cable.strands()) {
            Vec3 centre = strand.centre();
            double half = strand.tier.radius;
            cube(buffer, matrix, normals, centre, half, light);
            for (Direction direction : Direction.values()) {
                if (!strand.connects(direction)) {
                    continue;
                }
                Vec3 end = new Vec3(
                        direction.getStepX() > 0 ? 1.0 : direction.getStepX() < 0 ? 0.0 : centre.x,
                        direction.getStepY() > 0 ? 1.0 : direction.getStepY() < 0 ? 0.0 : centre.y,
                        direction.getStepZ() > 0 ? 1.0 : direction.getStepZ() < 0 ? 0.0 : centre.z);
                bar(buffer, matrix, normals, centre, end, half, light);
            }
        }
    }

    private static void cube(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 centre, double half, int light) {
        double x0 = centre.x - half;
        double x1 = centre.x + half;
        double y0 = centre.y - half;
        double y1 = centre.y + half;
        double z0 = centre.z - half;
        double z1 = centre.z + half;
        float u0 = 0.25f;
        float u1 = 0.75f;
        quad(buffer, matrix, normals, new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1), new Vec3(0, -1, 0), u0, u1, 0.25f, 0.50f, light);
        quad(buffer, matrix, normals, new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), new Vec3(x0, y1, z0), new Vec3(0, 1, 0), u0, u1, 0.00f, 0.25f, light);
        quad(buffer, matrix, normals, new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0), new Vec3(-1, 0, 0), u0, u1, 0.25f, 0.50f, light);
        quad(buffer, matrix, normals, new Vec3(x1, y0, z1), new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(1, 0, 0), u0, u1, 0.25f, 0.50f, light);
        quad(buffer, matrix, normals, new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y0, z0), new Vec3(0, 0, -1), u0, u1, 0.25f, 0.50f, light);
        quad(buffer, matrix, normals, new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), new Vec3(x0, y0, z1), new Vec3(0, 0, 1), u0, u1, 0.25f, 0.50f, light);
    }

    // Полоска от центра к грани клетки. Развёртка: U вдоль оси, V доля периметра.
    private static void bar(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 from, Vec3 to, double half, int light) {
        double minX = Math.min(from.x, to.x) - half;
        double maxX = Math.max(from.x, to.x) + half;
        double minY = Math.min(from.y, to.y) - half;
        double maxY = Math.max(from.y, to.y) + half;
        double minZ = Math.min(from.z, to.z) - half;
        double maxZ = Math.max(from.z, to.z) + half;
        double length = from.distanceTo(to);
        if (length < 1.0e-4) {
            return;
        }

        if (Math.abs(to.y - from.y) < 1.0e-6 && Math.abs(to.z - from.z) < 1.0e-6) {
            quad(buffer, matrix, normals, new Vec3(minX, maxY, minZ), new Vec3(maxX, maxY, minZ), new Vec3(maxX, maxY, maxZ), new Vec3(minX, maxY, maxZ), new Vec3(0, 1, 0), 0.0f, (float) length, 0.00f, 0.25f, light);
            quad(buffer, matrix, normals, new Vec3(minX, minY, maxZ), new Vec3(maxX, minY, maxZ), new Vec3(maxX, minY, minZ), new Vec3(minX, minY, minZ), new Vec3(0, -1, 0), 0.0f, (float) length, 0.50f, 0.75f, light);
            quad(buffer, matrix, normals, new Vec3(minX, minY, maxZ), new Vec3(minX, maxY, maxZ), new Vec3(maxX, maxY, maxZ), new Vec3(maxX, minY, maxZ), new Vec3(0, 0, 1), 0.0f, (float) length, 0.25f, 0.50f, light);
            quad(buffer, matrix, normals, new Vec3(maxX, minY, minZ), new Vec3(maxX, maxY, minZ), new Vec3(minX, maxY, minZ), new Vec3(minX, minY, minZ), new Vec3(0, 0, -1), 0.0f, (float) length, 0.75f, 1.00f, light);
        } else if (Math.abs(to.x - from.x) < 1.0e-6 && Math.abs(to.z - from.z) < 1.0e-6) {
            quad(buffer, matrix, normals, new Vec3(minX, minY, minZ), new Vec3(maxX, minY, minZ), new Vec3(maxX, maxY, minZ), new Vec3(minX, maxY, minZ), new Vec3(0, 0, -1), 0.0f, (float) length, 0.75f, 1.00f, light);
            quad(buffer, matrix, normals, new Vec3(minX, minY, maxZ), new Vec3(maxX, minY, maxZ), new Vec3(maxX, maxY, maxZ), new Vec3(minX, maxY, maxZ), new Vec3(0, 0, 1), 0.0f, (float) length, 0.25f, 0.50f, light);
            quad(buffer, matrix, normals, new Vec3(minX, maxY, minZ), new Vec3(minX, maxY, maxZ), new Vec3(maxX, maxY, maxZ), new Vec3(maxX, maxY, minZ), new Vec3(0, 1, 0), 0.0f, (float) length, 0.00f, 0.25f, light);
            quad(buffer, matrix, normals, new Vec3(maxX, minY, minZ), new Vec3(maxX, minY, maxZ), new Vec3(maxX, maxY, maxZ), new Vec3(maxX, maxY, minZ), new Vec3(1, 0, 0), 0.0f, (float) length, 0.50f, 0.75f, light);
        } else {
            quad(buffer, matrix, normals, new Vec3(minX, minY, minZ), new Vec3(minX, minY, maxZ), new Vec3(maxX, minY, maxZ), new Vec3(maxX, minY, minZ), new Vec3(0, -1, 0), 0.0f, (float) length, 0.50f, 0.75f, light);
            quad(buffer, matrix, normals, new Vec3(minX, maxY, minZ), new Vec3(minX, maxY, maxZ), new Vec3(maxX, maxY, maxZ), new Vec3(maxX, maxY, minZ), new Vec3(0, 1, 0), 0.0f, (float) length, 0.00f, 0.25f, light);
            quad(buffer, matrix, normals, new Vec3(minX, minY, maxZ), new Vec3(maxX, minY, maxZ), new Vec3(maxX, maxY, maxZ), new Vec3(minX, maxY, maxZ), new Vec3(0, 0, 1), 0.0f, (float) length, 0.25f, 0.50f, light);
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 face,
                             float u0, float u1, float v0, float v1, int light) {
        vertex(buffer, matrix, normals, a, u0, v1, face, light);
        vertex(buffer, matrix, normals, b, u1, v1, face, light);
        vertex(buffer, matrix, normals, c, u1, v0, face, light);
        vertex(buffer, matrix, normals, d, u0, v0, face, light);
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
