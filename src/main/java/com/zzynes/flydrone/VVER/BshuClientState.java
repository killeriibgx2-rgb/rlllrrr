package com.zzynes.flydrone.VVER;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class BshuClientState {
    private static final Map<String, Long> PRESS_STARTS = new HashMap<>();
    private static final Set<BlockPos> KNOWN_BSHUS = new HashSet<>();
    private static long lastScanTick = -100L;
    private static BlockPos focusPos;
    private static BshuScreenType focusScreen;
    private static int focusField;
    private static String focusText = "";
    private static long focusVersion;
    private static BlockPos coreAssignBshuPos;
    private static BlockPos coreAssignSuzPos;

    private BshuClientState() {
    }

    public static void startPress(BlockPos pos, BshuControl control) {
        if (pos == null || control == null || !control.pressable()) {
            return;
        }
        String key = pos.asLong() + ":" + control.ordinal();
        long now = System.nanoTime();
        Long existing = PRESS_STARTS.get(key);
        if (existing == null || now - existing > 120_000_000L) {
            PRESS_STARTS.put(key, now);
        }
    }

    public static float getPressAmount(BlockPos pos, BshuControl control) {
        String key = pos.asLong() + ":" + control.ordinal();
        Long start = PRESS_STARTS.get(key);
        if (start == null) {
            return 0.0f;
        }
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        if (elapsedMs < 0.0) {
            return 0.0f;
        }
        if (elapsedMs >= 380.0) {
            PRESS_STARTS.remove(key);
            return 0.0f;
        }
        if (elapsedMs < 130.0) {
            return smooth((float) (elapsedMs / 130.0));
        }
        if (elapsedMs < 230.0) {
            return 1.0f;
        }
        return 1.0f - smooth((float) ((elapsedMs - 230.0) / 150.0));
    }

    private static float smooth(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    public static void setFocus(BlockPos pos, BshuScreenType screen, int field, String text) {
        focusPos = pos;
        focusScreen = screen;
        focusField = field;
        focusText = text == null ? "" : text;
        focusVersion++;
    }

    public static void updateFocusText(String text) {
        focusText = text == null ? "" : text;
        focusVersion++;
    }

    public static void updateFocusField(int field, String text) {
        focusField = field;
        focusText = text == null ? "" : text;
        focusVersion++;
    }

    public static void clearFocus(BlockPos pos) {
        if (focusPos != null && focusPos.equals(pos)) {
            focusPos = null;
            focusScreen = null;
            focusText = "";
            focusVersion++;
        }
    }

    public static boolean isFocused(BlockPos pos, BshuScreenType screen) {
        return focusPos != null && focusPos.equals(pos) && focusScreen == screen;
    }

    public static int getFocusField() {
        return focusField;
    }

    public static String getFocusText() {
        return focusText;
    }

    public static long getFocusVersion() {
        return focusVersion;
    }

    public static void setCoreAssignment(BlockPos bshuPos, BlockPos suzPos) {
        coreAssignBshuPos = bshuPos;
        coreAssignSuzPos = suzPos;
        focusVersion++;
    }

    public static void clearCoreAssignment(BlockPos bshuPos) {
        if (coreAssignBshuPos != null && coreAssignBshuPos.equals(bshuPos)) {
            coreAssignBshuPos = null;
            coreAssignSuzPos = null;
            focusVersion++;
        }
    }

    public static BlockPos getCoreAssignSuzPos(BlockPos bshuPos) {
        return coreAssignBshuPos != null && coreAssignBshuPos.equals(bshuPos) ? coreAssignSuzPos : null;
    }

    public static BshuClientGeometry.Hit findLookHit(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return null;
        }
        refreshKnownBshus(level, player);
        Vec3 origin = player.getEyePosition(minecraft.getFrameTime());
        Vec3 direction = player.getViewVector(minecraft.getFrameTime()).normalize();
        return raycastKnown(level, origin, direction, 14.0);
    }

    public static BshuClientGeometry.Hit raycastKnown(ClientLevel level, Vec3 origin, Vec3 direction, double maxDistance) {
        BshuClientGeometry.Hit best = null;
        for (BlockPos pos : Set.copyOf(KNOWN_BSHUS)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof BshuBlockEntity bshu)) {
                KNOWN_BSHUS.remove(pos);
                continue;
            }
            Direction facing = bshu.getBlockState().getValue(BshuBlock.FACING);
            BshuClientGeometry.Hit hit = BshuClientGeometry.raycast(pos, facing, origin, direction, maxDistance);
            if (hit != null && (best == null || hit.distance() < best.distance())) {
                best = hit;
            }
        }
        return best;
    }

    public static void refreshKnownBshus(ClientLevel level, LocalPlayer player) {
        long now = level.getGameTime();
        if (now - lastScanTick < 20L) {
            return;
        }
        lastScanTick = now;
        ChunkPos center = player.chunkPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!level.getChunkSource().hasChunk(center.x + dx, center.z + dz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(center.x + dx, center.z + dz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof BshuBlockEntity) {
                        KNOWN_BSHUS.add(be.getBlockPos());
                    }
                }
            }
        }
        KNOWN_BSHUS.removeIf(pos -> player.position().distanceToSqr(Vec3.atCenterOf(pos)) > 3600.0);
    }
}
