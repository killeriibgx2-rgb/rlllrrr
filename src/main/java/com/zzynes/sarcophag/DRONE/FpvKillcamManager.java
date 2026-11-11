package com.zzynes.sarcophag.DRONE;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzynes.sarcophag.DRONE.entity.FpvDroneEntity;
import com.zzynes.sarcophag.DRONE.client.BlastParticlesClient;
import com.zzynes.sarcophag.DRONE.init.ModEntityTypes;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.zzynes.sarcophag.DRONE.entity.RpgProjectileEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = Dist.CLIENT)
public final class FpvKillcamManager {

    // 2 секунды до удара и 2 после, ровно это и крутим в реплее
    public static final int PRE_TICKS = 40;
    public static final int POST_TICKS = 40;
    public static final int TOTAL_TICKS = PRE_TICKS + POST_TICKS;

    // кольцевой буфер истории, 512 тиков это ~25 секунд при 20 tps
    private static final int RING_CAPACITY = 512;
    private static final int RING_MASK = RING_CAPACITY - 1;
    // пишем только то, что рядом, весь мир в реплей не тянем
    private static final double RECORD_RADIUS_SQR = 42.0 * 42.0;
    private static final double PROJECTILE_RECORD_RADIUS_SQR = 52.0 * 52.0;

    private static final WorldTickFrame[] RING = new WorldTickFrame[RING_CAPACITY];
    // первый тик, когда сущность попала в кадр, чтобы не рисовать её раньше времени
    private static final Int2IntOpenHashMap FIRST_SEEN_SEQ = new Int2IntOpenHashMap();
    private static final IntOpenHashSet RENDERED_THIS_FRAME = new IntOpenHashSet();

    private static boolean recordingActive = false;
    @Nullable
    private static ClientLevel recordedLevel = null;
    @Nullable
    private static UUID recordedDroneUuid = null;
    @Nullable
    private static FpvDroneEntity cachedDroneEntity = null;

    private static int sessionStartSeq = 0;
    private static int earliestSeq = 0;
    private static int writeSeq = 0;

    private static boolean killcamActive = false;
    private static int killcamTick = 0;
    private static int firstPreSeq = 0;
    private static int impactSeq = 0;
    private static int impactDroneEntityId = -1;
    private static boolean explosionPlayed = false;
    private static int projectileReplayStartSeq = 0;
    private static int projectileReplayFlightTicks = 0;
    private static double projectileReplaySequence = 0.0;

    private static Vec3 impactPos = Vec3.ZERO;
    private static double startOrbitAngle = 0.0;
    private static float orbitSign = 1.0f;
    // сглаживание отъезда камеры, иначе дёргает на каждом препятствии
    private static double smoothedClipRatio = 1.0;

    private static Vec3 currentCamPos = Vec3.ZERO;
    private static float currentCamYaw = 0.0f;
    private static float currentCamPitch = 20.0f;
    private static float currentCamRoll = 0.0f;

    @Nullable
    // призрак дрона на время реплея, чтобы камера не прилипала к трупу
    private static FpvDroneEntity ghostDrone = null;

    static {
        FIRST_SEEN_SEQ.defaultReturnValue(-1);
    }

    private FpvKillcamManager() {
    }

    public static boolean isActive() {
        return killcamActive;
    }

    public static void beginProjectileReplay(int flightTicks) {
        projectileReplayFlightTicks = Math.max(1, flightTicks);
        int desiredStart = writeSeq - projectileReplayFlightTicks - 1;
        projectileReplayStartSeq = Mth.clamp(desiredStart, earliestSeq, Math.max(earliestSeq, writeSeq - 1));
        projectileReplaySequence = projectileReplayStartSeq;
    }

    public static double getProjectileReplaySequenceStart() {
        return projectileReplayStartSeq;
    }

    public static void setProjectileReplaySequence(double sequence) {
        projectileReplaySequence = Mth.clamp(sequence, earliestSeq, Math.max(earliestSeq, writeSeq - 1));
    }

