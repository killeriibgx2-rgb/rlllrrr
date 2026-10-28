package com.zzynes.flydrone.DRONE.client;

import com.zzynes.flydrone.DRONE.BlastParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "fly_drone", value = Dist.CLIENT)
public final class BlastParticlesClient {

    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static long lastFxTick = Long.MIN_VALUE;
    private static float lastRadius = 4.0f;
    private static double lastNormalX;
    private static double lastNormalY = 1.0;
    private static double lastNormalZ;
    private static boolean lastMushroom;

    private BlastParticlesClient() {
    }

    public static void receive(double x, double y, double z, float radius, double nx, double ny, double nz, boolean mushroom) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        lastFxTick = level.getGameTime();
        lastRadius = radius;
        lastNormalX = nx;
        lastNormalY = ny;
        lastNormalZ = nz;
        lastMushroom = mushroom;
        Vec3 normal = new Vec3(nx, ny, nz);
        if (normal.lengthSqr() < 1.0e-5) {
            normal = new Vec3(0.0, 1.0, 0.0);
        } else {
            normal = normal.normalize();
        }
        RandomSource random = RandomSource.create(level.getGameTime() ^ Double.doubleToLongBits(x + y + z));
        spawn(level, BlastParticleTypes.BLAST_CORE.get(), x, y, z, 0.05, 0.0, 0.0);
        spawn(level, BlastParticleTypes.BLAST_CORE.get(), x, y, z, 0.35, 0.0, 0.0);
        spawn(level, BlastParticleTypes.BLAST_RING.get(), x, y + 0.2, z, 0.65, 0.0, 0.0);
        spawn(level, BlastParticleTypes.BLAST_RING.get(), x, y + 0.2, z, 1.15, 0.0, 0.0);

        if (mushroom) {
            spawnMushroom(level, x, y, z, normal, radius, random);
        } else {
            spawnSphere(level, x, y, z, radius, random);
        }

        float volume = Math.min(5.0f, 1.2f + radius * 0.32f);
        float pitch = 0.72f + random.nextFloat() * 0.16f;
        level.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, volume, pitch, false);
    }

    public static void replayAt(double x, double y, double z) {
        double replayRadius = x == lastX && y == lastY && z == lastZ ? lastRadius : 4.0f;
        double nx = x == lastX && y == lastY && z == lastZ ? lastNormalX : 0.0;
        double ny = x == lastX && y == lastY && z == lastZ ? lastNormalY : 1.0;
        double nz = x == lastX && y == lastY && z == lastZ ? lastNormalZ : 0.0;
        boolean mushroom = x == lastX && y == lastY && z == lastZ && lastMushroom;
        receive(x, y, z, (float) replayRadius, nx, ny, nz, mushroom);
    }

    private static void spawnSphere(ClientLevel level, double x, double y, double z, float radius, RandomSource random) {
        int sparks = 72 + Math.round(radius * 10.0f);
        for (int i = 0; i < sparks; i++) {
            Vec3 dir = randomUnit(random);
            double speed = 0.08 + random.nextDouble() * (0.28 + radius * 0.045);
            double px = x + dir.x * random.nextDouble() * 0.85;
            double py = y + dir.y * random.nextDouble() * 0.65;
            double pz = z + dir.z * random.nextDouble() * 0.85;
            spawn(level, BlastParticleTypes.BLAST_SPARK.get(), px, py, pz, dir.x * speed, dir.y * speed, dir.z * speed);
            if (i % 3 == 0) {
                spawn(level, BlastParticleTypes.BLAST_FLARE.get(), px, py, pz, dir.x * speed * 0.45, dir.y * speed * 0.45, dir.z * speed * 0.45);
            }
        }
        int clouds = 52 + Math.round(radius * 4.0f);
        for (int i = 0; i < clouds; i++) {
            Vec3 dir = randomUnit(random);
            double spread = 0.25 + random.nextDouble() * radius * 0.17;
            double speed = 0.012 + random.nextDouble() * 0.045;
            spawn(level, BlastParticleTypes.BLAST_SMOKE.get(), x + dir.x * spread, y + dir.y * spread * 0.72, z + dir.z * spread,
                    dir.x * speed, Math.abs(dir.y) * speed + 0.012, dir.z * speed);
        }
    }

    private static void spawnMushroom(ClientLevel level, double x, double y, double z, Vec3 normal, float radius, RandomSource random) {
        Vec3 reference = Math.abs(normal.y) < 0.92 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 axisA = normal.cross(reference).normalize();
        Vec3 axisB = normal.cross(axisA).normalize();
        for (int step = 1; step <= 7; step++) {
            double spread = 0.12 + step * 0.055;
            Vec3 center = new Vec3(x, y, z).add(normal.scale(step));
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * 2.0 * i / 10.0 + random.nextDouble() * 0.18;
                double r = spread * (0.45 + random.nextDouble() * 0.8);
                Vec3 p = center.add(axisA.scale(Math.cos(angle) * r)).add(axisB.scale(Math.sin(angle) * r));
                spawn(level, BlastParticleTypes.BLAST_SMOKE.get(), p.x, p.y, p.z,
                        normal.x * 0.055 + axisA.x * r * 0.08,
                        normal.y * 0.055 + axisA.y * r * 0.08,
                        normal.z * 0.055 + axisA.z * r * 0.08);
                if (step <= 2 && i % 2 == 0) {
                    spawn(level, BlastParticleTypes.BLAST_FLARE.get(), p.x, p.y, p.z, normal.x * 0.12, normal.y * 0.12, normal.z * 0.12);
                }
            }
        }
        Vec3 cap = new Vec3(x, y, z).add(normal.scale(7.0));
        spawn(level, BlastParticleTypes.BLAST_CORE.get(), cap.x, cap.y, cap.z, 0.8, 0.0, 0.0);
        spawn(level, BlastParticleTypes.BLAST_RING.get(), cap.x, cap.y, cap.z, 1.6, 0.0, 0.0);
        for (int i = 0; i < 48; i++) {
            double angle = Math.PI * 2.0 * i / 48.0;
            double r = 0.55 + random.nextDouble() * 1.0;
            Vec3 p = cap.add(axisA.scale(Math.cos(angle) * r)).add(axisB.scale(Math.sin(angle) * r));
            spawn(level, BlastParticleTypes.BLAST_SMOKE.get(), p.x, p.y, p.z,
                    axisA.x * Math.cos(angle) * 0.055 + normal.x * 0.025,
                    axisA.y * Math.cos(angle) * 0.055 + normal.y * 0.025,
                    axisA.z * Math.cos(angle) * 0.055 + normal.z * 0.025);
            if (i % 3 == 0) {
                spawn(level, BlastParticleTypes.BLAST_SPARK.get(), p.x, p.y, p.z,
                        axisA.x * Math.cos(angle) * 0.12,
                        axisA.y * Math.cos(angle) * 0.12,
                        axisA.z * Math.cos(angle) * 0.12);
            }
        }
    }

    private static Vec3 randomUnit(RandomSource random) {
        double y = random.nextDouble() * 2.0 - 1.0;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        return new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);
    }

    private static void spawn(ClientLevel level, SimpleParticleType type, double x, double y, double z, double vx, double vy, double vz) {
        level.addParticle(type, x, y, z, vx, vy, vz);
    }

    public static boolean shouldSuppressVanillaAt(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || lastFxTick == Long.MIN_VALUE || level.getGameTime() - lastFxTick > 8L) {
            return false;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        return dx * dx + dy * dy + dz * dz < 4.0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level == null) {
            lastFxTick = Long.MIN_VALUE;
        }
    }
}
