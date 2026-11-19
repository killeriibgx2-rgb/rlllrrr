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

// Жила рисуется как замкнутая квадратная трубка: кубик сечения плюс полоски до граней клетки.
// Грани строятся от габарита, поэтому ни одна сторона не выпадает и трубка не просвечивает.
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
            tube(buffer, matrix, normals, centre.add(-half, -half, -half), centre.add(half, half, half), 0.5f, light);
            for (Direction direction : Direction.values()) {
                if (!strand.connects(direction)) {
                    continue;
                }
                Vec3 end = new Vec3(
                        direction.getStepX() > 0 ? 1.0 : direction.getStepX() < 0 ? 0.0 : centre.x,
                        direction.getStepY() > 0 ? 1.0 : direction.getStepY() < 0 ? 0.0 : centre.y,
                        direction.getStepZ() > 0 ? 1.0 : direction.getStepZ() < 0 ? 0.0 : centre.z);
                Vec3 min = new Vec3(Math.min(centre.x, end.x), Math.min(centre.y, end.y), Math.min(centre.z, end.z));
                Vec3 max = new Vec3(Math.max(centre.x, end.x), Math.max(centre.y, end.y), Math.max(centre.z, end.z));
                min = min.add(-half, -half, -half);
                max = max.add(half, half, half);
                if (direction.getAxis() == Direction.Axis.X) {
                    min = new Vec3(min.x + half, min.y, min.z);
                    max = new Vec3(max.x - half, max.y, max.z);
                } else if (direction.getAxis() == Direction.Axis.Y) {
                    min = new Vec3(min.x, min.y + half, min.z);
                    max = new Vec3(max.x, max.y - half, max.z);
                } else {
                    min = new Vec3(min.x, min.y, min.z + half);
                    max = new Vec3(max.x, max.y, max.z - half);
                }
                tube(buffer, matrix, normals, min, max, (float) centre.distanceTo(end), light);
            }
        }
    }

    // Четыре боковые грани по габариту. Ось трубки определяем по размеру.
    private static void tube(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 min, Vec3 max, float length, int light) {
        double dx = max.x - min.x;
        double dy = max.y - min.y;
        double dz = max.z - min.z;
        float u = Math.max(length, 0.05f);
        if (dx >= dy && dx >= dz) {
            quad(buffer, matrix, normals, min, max, Direction.UP, u, light);
            quad(buffer, matrix, normals, min, max, Direction.DOWN, u, light);
            quad(buffer, matrix, normals, min, max, Direction.SOUTH, u, light);
            quad(buffer, matrix, normals, min, max, Direction.NORTH, u, light);
        } else if (dy >= dz) {
            quad(buffer, matrix, normals, min, max, Direction.EAST, u, light);
            quad(buffer, matrix, normals, min, max, Direction.WEST, u, light);
            quad(buffer, matrix, normals, min, max, Direction.SOUTH, u, light);
            quad(buffer, matrix, normals, min, max, Direction.NORTH, u, light);
        } else {
            quad(buffer, matrix, normals, min, max, Direction.EAST, u, light);
            quad(buffer, matrix, normals, min, max, Direction.WEST, u, light);
            quad(buffer, matrix, normals, min, max, Direction.UP, u, light);
            quad(buffer, matrix, normals, min, max, Direction.DOWN, u, light);
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 min, Vec3 max, Direction face, float u, int light) {
        float x0 = (float) min.x;
        float y0 = (float) min.y;
        float z0 = (float) min.z;
        float x1 = (float) max.x;
        float y1 = (float) max.y;
        float z1 = (float) max.z;
        float v0;
        float v1;
        Vec3 a;
        Vec3 b;
        Vec3 c;
        Vec3 d;
        switch (face) {
            case UP -> {
                v0 = 0.00f;
                v1 = 0.25f;
                a = new Vec3(x0, y1, z0);
                b = new Vec3(x0, y1, z1);
                c = new Vec3(x1, y1, z1);
                d = new Vec3(x1, y1, z0);
            }
            case DOWN -> {
                v0 = 0.50f;
                v1 = 0.75f;
                a = new Vec3(x0, y0, z0);
                b = new Vec3(x0, y0, z1);
                c = new Vec3(x1, y0, z1);
                d = new Vec3(x1, y0, z0);
            }
            case EAST -> {
                v0 = 0.25f;
                v1 = 0.50f;
                a = new Vec3(x1, y0, z0);
                b = new Vec3(x1, y0, z1);
                c = new Vec3(x1, y1, z1);
                d = new Vec3(x1, y1, z0);
            }
            case WEST -> {
                v0 = 0.75f;
                v1 = 1.00f;
                a = new Vec3(x0, y0, z1);
                b = new Vec3(x0, y0, z0);
                c = new Vec3(x0, y1, z0);
                d = new Vec3(x0, y1, z1);
            }
            case SOUTH -> {
                v0 = 0.25f;
                v1 = 0.50f;
                a = new Vec3(x1, y0, z1);
                b = new Vec3(x0, y0, z1);
                c = new Vec3(x0, y1, z1);
                d = new Vec3(x1, y1, z1);
            }
            default -> {
                v0 = 0.75f;
                v1 = 1.00f;
                a = new Vec3(x0, y0, z0);
                b = new Vec3(x1, y0, z0);
                c = new Vec3(x1, y1, z0);
                d = new Vec3(x0, y1, z0);
            }
        }
        vertex(buffer, matrix, normals, a, 0.0f, v1, face, light);
        vertex(buffer, matrix, normals, b, u, v1, face, light);
        vertex(buffer, matrix, normals, c, u, v0, face, light);
        vertex(buffer, matrix, normals, d, 0.0f, v0, face, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vec3 point, float u, float v, Direction face, int light) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(1.0f, 1.0f, 1.0f, 1.0f)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normals, face.getStepX(), face.getStepY(), face.getStepZ())
                .endVertex();
    }
}