    public static void renderProjectileReplayEntities(RenderLevelStageEvent event) {
        if (!PipReplayRenderer.isRendering() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double seq = projectileReplaySequence;
        int s0 = Mth.clamp(Mth.floor(seq), earliestSeq, Math.max(earliestSeq, writeSeq - 1));
        int s1 = Mth.clamp(s0 + 1, earliestSeq, Math.max(earliestSeq, writeSeq - 1));
        float frac = (float) Mth.clamp(seq - s0, 0.0, 1.0);
        WorldTickFrame f0 = RING[s0 & RING_MASK];
        WorldTickFrame f1 = RING[s1 & RING_MASK];
        if (f0 == null) return;
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        for (Int2ObjectMap.Entry<EntitySnap> entry : f0.entities.int2ObjectEntrySet()) {
            EntitySnap snap0 = entry.getValue();
            Entity entity = snap0.entityRef;
            if (entity == null || entity.level() != mc.level || entity instanceof FpvDroneEntity) continue;
            EntitySnap snap1 = f1 != null && f1.entities.containsKey(entry.getIntKey()) ? f1.entities.get(entry.getIntKey()) : snap0;
            renderInterpolatedSnap(entity, snap0, snap1, frac, camPos.x, camPos.y, camPos.z, event.getPartialTick(), poseStack, bufferSource, dispatcher);
        }
        bufferSource.endBatch();
    }

    public static Long2ObjectOpenHashMap<BlockState> getProjectileReplayBlocks() {
        if (projectileReplaySequence > projectileReplayStartSeq + projectileReplayFlightTicks) {
            return new Long2ObjectOpenHashMap<>();
        }
        int s0 = Mth.clamp(Mth.floor(projectileReplaySequence), earliestSeq, Math.max(earliestSeq, writeSeq - 1));
        int s1 = Mth.clamp(s0 + 1, earliestSeq, Math.max(earliestSeq, writeSeq - 1));
        WorldTickFrame f0 = RING[s0 & RING_MASK];
        WorldTickFrame f1 = RING[s1 & RING_MASK];
        if (f0 == null || f0.blocks.isEmpty()) {
            for (int s = s0 - 1; s >= Math.max(earliestSeq, s0 - 4); s--) {
                WorldTickFrame previous = RING[s & RING_MASK];
                if (previous != null && !previous.blocks.isEmpty()) {
                    f0 = previous;
                    break;
                }
            }
        }
        if (f0 == null) return new Long2ObjectOpenHashMap<>();
        if (f1 == null || f1.blocks.isEmpty()) return f0.blocks;
        Long2ObjectOpenHashMap<BlockState> merged = new Long2ObjectOpenHashMap<>(f0.blocks);
        merged.putAll(f1.blocks);
        return merged;
    }

    public static Vec3 getCurrentCameraPos() {
        return currentCamPos;
    }

    public static float getCurrentCameraRoll() {
        return currentCamRoll;
    }

    public static float getNormalizedProgress(float partialTick) {
        if (!killcamActive) return 0.0f;
        return Mth.clamp((killcamTick + partialTick) / (float) TOTAL_TICKS, 0.0f, 1.0f);
    }

    public static float getImpactFlashAlpha(float partialTick) {
        if (!killcamActive) return 0.0f;
        float elapsed = killcamTick + partialTick;
        if (elapsed < PRE_TICKS || elapsed > PRE_TICKS + 4.5f) {
            return 0.0f;
        }
        float dt = (elapsed - PRE_TICKS) / 4.5f;
        return 0.24f * (1.0f - dt) * (1.0f - dt);
    }

    public static boolean shouldSuppressEarlyExplosion(double x, double y, double z) {
        if (!killcamActive || explosionPlayed) {
            return false;
        }
        return impactPos.distanceToSqr(x, y, z) <= 36.0;
    }

    public static void triggerKillcam(UUID droneUuid, int droneEntityId, double ix, double iy, double iz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        ClientLevel level = mc.level;
        Vec3 hitPos = new Vec3(ix, iy, iz);

        if (!recordingActive || recordedLevel != level || (recordedDroneUuid != null && !recordedDroneUuid.equals(droneUuid))) {
            resetRecording(level, droneUuid);
        }

        FpvDroneEntity liveDrone = cachedDroneEntity;
        if (liveDrone == null || liveDrone.level() != level) {
            Entity byId = level.getEntity(droneEntityId);
            if (byId instanceof FpvDroneEntity fd) {
                liveDrone = fd;
            }
        }

        Quaternionf finalQuat = liveDrone != null
                ? new Quaternionf(liveDrone.getInterpolatedOrientation(1.0f))
                : getLastRecordedQuat();

        recordFrameInternal(level, hitPos, true, hitPos, finalQuat, droneEntityId);
        impactSeq = writeSeq - 1;
        firstPreSeq = Math.max(earliestSeq, impactSeq - PRE_TICKS);

        if (firstPreSeq == impactSeq) {
            Vec3 backDir = rotateVec(finalQuat, 0.0f, 0.0f, -1.0f);
            Vec3 synthStart = hitPos.add(backDir.scale(5.0)).add(0.0, 1.5, 0.0);
            WorldTickFrame synth = new WorldTickFrame(true, synthStart, new Quaternionf(finalQuat));
            WorldTickFrame hitFrame = RING[impactSeq & RING_MASK];
            if (hitFrame != null) {
                synth.entities.putAll(hitFrame.entities);
            }
            int prevSlot = (impactSeq - 1 + RING_CAPACITY) & RING_MASK;
            if (impactSeq > 0) {
                RING[(impactSeq - 1) & RING_MASK] = synth;
                firstPreSeq = impactSeq - 1;
                earliestSeq = Math.min(earliestSeq, firstPreSeq);
            } else {
                RING[prevSlot] = synth;
            }
        }

        ghostDrone = (liveDrone != null && liveDrone.level() == level)
                ? liveDrone
                : new FpvDroneEntity(ModEntityTypes.FPV_DRONE.get(), level);

        impactPos = hitPos;
        impactDroneEntityId = droneEntityId;
        killcamTick = 0;
        explosionPlayed = false;
        smoothedClipRatio = 1.0;

        setupOrbitGeometry(level, hitPos, finalQuat);

        killcamActive = true;
        computeCameraPose(0.0f);

        DroneInputHandler.enterKillcamFromImpact(mc, droneUuid);
        mc.levelRenderer.needsUpdate();
    }

    private static void setupOrbitGeometry(ClientLevel level, Vec3 hitPos, Quaternionf finalQuat) {
        WorldTickFrame startFrame = RING[firstPreSeq & RING_MASK];
        Vec3 approachVec = startFrame != null && startFrame.droneRecorded
                ? hitPos.subtract(startFrame.dronePos)
                : rotateVec(finalQuat, 0.0f, 0.0f, 1.0f);

        double horizLen = Math.hypot(approachVec.x, approachVec.z);
        double backAzimuth;
        if (horizLen > 0.2) {
            backAzimuth = Math.atan2(-approachVec.z, -approachVec.x);
        } else {
            Vec3 fwd = rotateVec(finalQuat, 0.0f, 0.0f, 1.0f);
            if (Math.hypot(fwd.x, fwd.z) > 0.05) {
                backAzimuth = Math.atan2(-fwd.z, -fwd.x);
            } else {
                backAzimuth = 0.0;
            }
        }

        Vec3 focus = hitPos.add(0.0, 0.55, 0.0);
        double plusClearance = probeOrbitClearance(level, focus, backAzimuth, 1.0f);
        double minusClearance = probeOrbitClearance(level, focus, backAzimuth, -1.0f);
        orbitSign = plusClearance >= minusClearance ? 1.0f : -1.0f;
        startOrbitAngle = backAzimuth - orbitSign * 0.48;
    }

    private static double probeOrbitClearance(ClientLevel level, Vec3 focus, double baseAngle, float sign) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 1.0;
        double total = 0.0;
        double[] steps = new double[]{0.55, 1.35, 2.25, 3.15};
        for (double step : steps) {
            double ang = baseAngle + sign * step;
            Vec3 target = focus.add(Math.cos(ang) * 6.8, 2.4, Math.sin(ang) * 6.8);
            BlockHitResult hit = level.clip(new ClipContext(focus, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                total += 6.8;
            } else {
                total += focus.distanceTo(hit.getLocation());
            }
        }
        return total;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            hardReset();
            return;
        }

        if (killcamActive) {
            if (recordedLevel != mc.level) {
                stopKillcam(mc, false);
                return;
            }

            if (killcamTick <= POST_TICKS + 4) {
                recordFrameInternal(mc.level, impactPos, false, impactPos, new Quaternionf(), impactDroneEntityId);
            }

            if (ghostDrone != null) {
                ghostDrone.tickCount++;
            }

            killcamTick++;

            if (killcamTick >= PRE_TICKS && !explosionPlayed) {
                explosionPlayed = true;
                spawnImpactExplosion(mc.level, impactPos);
            }

            if (killcamTick >= TOTAL_TICKS) {
                stopKillcam(mc, false);
            }
            return;
        }

        if (FpvOverlay.fpvActive && FpvOverlay.drone != null && FpvOverlay.drone.level() == mc.level) {
            FpvDroneEntity drone = FpvOverlay.drone;
            if (!recordingActive || recordedLevel != mc.level || !drone.getUUID().equals(recordedDroneUuid)) {
                resetRecording(mc.level, drone.getUUID());
            }
            cachedDroneEntity = drone;
            recordFrameInternal(
                    mc.level,
                    drone.position(),
                    true,
                    drone.position(),
                    drone.getInterpolatedOrientation(1.0f),
                    drone.getId()
            );
        } else if (!FpvOverlay.fpvActive && recordingActive) {
            recordingActive = false;
        }
    }

