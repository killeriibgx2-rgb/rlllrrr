package com.zzynes.sarcophag.VVER;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class SuzBlockEntityRenderer implements BlockEntityRenderer<SuzBlockEntity> {

    public static final ResourceLocation RODS_MODEL_LOC =
            new ResourceLocation(SarcophagMod.MOD_ID, "block/suz_rods");
    public static final ResourceLocation RODS_LOD_MODEL_LOC =
            new ResourceLocation(SarcophagMod.MOD_ID, "block/suz_rods_lod");

    private static final int VERTEX_STRIDE = 8;
    private static final int FLOATS_PER_VERTEX = 9;
    private static final float BASE_REST_OFFSET_BLOCKS = 0.05f;
    private static final double LOD_DISTANCE_SQR = 18.0 * 18.0;
    private static final double MAX_RENDER_DISTANCE_SQR = 64.0 * 64.0;

    private static volatile BakedModel cachedHiSource;
    private static volatile BakedModel cachedLodSource;
    private static volatile CompiledRodMesh cachedHiMesh;
    private static volatile CompiledRodMesh cachedLodMesh;

    private final int[] columnLights = new int[7];
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private final Vector3f posVec = new Vector3f();
    private final Vector3f normVec = new Vector3f();

    public SuzBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void invalidateMeshCache() {
        cachedHiSource = null;
        cachedLodSource = null;
        cachedHiMesh = null;
        cachedLodMesh = null;
    }

    @Override
    public void render(
            SuzBlockEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos basePos = entity.getBlockPos();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        double distSq = camPos.distanceToSqr(basePos.getX() + 0.5, basePos.getY() + 1.5, basePos.getZ() + 0.5);
        if (distSq > MAX_RENDER_DISTANCE_SQR) {
            return;
        }

        boolean useLod = distSq > LOD_DISTANCE_SQR;
        CompiledRodMesh mesh = getOrCompileMesh(mc, useLod);
        if (mesh == null) {
            return;
        }

        float liftBlocks = entity.getInterpolatedLiftBlocks(partialTick);
        boolean parkedZero = liftBlocks <= 0.001f;
        float[] vertexData = parkedZero ? mesh.parkedData : mesh.fullData;
        if (vertexData.length == 0) {
            return;
        }

        float renderOffsetY = BASE_REST_OFFSET_BLOCKS + liftBlocks;
        fillColumnLights(entity.getLevel(), basePos, packedLight, parkedZero ? 3 : 3 + Mth.ceil(renderOffsetY));
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());

        poseStack.pushPose();
        poseStack.translate(0.0, renderOffsetY, 0.0);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        int maxLightIdx = this.columnLights.length - 1;
        for (int offset = 0, len = vertexData.length; offset < len; offset += FLOATS_PER_VERTEX) {
            float vx = vertexData[offset];
            float vy = vertexData[offset + 1];
            float vz = vertexData[offset + 2];
            float vu = vertexData[offset + 3];
            float vv = vertexData[offset + 4];
            float nx = vertexData[offset + 5];
            float ny = vertexData[offset + 6];
            float nz = vertexData[offset + 7];
            float shade = vertexData[offset + 8];

            int lightBucket = Mth.clamp(Mth.floor(vy + renderOffsetY), 0, maxLightIdx);
            int light = this.columnLights[lightBucket];

            poseMatrix.transformPosition(vx, vy, vz, this.posVec);
            normalMatrix.transform(nx, ny, nz, this.normVec);

            consumer.vertex(
                    this.posVec.x,
                    this.posVec.y,
                    this.posVec.z,
                    shade,
                    shade,
                    shade,
                    1.0f,
                    vu,
                    vv,
                    packedOverlay,
                    light,
                    this.normVec.x,
                    this.normVec.y,
                    this.normVec.z
            );
        }

        poseStack.popPose();
    }

    private static CompiledRodMesh getOrCompileMesh(Minecraft mc, boolean lod) {
        if (lod) {
            BakedModel model = mc.getModelManager().getModel(RODS_LOD_MODEL_LOC);
            if (model != cachedLodSource || cachedLodMesh == null) {
                cachedLodMesh = compileModel(model);
                cachedLodSource = model;
            }
            return cachedLodMesh;
        } else {
            BakedModel model = mc.getModelManager().getModel(RODS_MODEL_LOC);
            if (model != cachedHiSource || cachedHiMesh == null) {
                cachedHiMesh = compileModel(model);
                cachedHiSource = model;
            }
            return cachedHiMesh;
        }
    }

    private static CompiledRodMesh compileModel(BakedModel model) {
        RandomSource rand = RandomSource.create(42L);
        List<BakedQuad> allQuads = new ArrayList<>();
        allQuads.addAll(model.getQuads(null, null, rand, ModelData.EMPTY, RenderType.solid()));
        for (Direction dir : Direction.values()) {
            rand.setSeed(42L);
            allQuads.addAll(model.getQuads(null, dir, rand, ModelData.EMPTY, RenderType.solid()));
        }

        List<BakedQuad> parkedQuads = new ArrayList<>();
        for (BakedQuad quad : allQuads) {
            int[] verts = quad.getVertices();
            if (verts.length < VERTEX_STRIDE * 4) {
                continue;
            }
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (int v = 0; v < 4; v++) {
                float y = Float.intBitsToFloat(verts[v * VERTEX_STRIDE + 1]);
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
            boolean isInternalShaft = (maxY - minY) > 2.0f;
            if (!isInternalShaft) {
                parkedQuads.add(quad);
            }
        }

        return new CompiledRodMesh(packQuads(allQuads), packQuads(parkedQuads));
    }

    private static float[] packQuads(List<BakedQuad> quads) {
        int validCount = 0;
        for (BakedQuad q : quads) {
            if (q.getVertices().length >= VERTEX_STRIDE * 4) {
                validCount++;
            }
        }
        float[] packed = new float[validCount * 4 * FLOATS_PER_VERTEX];
        int ptr = 0;
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            if (vertices.length < VERTEX_STRIDE * 4) {
                continue;
            }
            Vec3i fallback = quad.getDirection().getNormal();
            for (int v = 0; v < 4; v++) {
                int base = v * VERTEX_STRIDE;
                float x = Float.intBitsToFloat(vertices[base]);
                float y = Float.intBitsToFloat(vertices[base + 1]);
                float z = Float.intBitsToFloat(vertices[base + 2]);
                float u = Float.intBitsToFloat(vertices[base + 4]);
                float vt = Float.intBitsToFloat(vertices[base + 5]);

                int packedNormal = vertices[base + 7];
                float nx = ((byte) (packedNormal & 0xFF)) / 127.0f;
                float ny = ((byte) ((packedNormal >> 8) & 0xFF)) / 127.0f;
                float nz = ((byte) ((packedNormal >> 16) & 0xFF)) / 127.0f;
                float lenSq = nx * nx + ny * ny + nz * nz;
                if (lenSq < 0.01f) {
                    nx = fallback.getX();
                    ny = fallback.getY();
                    nz = fallback.getZ();
                } else {
                    float invLen = Mth.invSqrt(lenSq);
                    nx *= invLen;
                    ny *= invLen;
                    nz *= invLen;
                }

                packed[ptr++] = x;
                packed[ptr++] = y;
                packed[ptr++] = z;
                packed[ptr++] = u;
                packed[ptr++] = vt;
                packed[ptr++] = nx;
                packed[ptr++] = ny;
                packed[ptr++] = nz;
                packed[ptr++] = computeMetallicShade(nx, ny, nz);
            }
        }
        return packed;
    }

    public static float computeMetallicShade(float nx, float ny, float nz) {
        float key = Math.max(0.0f, nx * 0.38f + ny * 0.82f + nz * 0.43f);
        float rim = Math.max(0.0f, -nx * 0.56f + ny * 0.28f - nz * 0.78f);
        float sky = 0.5f + 0.5f * ny;
        float spec = key * key * key * 0.16f;
        return Mth.clamp(0.45f + 0.37f * key + 0.11f * rim + 0.07f * sky + spec, 0.42f, 1.0f);
    }

    private void fillColumnLights(Level level, BlockPos basePos, int fallback, int topHeight) {
        if (level == null) {
            for (int i = 0; i < this.columnLights.length; i++) {
                this.columnLights[i] = fallback;
            }
            return;
        }
        int bx = basePos.getX();
        int by = basePos.getY();
        int bz = basePos.getZ();
        int bestBlock = LightTexture.block(fallback);
        int bestSky = LightTexture.sky(fallback);
        int limit = Math.min(topHeight, this.columnLights.length - 1);
        for (int dy = 0; dy < 3; dy++) {
            this.mutablePos.set(bx, by + dy, bz);
            int sampled = LevelRenderer.getLightColor(level, this.mutablePos);
            bestBlock = Math.max(bestBlock, LightTexture.block(sampled));
            bestSky = Math.max(bestSky, LightTexture.sky(sampled));
        }
        int baseLight = LightTexture.pack(bestBlock, bestSky);
        for (int dy = 0; dy < this.columnLights.length; dy++) {
            if (dy < 3 || dy > limit) {
                this.columnLights[dy] = baseLight;
            } else {
                this.mutablePos.set(bx, by + dy, bz);
                int sampled = LevelRenderer.getLightColor(level, this.mutablePos);
                int blk = Math.max(bestBlock, LightTexture.block(sampled));
                int sky = Math.max(bestSky, LightTexture.sky(sampled));
                this.columnLights[dy] = LightTexture.pack(blk, sky);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(SuzBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    private static final class CompiledRodMesh {
        final float[] fullData;
        final float[] parkedData;

        CompiledRodMesh(float[] fullData, float[] parkedData) {
            this.fullData = fullData;
            this.parkedData = parkedData;
        }
    }
}
