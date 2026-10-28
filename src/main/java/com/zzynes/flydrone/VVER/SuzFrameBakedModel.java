package com.zzynes.flydrone.VVER;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SuzFrameBakedModel extends BakedModelWrapper<BakedModel> {

    private static final int VERTEX_STRIDE = 8;
    private static final ChunkRenderTypeSet SOLID_SET = ChunkRenderTypeSet.of(RenderType.solid());

    private final List<BakedQuad> unculledQuads;
    private final Map<Direction, List<BakedQuad>> culledQuads;

    public SuzFrameBakedModel(BakedModel originalModel) {
        super(originalModel);
        RandomSource rand = RandomSource.create(42L);
        this.unculledQuads = bakeShadedList(originalModel.getQuads(null, null, rand, ModelData.EMPTY, RenderType.solid()));
        this.culledQuads = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            rand.setSeed(42L);
            this.culledQuads.put(dir, bakeShadedList(originalModel.getQuads(null, dir, rand, ModelData.EMPTY, RenderType.solid())));
        }
    }

    private static List<BakedQuad> bakeShadedList(List<BakedQuad> source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        List<BakedQuad> result = new ArrayList<>(source.size());
        for (BakedQuad quad : source) {
            int[] srcVerts = quad.getVertices();
            if (srcVerts.length < VERTEX_STRIDE * 4) {
                result.add(quad);
                continue;
            }
            int[] dstVerts = srcVerts.clone();
            Vec3i fallback = quad.getDirection().getNormal();
            for (int v = 0; v < 4; v++) {
                int base = v * VERTEX_STRIDE;
                int packedNormal = dstVerts[base + 7];
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
                float shade = SuzBlockEntityRenderer.computeMetallicShade(nx, ny, nz);
                int c = Mth.clamp(Math.round(shade * 255.0f), 0, 255);
                dstVerts[base + 3] = 0xFF000000 | (c << 16) | (c << 8) | c;
            }
            result.add(new BakedQuad(dstVerts, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), false));
        }
        return result;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state) {
        return false;
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
        return false;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return side == null ? this.unculledQuads : this.culledQuads.getOrDefault(side, Collections.emptyList());
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource rand,
            ModelData extraData,
            @Nullable RenderType renderType
    ) {
        if (renderType != null && renderType != RenderType.solid()) {
            return Collections.emptyList();
        }
        return side == null ? this.unculledQuads : this.culledQuads.getOrDefault(side, Collections.emptyList());
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return SOLID_SET;
    }
}