    private static void spawnImpactExplosion(ClientLevel level, Vec3 pos) {
        BlastParticlesClient.replayAt(pos.x, pos.y, pos.z);
    }

    public static void stopEarlyByUser(Minecraft mc) {
        if (!killcamActive) return;
        stopKillcam(mc, true);
    }

    private static void stopKillcam(Minecraft mc, boolean userSkipped) {
        killcamActive = false;
        recordingActive = false;
        if (ghostDrone != null) {
            ghostDrone.setKillcamRenderState(false, null);
        }
        ghostDrone = null;
        RENDERED_THIS_FRAME.clear();
        DroneInputHandler.finishKillcamReturn(mc, userSkipped);
    }

    private static void hardReset() {
        killcamActive = false;
        recordingActive = false;
        recordedLevel = null;
        recordedDroneUuid = null;
        cachedDroneEntity = null;
        ghostDrone = null;
        FIRST_SEEN_SEQ.clear();
        RENDERED_THIS_FRAME.clear();
        for (int i = 0; i < RING_CAPACITY; i++) {
            RING[i] = null;
        }
    }

    private static void resetRecording(ClientLevel level, UUID droneUuid) {
        recordingActive = true;
        recordedLevel = level;
        recordedDroneUuid = droneUuid;
        sessionStartSeq = 0;
        earliestSeq = 0;
        writeSeq = 0;
        FIRST_SEEN_SEQ.clear();
        for (int i = 0; i < RING_CAPACITY; i++) {
            RING[i] = null;
        }
    }

