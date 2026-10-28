package com.zzynes.flydrone.DRONE;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = FlyDroneMod.MOD_ID, value = Dist.CLIENT)
public final class ProjectileImpactPreview {

    private static List<Vec3> path = List.of();
    private static int tick;
    private static int flightTicks;
    private static int lifeTicks;
    private static boolean active;

    private ProjectileImpactPreview() {
    }

    public static void start(List<Vec3> points) {
        if (points == null || points.size() < 2) return;
        path = List.copyOf(points);
        tick = 0;
        flightTicks = Math.max(1, path.size() - 1);
        lifeTicks = flightTicks + 40;
        active = true;
        FpvKillcamManager.beginProjectileReplay(flightTicks);
    }

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;
        tick++;
        if (tick > lifeTicks) {
            active = false;
            path = List.of();
        }
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        if (!active || path.size() < 2) return;
        int width = Math.min(360, graphics.guiWidth() - 24);
        if (width < 180) return;
        int videoHeight = Math.round(width * 9.0f / 16.0f);
        int x = 12;
        int y = 12;
        int outerHeight = videoHeight + 48;
        if (y + outerHeight > graphics.guiHeight() - 8) {
            videoHeight = Math.max(88, graphics.guiHeight() - y - 56);
            width = Math.round(videoHeight * 16.0f / 9.0f);
            outerHeight = videoHeight + 48;
        }
        float elapsed = Mth.clamp(tick + partialTick, 0.0f, (float) lifeTicks);
        float progress = elapsed / lifeTicks;
        updateReplayCamera(elapsed);

        graphics.fill(x - 3, y - 3, x + width + 3, y + outerHeight + 3, 0xD9000000);
        graphics.fill(x, y, x + width, y + outerHeight, 0xFF0B1116);
        graphics.fill(x, y, x + width, y + 22, 0xFF14232A);
        graphics.fill(x, y, x + width, y + 2, 0xFF63D8EE);
        graphics.drawString(Minecraft.getInstance().font, "3D REPLAY  /  RPG", x + 9, y + 7, 0xFFE8F7FA, false);
        int recX = x + width - 38;
        graphics.fill(recX, y + 9, recX + 5, y + 14, 0xFFFF5148);
        graphics.drawString(Minecraft.getInstance().font, "REC", recX + 8, y + 7, 0xFFFFB5AC, false);

        int videoY = y + 23;
        graphics.fill(x + 1, videoY, x + width - 1, videoY + videoHeight, 0xFF020508);
        PipReplayRenderer.drawReplay(graphics, x + 1, videoY, width - 2, videoHeight);
        graphics.fill(x + 1, videoY, x + width - 1, videoY + 1, 0xFF5A8792);
        graphics.fill(x + 1, videoY + videoHeight - 1, x + width - 1, videoY + videoHeight, 0xFF5A8792);
        graphics.fill(x + 1, videoY, x + 2, videoY + videoHeight, 0xFF5A8792);
        graphics.fill(x + width - 2, videoY, x + width - 1, videoY + videoHeight, 0xFF5A8792);

        int footerY = videoY + videoHeight + 3;
        String state = elapsed < flightTicks ? "ПОЛЁТ" : "ПОПАДАНИЕ  +" + String.format(Locale.ROOT, "%.1f", Math.min(2.0f, (elapsed - flightTicks) / 20.0f)) + " С";
        graphics.drawString(Minecraft.getInstance().font, state, x + 8, footerY + 2, 0xFFE7F2F4, false);
        String clock = String.format(Locale.ROOT, "%02d:%02d", (int) elapsed / 20, (int) elapsed % 20);
        graphics.drawString(Minecraft.getInstance().font, clock, x + width - Minecraft.getInstance().font.width(clock) - 8, footerY + 2, 0xFF9AC0C8, false);
        int barY = y + outerHeight - 4;
        graphics.fill(x + 8, barY, x + width - 8, barY + 2, 0xFF29373C);
        graphics.fill(x + 8, barY, x + 8 + Math.round((width - 16) * progress), barY + 2, 0xFF5CE1F3);
        graphics.fill(x, y + outerHeight - 1, x + width, y + outerHeight, 0xFF63D8EE);
    }

    private static void updateReplayCamera(float elapsed) {
        int index = Mth.clamp((int) Math.floor(Math.min(elapsed, flightTicks)), 0, path.size() - 1);
        Vec3 projectile = path.get(index);
        Vec3 tangent;
        if (index < path.size() - 1) {
            tangent = path.get(index + 1).subtract(projectile).normalize();
        } else if (index > 0) {
            tangent = projectile.subtract(path.get(index - 1)).normalize();
        } else {
            tangent = new Vec3(0.0, 0.0, 1.0);
        }
        if (tangent.lengthSqr() < 1.0e-6) tangent = new Vec3(0.0, 0.0, 1.0);

        Vec3 camera;
        Vec3 lookAt;
        if (elapsed <= flightTicks) {
            camera = projectile.subtract(tangent.scale(6.5)).add(0.0, 2.0, 0.0);
            lookAt = projectile.add(tangent.scale(2.0)).add(0.0, 0.3, 0.0);
        } else {
            float post = Mth.clamp((elapsed - flightTicks) / 40.0f, 0.0f, 1.0f);
            Vec3 impact = path.get(path.size() - 1);
            double base = Math.atan2(tangent.z, tangent.x);
            double angle = base + post * Math.PI * 0.72;
            camera = impact.add(Math.cos(angle) * 8.0, 3.0 + Mth.sin(post * (float) Math.PI) * 0.7, Math.sin(angle) * 8.0);
            lookAt = impact.add(0.0, 0.55, 0.0);
        }

        Vec3 view = lookAt.subtract(camera).normalize();
        float yaw = (float) (Math.toDegrees(Math.atan2(-view.x, view.z)));
        float pitch = (float) (-Math.toDegrees(Math.asin(Mth.clamp(view.y, -1.0, 1.0))));
        double replaySeq = FpvKillcamManager.getProjectileReplaySequenceStart() + elapsed;
        FpvKillcamManager.setProjectileReplaySequence(replaySeq);
        PipReplayRenderer.setCameraPose(camera, yaw, pitch, replaySeq);
    }
}
