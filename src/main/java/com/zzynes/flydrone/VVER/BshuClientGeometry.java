package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.state.BoneSnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class BshuClientGeometry {
    private static final List<Triangle> TRIANGLES = new ArrayList<>();
    private static final Map<BshuScreenType, ScreenPlane> SCREEN_PLANES = new EnumMap<>(BshuScreenType.class);
    private static final Map<String, Vec3> PRESS_DIRECTIONS = new HashMap<>();
    private static Object builtModel;
    private static Vec3 modelMin = new Vec3(-7.0, 0.0, -4.0);
    private static Vec3 modelMax = new Vec3(7.0, 6.0, 2.0);

    private BshuClientGeometry() {
    }

    public record Triangle(Vec3 a, Vec3 b, Vec3 c, String boneName, BshuControl control, boolean pair, boolean pairOn, BshuScreenType screen, float[] uvs) {
    }

    public record ScreenPlane(BshuScreenType type, Vec3 topLeft, Vec3 topRight, Vec3 bottomRight, Vec3 bottomLeft, Vec3 origin, Vec3 u, Vec3 v, Vec3 normal, Vec3 center) {
    }

    public record Hit(BlockPos blockPos, String boneName, BshuControl control, boolean pairOn, BshuScreenType screen, Vec3 worldPos, double distance, float textureU, float textureV) {
    }

    public static void ensureBuilt(BakedGeoModel model) {
        if (model == null || builtModel == model) {
            return;
        }
        builtModel = model;
        TRIANGLES.clear();
        SCREEN_PLANES.clear();
        PRESS_DIRECTIONS.clear();
        double[] min = new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] max = new double[]{Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (GeoBone bone : model.topLevelBones()) {
            processBone(bone, new Matrix4f(), min, max);
        }
        if (Double.isFinite(min[0])) {
            modelMin = new Vec3(min[0], min[1], min[2]);
            modelMax = new Vec3(max[0], max[1], max[2]);
        }
    }

    public static boolean isBuilt() {
        return builtModel != null && !TRIANGLES.isEmpty();
    }

    public static Vec3 getModelMin() {
        return modelMin;
    }

    public static Vec3 getModelMax() {
        return modelMax;
    }

    public static Vec3 getPressDirection(String boneName) {
        return PRESS_DIRECTIONS.getOrDefault(boneName, new Vec3(0.0, 0.0, 1.0));
    }

    public static ScreenPlane getScreenPlane(BshuScreenType type) {
        return SCREEN_PLANES.get(type);
    }

    public static Hit raycast(BlockPos blockPos, Direction facing, Vec3 worldOrigin, Vec3 worldDirection, double maxDistance) {
        if (!isBuilt()) {
            return null;
        }
        Matrix4f inverse = blockMatrix(blockPos, facing).invert();
        Vector3f localOrigin = inverse.transformPosition(new Vector3f((float) worldOrigin.x, (float) worldOrigin.y, (float) worldOrigin.z));
        Vector3f localDirection = inverse.transformDirection(new Vector3f((float) worldDirection.x, (float) worldDirection.y, (float) worldDirection.z)).normalize();
        Vec3 origin = new Vec3(localOrigin.x, localOrigin.y, localOrigin.z);
        Vec3 direction = new Vec3(localDirection.x, localDirection.y, localDirection.z);
        Triangle best = null;
        double bestDistance = maxDistance;
        float bestU = 0.0f;
        float bestV = 0.0f;
        for (Triangle triangle : TRIANGLES) {
            double[] hit = intersect(origin, direction, triangle.a(), triangle.b(), triangle.c(), bestDistance);
            if (hit == null) {
                continue;
            }
            best = triangle;
            bestDistance = hit[0];
            if (triangle.uvs() != null) {
                double w0 = 1.0 - hit[1] - hit[2];
                bestU = (float) (w0 * triangle.uvs()[0] + hit[1] * triangle.uvs()[2] + hit[2] * triangle.uvs()[4]);
                bestV = (float) (w0 * triangle.uvs()[1] + hit[1] * triangle.uvs()[3] + hit[2] * triangle.uvs()[5]);
            }
        }
        if (best == null) {
            return null;
        }
        Vec3 worldPos = worldOrigin.add(worldDirection.normalize().scale(bestDistance));
        return new Hit(blockPos, best.boneName(), best.control(), best.pairOn(), best.screen(), worldPos, bestDistance, bestU, bestV);
    }

    public static Vec3 localToWorld(BlockPos blockPos, Direction facing, Vec3 local) {
        Vector3f result = blockMatrix(blockPos, facing).transformPosition(new Vector3f((float) local.x, (float) local.y, (float) local.z));
        return new Vec3(result.x, result.y, result.z);
    }

    public static Vec3 normalToWorld(Direction facing, Vec3 local) {
        Matrix4f matrix = new Matrix4f().rotateY((float) Math.toRadians(facingAngle(facing)));
        Vector3f result = matrix.transformDirection(new Vector3f((float) local.x, (float) local.y, (float) local.z)).normalize();
        return new Vec3(result.x, result.y, result.z);
    }

    public static Matrix4f blockMatrix(BlockPos pos, Direction facing) {
        return new Matrix4f()
                .translation(pos.getX() + 0.5f, pos.getY(), pos.getZ() + 0.5f)
                .rotateY((float) Math.toRadians(facingAngle(facing)));
    }

    public static float facingAngle(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
    }

    private static void processBone(GeoBone bone, Matrix4f parentMatrix, double[] min, double[] max) {
        Matrix4f matrix = boneMatrix(bone, parentMatrix);
        String parentName = bone.getParent() == null ? "" : bone.getParent().getName();
        BshuControl control = "swith".equals(parentName) ? BshuControl.byBone(bone.getName()) : null;
        BshuScreenType screen = "screen".equals(parentName) ? BshuScreenType.byBone(bone.getName()) : null;
        List<GeoCube> cubes = bone.getCubes();
        for (int cubeIndex = 0; cubeIndex < cubes.size(); cubeIndex++) {
            processCube(bone.getName(), cubes.get(cubeIndex), cubeIndex, matrix, control, screen, min, max);
        }
        for (GeoBone child : bone.getChildBones()) {
            processBone(child, matrix, min, max);
        }
    }

    private static Matrix4f boneMatrix(GeoBone bone, Matrix4f parentMatrix) {
        BoneSnapshot snapshot = bone.getInitialSnapshot();
        float posX = snapshot == null ? bone.getPosX() : snapshot.getOffsetX();
        float posY = snapshot == null ? bone.getPosY() : snapshot.getOffsetY();
        float posZ = snapshot == null ? bone.getPosZ() : snapshot.getOffsetZ();
        float rotX = snapshot == null ? bone.getRotX() : snapshot.getRotX();
        float rotY = snapshot == null ? bone.getRotY() : snapshot.getRotY();
        float rotZ = snapshot == null ? bone.getRotZ() : snapshot.getRotZ();
        float scaleX = snapshot == null ? bone.getScaleX() : snapshot.getScaleX();
        float scaleY = snapshot == null ? bone.getScaleY() : snapshot.getScaleY();
        float scaleZ = snapshot == null ? bone.getScaleZ() : snapshot.getScaleZ();
        Matrix4f matrix = new Matrix4f(parentMatrix);
        matrix.translate(-posX / 16.0f, posY / 16.0f, posZ / 16.0f);
        matrix.translate(bone.getPivotX() / 16.0f, bone.getPivotY() / 16.0f, bone.getPivotZ() / 16.0f);
        if (rotZ != 0.0f) {
            matrix.rotateZ(rotZ);
        }
        if (rotY != 0.0f) {
            matrix.rotateY(rotY);
        }
        if (rotX != 0.0f) {
            matrix.rotateX(rotX);
        }
        matrix.scale(scaleX, scaleY, scaleZ);
        matrix.translate(-bone.getPivotX() / 16.0f, -bone.getPivotY() / 16.0f, -bone.getPivotZ() / 16.0f);
        return matrix;
    }

    private static void processCube(String boneName, GeoCube cube, int cubeIndex, Matrix4f boneMatrix, BshuControl control, BshuScreenType screen, double[] min, double[] max) {
        Matrix4f matrix = new Matrix4f(boneMatrix);
        Vec3 pivot = cube.pivot();
        Vec3 rotation = cube.rotation();
        matrix.translate((float) pivot.x / 16.0f, (float) pivot.y / 16.0f, (float) pivot.z / 16.0f);
        matrix.rotateZ((float) rotation.z);
        matrix.rotateY((float) rotation.y);
        matrix.rotateX((float) rotation.x);
        matrix.translate((float) -pivot.x / 16.0f, (float) -pivot.y / 16.0f, (float) -pivot.z / 16.0f);
        if (cube.quads() == null) {
            return;
        }
        boolean pair = control != null && control.kind() == BshuControl.Kind.PAIR;
        boolean pairOn = pair && cube.size().z <= 0.12;
        for (GeoQuad quad : cube.quads()) {
            if (quad == null || quad.vertices() == null || quad.vertices().length < 4) {
                continue;
            }
            if (screen != null && quad.direction() != Direction.NORTH) {
                continue;
            }
            Vec3[] points = new Vec3[4];
            for (int i = 0; i < 4; i++) {
                points[i] = transformVertex(matrix, quad.vertices()[i]);
                includePoint(points[i], min, max);
            }
            if (screen != null && cubeIndex == 0 && quad.direction() == Direction.NORTH) {
                addScreenPlane(screen, points, quad.vertices());
            }
            if (control != null && control.pressable() && cubeIndex == 0 && quad.direction() == Direction.NORTH) {
                Vec3 normal = points[1].subtract(points[0]).cross(points[3].subtract(points[0]));
                if (normal.lengthSqr() > 1.0e-12) {
                    PRESS_DIRECTIONS.put(boneName, normal.normalize().scale(-1.0));
                }
            }
            float[][] normalizedUvs = screen == null ? null : normalizedUvs(quad.vertices());
            float[] firstUvs = normalizedUvs == null ? null : new float[]{
                    normalizedUvs[0][0], normalizedUvs[0][1],
                    normalizedUvs[1][0], normalizedUvs[1][1],
                    normalizedUvs[2][0], normalizedUvs[2][1]
            };
            float[] secondUvs = normalizedUvs == null ? null : new float[]{
                    normalizedUvs[0][0], normalizedUvs[0][1],
                    normalizedUvs[2][0], normalizedUvs[2][1],
                    normalizedUvs[3][0], normalizedUvs[3][1]
            };
            TRIANGLES.add(new Triangle(points[0], points[1], points[2], boneName, control, pair, pairOn, screen, firstUvs));
            TRIANGLES.add(new Triangle(points[0], points[2], points[3], boneName, control, pair, pairOn, screen, secondUvs));
        }
    }

    private static float[][] normalizedUvs(GeoVertex[] vertices) {
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            minU = Math.min(minU, vertices[i].texU());
            maxU = Math.max(maxU, vertices[i].texU());
            minV = Math.min(minV, vertices[i].texV());
            maxV = Math.max(maxV, vertices[i].texV());
        }
        float rangeU = Math.abs(maxU - minU) < 1.0e-8f ? 1.0f : maxU - minU;
        float rangeV = Math.abs(maxV - minV) < 1.0e-8f ? 1.0f : maxV - minV;
        float[][] result = new float[4][2];
        for (int i = 0; i < 4; i++) {
            result[i][0] = (vertices[i].texU() - minU) / rangeU;
            result[i][1] = (vertices[i].texV() - minV) / rangeV;
        }
        return result;
    }

    private static void addScreenPlane(BshuScreenType type, Vec3[] points, GeoVertex[] vertices) {
        float[][] uvs = normalizedUvs(vertices);
        Vec3 topLeft = null;
        Vec3 topRight = null;
        Vec3 bottomRight = null;
        Vec3 bottomLeft = null;
        for (int i = 0; i < 4; i++) {
            boolean left = uvs[i][0] < 0.5f;
            boolean top = uvs[i][1] < 0.5f;
            if (left && top) {
                topLeft = points[i];
            } else if (!left && top) {
                topRight = points[i];
            } else if (!left) {
                bottomRight = points[i];
            } else {
                bottomLeft = points[i];
            }
        }
        if (topLeft == null || topRight == null || bottomRight == null || bottomLeft == null) {
            return;
        }
        Vec3 u = bottomRight.subtract(bottomLeft);
        Vec3 v = topLeft.subtract(bottomLeft);
        Vec3 normal = u.cross(v);
        if (normal.lengthSqr() <= 1.0e-12) {
            return;
        }
        normal = normal.normalize();
        Vec3 center = bottomLeft.add(u.scale(0.5)).add(v.scale(0.5));
        SCREEN_PLANES.put(type, new ScreenPlane(type, topLeft, topRight, bottomRight, bottomLeft, bottomLeft, u, v, normal, center));
    }

    private static Vec3 transformVertex(Matrix4f matrix, GeoVertex vertex) {
        Vector3f position = new Vector3f(vertex.position());
        matrix.transformPosition(position);
        return new Vec3(position.x, position.y, position.z);
    }

    private static void includePoint(Vec3 point, double[] min, double[] max) {
        min[0] = Math.min(min[0], point.x);
        min[1] = Math.min(min[1], point.y);
        min[2] = Math.min(min[2], point.z);
        max[0] = Math.max(max[0], point.x);
        max[1] = Math.max(max[1], point.y);
        max[2] = Math.max(max[2], point.z);
    }

    private static double[] intersect(Vec3 origin, Vec3 direction, Vec3 a, Vec3 b, Vec3 c, double maxDistance) {
        Vec3 edge1 = b.subtract(a);
        Vec3 edge2 = c.subtract(a);
        Vec3 p = direction.cross(edge2);
        double determinant = edge1.dot(p);
        if (Math.abs(determinant) < 1.0e-10) {
            return null;
        }
        double inverseDeterminant = 1.0 / determinant;
        Vec3 t = origin.subtract(a);
        double u = t.dot(p) * inverseDeterminant;
        if (u < -1.0e-7 || u > 1.0 + 1.0e-7) {
            return null;
        }
        Vec3 q = t.cross(edge1);
        double v = direction.dot(q) * inverseDeterminant;
        if (v < -1.0e-7 || u + v > 1.0 + 1.0e-7) {
            return null;
        }
        double distance = edge2.dot(q) * inverseDeterminant;
        if (distance < 0.0 || distance >= maxDistance) {
            return null;
        }
        return new double[]{distance, u, v};
    }
}