    private static void recordFrameInternal(ClientLevel level, Vec3 center, boolean hasDrone, Vec3 dronePos, Quaternionf droneQuat, int skipDroneId) {
        int seq = writeSeq;
        WorldTickFrame frame = new WorldTickFrame(hasDrone, dronePos, new Quaternionf(droneQuat));
        List<Vec3> projectileCenters = new ArrayList<>();
        for (Entity candidate : level.entitiesForRendering()) {
            if (candidate instanceof RpgProjectileEntity && candidate.position().distanceToSqr(center) <= 180.0 * 180.0) {
                projectileCenters.add(candidate.position());
            }
        }

        for (Entity e : level.entitiesForRendering()) {
            if (e.getId() == skipDroneId) {
                continue;
            }
            boolean nearProjectile = false;
            for (Vec3 projectileCenter : projectileCenters) {
                if (e.position().distanceToSqr(projectileCenter) <= PROJECTILE_RECORD_RADIUS_SQR) {
                    nearProjectile = true;
                    break;
                }
            }
            if (e.position().distanceToSqr(center) > RECORD_RADIUS_SQR && !nearProjectile) {
                continue;
            }
            int id = e.getId();
            if (!FIRST_SEEN_SEQ.containsKey(id)) {
                FIRST_SEEN_SEQ.put(id, seq);
            }
            frame.entities.put(id, captureEntity(e));
            if (e instanceof RpgProjectileEntity) {
                BlockPos origin = BlockPos.containing(e.position());
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -4, -4), origin.offset(4, 4, 4))) {
                    frame.blocks.put(pos.asLong(), level.getBlockState(pos));
                }
            }
        }

        RING[seq & RING_MASK] = frame;
        writeSeq = seq + 1;
        if (writeSeq - earliestSeq > RING_CAPACITY - 2) {
            earliestSeq = writeSeq - (RING_CAPACITY - 2);
        }
    }

    private static EntitySnap captureEntity(Entity e) {
        float bodyRot = e.getYRot();
        float headRot = e.getYRot();
        int hurtTime = 0;
        int deathTime = 0;
        if (e instanceof LivingEntity le) {
            bodyRot = le.yBodyRot;
            headRot = le.yHeadRot;
            hurtTime = le.hurtTime;
            deathTime = le.deathTime;
        }
        return new EntitySnap(
                e,
                e.getX(),
                e.getY(),
                e.getZ(),
                e.getYRot(),
                e.getXRot(),
                bodyRot,
                headRot,
                hurtTime,
                deathTime
        );
    }

    private static Quaternionf getLastRecordedQuat() {
        for (int s = writeSeq - 1; s >= earliestSeq; s--) {
            WorldTickFrame f = RING[s & RING_MASK];
            if (f != null && f.droneRecorded) {
                return new Quaternionf(f.droneQuat);
            }
        }
        return new Quaternionf();
    }

    private static double getReplaySeq(float partialTick) {
        double elapsed = Mth.clamp(killcamTick + partialTick, 0.0, (double) TOTAL_TICKS);
        if (elapsed <= PRE_TICKS) {
            double u = elapsed / (double) PRE_TICKS;
            return Mth.lerp(u, (double) firstPreSeq, (double) impactSeq);
        }
        double post = elapsed - PRE_TICKS;
        double maxSeq = Math.max((double) impactSeq, (double) (writeSeq - 1));
        return Math.min((double) impactSeq + post, maxSeq);
    }

    @Nullable
    private static Vec3 sampleGhostDronePos(double replaySeq) {
        int s0 = Mth.clamp(Mth.floor(replaySeq), earliestSeq, impactSeq);
        int s1 = Mth.clamp(s0 + 1, earliestSeq, impactSeq);
        float frac = (float) Mth.clamp(replaySeq - s0, 0.0, 1.0);
        WorldTickFrame f0 = RING[s0 & RING_MASK];
        WorldTickFrame f1 = RING[s1 & RING_MASK];
        if (f0 == null || !f0.droneRecorded) return null;
        if (f1 == null || !f1.droneRecorded) return f0.dronePos;
        return new Vec3(
                Mth.lerp(frac, f0.dronePos.x, f1.dronePos.x),
                Mth.lerp(frac, f0.dronePos.y, f1.dronePos.y),
                Mth.lerp(frac, f0.dronePos.z, f1.dronePos.z)
        );
    }

    private static Quaternionf sampleGhostDroneQuat(double replaySeq) {
        int s0 = Mth.clamp(Mth.floor(replaySeq), earliestSeq, impactSeq);
        int s1 = Mth.clamp(s0 + 1, earliestSeq, impactSeq);
        float frac = (float) Mth.clamp(replaySeq - s0, 0.0, 1.0);
        WorldTickFrame f0 = RING[s0 & RING_MASK];
        WorldTickFrame f1 = RING[s1 & RING_MASK];
        if (f0 == null || !f0.droneRecorded) return new Quaternionf();
        if (f1 == null || !f1.droneRecorded) return new Quaternionf(f0.droneQuat);
        return new Quaternionf(f0.droneQuat).slerp(f1.droneQuat, frac);
    }

    public static CameraPose computeCameraPose(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        float elapsed = Mth.clamp(killcamTick + partialTick, 0.0f, (float) TOTAL_TICKS);
        float t = elapsed / (float) TOTAL_TICKS;

        float smoothT = t * t * (3.0f - 2.0f * t);
        float orbitProgress = 0.68f * t + 0.32f * smoothT;
        double orbitAngle = startOrbitAngle + orbitSign * orbitProgress * 3.95;

        double radius;
        if (t < 0.5f) {
            float u = t / 0.5f;
            float su = u * u * (3.0f - 2.0f * u);
            radius = Mth.lerp(su, 7.4, 5.7);
        } else {
            float u = (t - 0.5f) / 0.5f;
            float su = u * u * (3.0f - 2.0f * u);
            radius = Mth.lerp(su, 5.7, 7.8);
        }

        double heightOffset = Mth.lerp(smoothT, 1.85, 3.45);

        Vec3 baseFocus = impactPos.add(0.0, 0.48, 0.0);
        Vec3 focus = baseFocus;
        double replaySeq = getReplaySeq(partialTick);
        if (elapsed < PRE_TICKS) {
            Vec3 dronePos = sampleGhostDronePos(replaySeq);
            if (dronePos != null) {
                float u = elapsed / (float) PRE_TICKS;
                float leadWeight = 0.52f * (1.0f - u * u);
                focus = new Vec3(
                        Mth.lerp(leadWeight, baseFocus.x, dronePos.x),
                        Mth.lerp(leadWeight, baseFocus.y, dronePos.y + 0.15),
                        Mth.lerp(leadWeight, baseFocus.z, dronePos.z)
                );
            }
        }

        Vec3 idealOffset = new Vec3(
                Math.cos(orbitAngle) * radius,
                heightOffset,
                Math.sin(orbitAngle) * radius
        );
        Vec3 idealPos = baseFocus.add(idealOffset);

        double targetRatio = 1.0;
        if (mc.level != null && mc.player != null) {
            Vec3 rayVec = idealPos.subtract(focus);
            double rayLen = rayVec.length();
            if (rayLen > 0.05) {
                BlockHitResult hit = mc.level.clip(new ClipContext(
                        focus,
                        idealPos,
                        ClipContext.Block.VISUAL,
                        ClipContext.Fluid.NONE,
                        mc.player
                ));
                if (hit.getType() != HitResult.Type.MISS) {
                    double safeDist = Math.max(1.15, focus.distanceTo(hit.getLocation()) - 0.38);
                    targetRatio = Mth.clamp(safeDist / rayLen, 0.15, 1.0);
                }
            }
        }

        if (targetRatio < smoothedClipRatio) {
            smoothedClipRatio = Mth.lerp(0.55, smoothedClipRatio, targetRatio);
        } else {
            smoothedClipRatio = Mth.lerp(0.18, smoothedClipRatio, targetRatio);
        }

        Vec3 camPos = focus.add(idealPos.subtract(focus).scale(smoothedClipRatio));
        Vec3 look = focus.subtract(camPos);
        double horiz = Math.hypot(look.x, look.z);

        float yaw = (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(look.y, Math.max(1.0e-4, horiz))));
        float roll = orbitSign * 2.2f * Mth.sin(t * (float) Math.PI);

        if (elapsed >= PRE_TICKS && elapsed < PRE_TICKS + 13.0f) {
            float dt = (elapsed - PRE_TICKS) / 13.0f;
            float env = (float) Math.exp(-4.2f * dt) * (1.0f - dt);
            yaw += Mth.sin(dt * 41.0f) * 1.9f * env;
            pitch += Mth.cos(dt * 53.0f) * 2.3f * env;
            roll += Mth.sin(dt * 37.0f) * 2.8f * env;
        }

        currentCamPos = camPos;
        currentCamYaw = yaw;
        currentCamPitch = Mth.clamp(pitch, -89.0f, 89.0f);
        currentCamRoll = roll;

        return new CameraPose(currentCamPos, currentCamYaw, currentCamPitch, currentCamRoll);
    }

    public static void onBeginRenderWorld() {
        RENDERED_THIS_FRAME.clear();
    }

    public static void renderLiveEntityInKillcam(
            Entity entity,
            double camX,
            double camY,
            double camZ,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            EntityRenderDispatcher dispatcher
    ) {
        int id = entity.getId();
        RENDERED_THIS_FRAME.add(id);

        if (id == impactDroneEntityId || entity == ghostDrone) {
            return;
        }

        double replaySeq = getReplaySeq(partialTick);
        int firstSeen = FIRST_SEEN_SEQ.get(id);
        if (firstSeen > sessionStartSeq && replaySeq < firstSeen - 0.02) {
            return;
        }

        int s0 = Mth.clamp(Mth.floor(replaySeq), earliestSeq, Math.max(earliestSeq, writeSeq - 1));
        int s1 = Mth.clamp(s0 + 1, earliestSeq, Math.max(earliestSeq, writeSeq - 1));
        float frac = (float) Mth.clamp(replaySeq - s0, 0.0, 1.0);

        WorldTickFrame f0 = RING[s0 & RING_MASK];
        WorldTickFrame f1 = RING[s1 & RING_MASK];

        EntitySnap snap0 = f0 != null ? f0.entities.get(id) : null;
        EntitySnap snap1 = f1 != null ? f1.entities.get(id) : null;

        if (snap0 == null && snap1 == null) {
            double lx = Mth.lerp(partialTick, entity.xOld, entity.getX());
            double ly = Mth.lerp(partialTick, entity.yOld, entity.getY());
            double lz = Mth.lerp(partialTick, entity.zOld, entity.getZ());
            float lyaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            dispatcher.render(
                    entity,
                    lx - camX,
                    ly - camY,
                    lz - camZ,
                    lyaw,
                    partialTick,
                    poseStack,
                    bufferSource,
                    dispatcher.getPackedLightCoords(entity, partialTick)
            );
            return;
        }

        if (snap0 == null) snap0 = snap1;
        if (snap1 == null) snap1 = snap0;

        renderInterpolatedSnap(entity, snap0, snap1, frac, camX, camY, camZ, partialTick, poseStack, bufferSource, dispatcher);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        renderProjectileReplayEntities(event);
        if (!killcamActive || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float partialTick = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        double replaySeq = getReplaySeq(partialTick);
        float elapsed = killcamTick + partialTick;
        boolean drewAny = false;

        if (elapsed < PRE_TICKS && ghostDrone != null) {
            Vec3 dPos = sampleGhostDronePos(replaySeq);
            if (dPos != null) {
                Quaternionf dQuat = sampleGhostDroneQuat(replaySeq);
                Vec3 fwd = rotateVec(dQuat, 0.0f, 0.0f, 1.0f);
                float dYaw = (float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z));
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(dPos.x, dPos.y + 0.25, dPos.z));

                ghostDrone.setKillcamRenderState(true, dQuat);
                try {
                    dispatcher.render(
                            ghostDrone,
                            dPos.x - camPos.x,
                            dPos.y - camPos.y,
                            dPos.z - camPos.z,
                            dYaw,
                            partialTick,
                            poseStack,
                            bufferSource,
                            light
                    );
                    drewAny = true;
                } finally {
                    ghostDrone.setKillcamRenderState(false, null);
                }
            }
        }

        int s0 = Mth.clamp(Mth.floor(replaySeq), earliestSeq, Math.max(earliestSeq, writeSeq - 1));
        int s1 = Mth.clamp(s0 + 1, earliestSeq, Math.max(earliestSeq, writeSeq - 1));
        float frac = (float) Mth.clamp(replaySeq - s0, 0.0, 1.0);

        WorldTickFrame f0 = RING[s0 & RING_MASK];
        WorldTickFrame f1 = RING[s1 & RING_MASK];

        if (f0 != null) {
            for (Int2ObjectMap.Entry<EntitySnap> entry : f0.entities.int2ObjectEntrySet()) {
                int id = entry.getIntKey();
                if (RENDERED_THIS_FRAME.contains(id) || id == impactDroneEntityId) {
                    continue;
                }
                int firstSeen = FIRST_SEEN_SEQ.get(id);
                if (firstSeen > sessionStartSeq && replaySeq < firstSeen - 0.02) {
                    continue;
                }
                EntitySnap snap0 = entry.getValue();
                Entity ref = snap0.entityRef;
                if (ref == null || ref.level() != mc.level || ref == ghostDrone) {
                    continue;
                }
                EntitySnap snap1 = (f1 != null && f1.entities.containsKey(id)) ? f1.entities.get(id) : snap0;
                renderInterpolatedSnap(ref, snap0, snap1, frac, camPos.x, camPos.y, camPos.z, partialTick, poseStack, bufferSource, dispatcher);
                RENDERED_THIS_FRAME.add(id);
                drewAny = true;
            }
        }

        if (drewAny) {
            bufferSource.endBatch();
        }
    }

    private static void renderInterpolatedSnap(
            Entity entity,
            EntitySnap s0,
            EntitySnap s1,
            float frac,
            double camX,
            double camY,
            double camZ,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            EntityRenderDispatcher dispatcher
    ) {
        double rx = Mth.lerp(frac, s0.x, s1.x);
        double ry = Mth.lerp(frac, s0.y, s1.y);
        double rz = Mth.lerp(frac, s0.z, s1.z);
        float ryaw = Mth.rotLerp(frac, s0.yRot, s1.yRot);
        float rpitch = Mth.lerp(frac, s0.xRot, s1.xRot);
        float rbody = Mth.rotLerp(frac, s0.yBodyRot, s1.yBodyRot);
        float rhead = Mth.rotLerp(frac, s0.yHeadRot, s1.yHeadRot);
        int rhurt = Math.round(Mth.lerp(frac, s0.hurtTime, s1.hurtTime));
        int rdeath = Math.round(Mth.lerp(frac, s0.deathTime, s1.deathTime));

        float oldYRot = entity.getYRot();
        float oldYRotO = entity.yRotO;
        float oldXRot = entity.getXRot();
        float oldXRotO = entity.xRotO;

        float oldBody = 0f, oldBodyO = 0f, oldHead = 0f, oldHeadO = 0f;
        int oldHurt = 0, oldDeath = 0;
        LivingEntity living = entity instanceof LivingEntity le ? le : null;
        if (living != null) {
            oldBody = living.yBodyRot;
            oldBodyO = living.yBodyRotO;
            oldHead = living.yHeadRot;
            oldHeadO = living.yHeadRotO;
            oldHurt = living.hurtTime;
            oldDeath = living.deathTime;
        }

        try {
            entity.setYRot(ryaw);
            entity.yRotO = ryaw;
            entity.setXRot(rpitch);
            entity.xRotO = rpitch;
            if (living != null) {
                living.yBodyRot = rbody;
                living.yBodyRotO = rbody;
                living.yHeadRot = rhead;
                living.yHeadRotO = rhead;
                living.hurtTime = rhurt;
                living.deathTime = rdeath;
            }

            int light = entity.isOnFire()
                    ? 15728880
                    : LevelRenderer.getLightColor(entity.level(), BlockPos.containing(rx, ry + entity.getBbHeight() * 0.5, rz));

            dispatcher.render(
                    entity,
                    rx - camX,
                    ry - camY,
                    rz - camZ,
                    ryaw,
                    partialTick,
                    poseStack,
                    bufferSource,
                    light
            );
        } finally {
            entity.setYRot(oldYRot);
            entity.yRotO = oldYRotO;
            entity.setXRot(oldXRot);
            entity.xRotO = oldXRotO;
            if (living != null) {
                living.yBodyRot = oldBody;
                living.yBodyRotO = oldBodyO;
                living.yHeadRot = oldHead;
                living.yHeadRotO = oldHeadO;
                living.hurtTime = oldHurt;
                living.deathTime = oldDeath;
            }
        }
    }

    private static Vec3 rotateVec(Quaternionf q, float x, float y, float z) {
        float qx = q.x, qy = q.y, qz = q.z, qw = q.w;
        float tx = 2.0f * (qy * z - qz * y);
        float ty = 2.0f * (qz * x - qx * z);
        float tz = 2.0f * (qx * y - qy * x);
        float rx = x + qw * tx + (qy * tz - qz * ty);
        float ry = y + qw * ty + (qz * tx - qx * tz);
        float rz = z + qw * tz + (qx * ty - qy * tx);
        return new Vec3(rx, ry, rz);
    }

    public record CameraPose(Vec3 pos, float yaw, float pitch, float roll) {
    }

    private static final class WorldTickFrame {
        final boolean droneRecorded;
        final Vec3 dronePos;
        final Quaternionf droneQuat;
        final Int2ObjectOpenHashMap<EntitySnap> entities = new Int2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();

        WorldTickFrame(boolean droneRecorded, Vec3 dronePos, Quaternionf droneQuat) {
            this.droneRecorded = droneRecorded;
            this.dronePos = dronePos;
            this.droneQuat = droneQuat;
        }
    }

    private record EntitySnap(
            Entity entityRef,
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            float yBodyRot,
            float yHeadRot,
            int hurtTime,
            int deathTime
    ) {
    }
}
