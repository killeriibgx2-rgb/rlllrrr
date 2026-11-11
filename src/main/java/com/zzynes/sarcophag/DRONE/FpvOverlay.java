package com.zzynes.sarcophag.DRONE;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zzynes.sarcophag.DRONE.entity.FpvDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = Dist.CLIENT)
public class FpvOverlay {

    public static final ResourceLocation OVERLAY = new ResourceLocation("sarcophag", "textures/gui/overlay_fpv.png");
    public static final ResourceLocation CONNECTION_LOST = new ResourceLocation("sarcophag", "textures/gui/connection_lost.png");

    private static final int TEX_W = 1672;
    private static final int TEX_H = 941;

    private static final float COMPASS_CENTER_X = 836.5f;
    private static final float COMPASS_PX_PER_DEG = 112.0f / 15.0f;

    private static final float ALT_SCALE_CENTER_Y = 408.0f;
    private static final float ALT_PX_PER_METER = 113.5f / 10.0f;

    private static final float PITCH_SCALE_CENTER_Y = 459.0f;
    private static final float PITCH_PX_PER_DEG = 88.0f / 15.0f;

    public static boolean fpvActive = false;
    public static int connectionLostTimer = 0;
    public static float distance = 0;
    public static FpvDroneEntity drone = null;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (fpvActive || FpvKillcamManager.isActive()) {
            event.setCanceled(true);
        }
    }

    public static void render(GuiGraphics g) {
        render(g, 1.0f);
    }

    public static void render(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ProjectileImpactPreview.render(g, partialTick);
        int gw = g.guiWidth();
        int gh = g.guiHeight();

        if (FpvKillcamManager.isActive()) {
            renderKillcamLetterbox(g, gw, gh, partialTick);
            return;
        }

        if (connectionLostTimer > 0) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(CONNECTION_LOST, 0, 0, gw, gh, 0, 0, 512, 512, 512, 512);
            return;
        }

        if (!fpvActive || drone == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(OVERLAY, 0, 0, gw, gh, 0.0f, 0.0f, TEX_W, TEX_H, TEX_W, TEX_H);

        float sx = gw / (float) TEX_W;
        float sy = gh / (float) TEX_H;
        Font font = mc.font;

        int battTicks = drone.getBatteryTicks();
        int pct = battTicks <= 0 ? 0 : Mth.clamp(Math.max(1, Math.round(battTicks * 100.0f / 4800.0f)), 0, 100);
        float voltage = drone.getBatteryVoltage();
        float battTemp = drone.getBatteryTemp();
        int speedKmh = Math.max(0, Math.round(drone.getSpeedKmh()));
        double altY = Mth.lerp(partialTick, drone.yo, drone.getY());

        Quaternionf q = drone.getInterpolatedOrientation(partialTick);
        float fwdX = 2.0f * (q.x * q.z + q.w * q.y);
        float fwdY = 2.0f * (q.y * q.z - q.w * q.x);
        float fwdZ = 1.0f - 2.0f * (q.x * q.x + q.y * q.y);

        float headingDeg = (float) Math.toDegrees(Math.atan2(fwdX, -fwdZ));
        if (headingDeg < 0.0f) {
            headingDeg += 360.0f;
        }
        float pitchDeg = (float) Math.toDegrees(Math.asin(Mth.clamp(fwdY, -1.0f, 1.0f)));

        drawCenteredInBox(g, font, String.valueOf((int) Math.round(altY)), 78f, 428f, 144f, 457f, 3.0f, sx, sy, 0xFFFFFF);

        int pctColor = pct <= 15 ? 0xFF4040 : 0xFFFFFF;
        drawRightInBox(g, font, String.valueOf(pct), 157f, 62f, 208f, 96f, 3.2f, sx, sy, pctColor);

        String voltText = String.format(Locale.ROOT, "%.1f", voltage);
        int voltColor = voltage <= 20.4f ? 0xFF5555 : 0xFFFFFF;
        drawRightInBox(g, font, voltText, 82f, 121f, 156f, 144f, 2.75f, sx, sy, voltColor);

        int tempColor = battTemp >= 80.0f ? 0xFF4040 : 0xFFFFFF;
        drawCenteredInBox(g, font, Math.round(battTemp) + "°C", 35f, 168f, 113f, 192f, 2.55f, sx, sy, tempColor);

        drawCenteredInBox(g, font, "НЕТ ЗАРЯДА", 767f, 126f, 904f, 173f, 2.5f, sx, sy, 0xFFFFFF);

        drawCenteredInBox(g, font, String.valueOf(speedKmh), 1526f, 428f, 1608f, 460f, 3.1f, sx, sy, 0xFFFFFF);

        renderWarningsBelowPayload(g, font, mc.level.getGameTime(), pct, battTemp, sx, sy);

        renderCompassTape(g, font, headingDeg, sx, sy, gw, gh);
        renderAltitudeTape(g, font, (float) altY, sx, sy, gw, gh);
        renderPitchTape(g, font, pitchDeg, sx, sy, gw, gh);
    }

    private static void renderKillcamLetterbox(GuiGraphics g, int gw, int gh, float partialTick) {
        float flashAlpha = FpvKillcamManager.getImpactFlashAlpha(partialTick);
        if (flashAlpha > 0.005f) {
            int a = Mth.clamp(Math.round(flashAlpha * 255.0f), 0, 255);
            g.fill(0, 0, gw, gh, (a << 24) | 0xFFF2D6);
        }

        float p = FpvKillcamManager.getNormalizedProgress(partialTick);
        float openEase = 1.0f;
        if (p < 0.05f) {
            float u = p / 0.05f;
            openEase = u * u * (3.0f - 2.0f * u);
        } else if (p > 0.93f) {
            float u = (1.0f - p) / 0.07f;
            openEase = Mth.clamp(u * u * (3.0f - 2.0f * u), 0.0f, 1.0f);
        }

        int barH = Math.round(gh * 0.075f * openEase);
        if (barH > 0) {
            g.fill(0, 0, gw, barH, 0xEB000000);
            g.fill(0, gh - barH, gw, gh, 0xEB000000);
        }
    }

    private static void renderWarningsBelowPayload(GuiGraphics g, Font font, long gameTime, int pct, float battTemp, float sx, float sy) {
        boolean overheat = battTemp >= 80.0f;
        boolean low = pct <= 15;
        boolean blinkOn = (gameTime / 10L) % 2L == 0L;

        float centerX = 835.5f;
        float lineY = 190.0f;

        if (overheat || low) {
            if (blinkOn || (overheat && low)) {
                String warn;
                if (overheat && low) {
                    warn = blinkOn ? "ПЕРЕГРЕВ" : "ПЕРЕРАЗРЯД";
                } else {
                    warn = overheat ? "ПЕРЕГРЕВ" : "ПЕРЕРАЗРЯД";
                }
                drawVirtualCenteredAt(g, font, warn, centerX, lineY, 2.3f, sx, sy, 0xFF2020);
            }
            lineY += 28.0f;
        }

        if (distance >= DroneControlPacket.WARNING_START_RANGE) {
            int remaining = Math.max(0, (int) Math.ceil(DroneControlPacket.MAX_RANGE - distance));
            drawVirtualCenteredAt(g, font, "ДО ПОТЕРИ СИГНАЛА: " + remaining + " м", centerX, lineY, 2.2f, sx, sy, 0xFFFF55);
        }
    }

    private static void renderCompassTape(GuiGraphics g, Font font, float headingDeg, float sx, float sy, int gw, int gh) {
        int headInt = ((Math.round(headingDeg) % 360) + 360) % 360;
        String centerReadout = String.format(Locale.ROOT, "%03d°", headInt);
        drawVirtualCenteredAt(g, font, centerReadout, COMPASS_CENTER_X, 34.0f, 1.9f, sx, sy, 0xFFFFFF);

        enableVirtualScissor(g, 476f, 46f, 1196f, 70f, sx, sy, gw, gh);
        for (int deg = 0; deg < 360; deg += 15) {
            float diff = wrapDegrees180(deg - headingDeg);
            if (Math.abs(diff) > 52.0f) {
                continue;
            }
            float vx = COMPASS_CENTER_X + diff * COMPASS_PX_PER_DEG;
            String label = compassTickLabel(deg);
            boolean isCardinal = (deg % 45 == 0);
            float scale = isCardinal ? 2.0f : 1.8f;
            drawVirtualCenteredAt(g, font, label, vx, 56.5f, scale, sx, sy, 0xFFFFFF);
        }
        g.disableScissor();
    }

    private static void renderAltitudeTape(GuiGraphics g, Font font, float altY, float sx, float sy, int gw, int gh) {
        enableVirtualScissor(g, 252f, 276f, 330f, 650f, sx, sy, gw, gh);
        int baseTen = Math.round(altY / 10.0f) * 10;
        for (int step = -3; step <= 3; step++) {
            int labelAlt = baseTen + step * 10;
            float vy = ALT_SCALE_CENTER_Y - (labelAlt - altY) * ALT_PX_PER_METER;
            if (vy < 270.0f || vy > 656.0f) {
                continue;
            }
            drawVirtualLeftAt(g, font, String.valueOf(labelAlt), 258.0f, vy, 1.95f, sx, sy, 0xFFFFFF);
        }
        g.disableScissor();
    }

    private static void renderPitchTape(GuiGraphics g, Font font, float pitchDeg, float sx, float sy, int gw, int gh) {
        enableVirtualScissor(g, 1400f, 267f, 1475f, 651f, sx, sy, gw, gh);
        for (int labelPitch = -90; labelPitch <= 90; labelPitch += 15) {
            float vy = PITCH_SCALE_CENTER_Y - (labelPitch - pitchDeg) * PITCH_PX_PER_DEG;
            if (vy < 262.0f || vy > 656.0f) {
                continue;
            }
            String txt = labelPitch > 0 ? ("+" + labelPitch) : String.valueOf(labelPitch);
            drawVirtualLeftAt(g, font, txt, 1404.0f, vy, 1.95f, sx, sy, 0xFFFFFF);
        }
        g.disableScissor();
    }

    private static String compassTickLabel(int deg) {
        return switch (deg) {
            case 0 -> "N";
            case 45 -> "NE";
            case 90 -> "E";
            case 135 -> "SE";
            case 180 -> "S";
            case 225 -> "SW";
            case 270 -> "W";
            case 315 -> "NW";
            default -> String.valueOf(deg);
        };
    }

    private static float wrapDegrees180(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    private static void enableVirtualScissor(GuiGraphics g, float x1, float y1, float x2, float y2, float sx, float sy, int gw, int gh) {
        int gx1 = Mth.clamp(Math.round(x1 * sx), 0, gw);
        int gy1 = Mth.clamp(Math.round(y1 * sy), 0, gh);
        int gx2 = Mth.clamp(Math.round(x2 * sx), 0, gw);
        int gy2 = Mth.clamp(Math.round(y2 * sy), 0, gh);
        g.enableScissor(gx1, gy1, Math.max(gx1, gx2), Math.max(gy1, gy2));
    }

    private static void drawCenteredInBox(GuiGraphics g, Font font, String text, float x1, float y1, float x2, float y2, float maxScale, float sx, float sy, int color) {
        int tw = Math.max(1, font.width(text));
        float bw = x2 - x1;
        float bh = y2 - y1;
        float scale = Math.min(maxScale, Math.min(bw / tw, bh / 7.5f));
        float vx = (x1 + x2) * 0.5f - (tw * scale) * 0.5f;
        float vy = (y1 + y2) * 0.5f - (7.0f * scale) * 0.5f;
        drawScaledVirtual(g, font, text, vx, vy, scale, sx, sy, color);
    }

    private static void drawRightInBox(GuiGraphics g, Font font, String text, float x1, float y1, float x2, float y2, float maxScale, float sx, float sy, int color) {
        int tw = Math.max(1, font.width(text));
        float bw = x2 - x1;
        float bh = y2 - y1;
        float scale = Math.min(maxScale, Math.min(bw / tw, bh / 7.5f));
        float vx = x2 - tw * scale;
        float vy = (y1 + y2) * 0.5f - (7.0f * scale) * 0.5f;
        drawScaledVirtual(g, font, text, vx, vy, scale, sx, sy, color);
    }

    private static void drawVirtualCenteredAt(GuiGraphics g, Font font, String text, float centerX, float centerY, float scale, float sx, float sy, int color) {
        int tw = font.width(text);
        float vx = centerX - (tw * scale) * 0.5f;
        float vy = centerY - (7.0f * scale) * 0.5f;
        drawScaledVirtual(g, font, text, vx, vy, scale, sx, sy, color);
    }

    private static void drawVirtualLeftAt(GuiGraphics g, Font font, String text, float leftX, float centerY, float scale, float sx, float sy, int color) {
        float vy = centerY - (7.0f * scale) * 0.5f;
        drawScaledVirtual(g, font, text, leftX, vy, scale, sx, sy, color);
    }

    private static void drawScaledVirtual(GuiGraphics g, Font font, String text, float vx, float vy, float scale, float sx, float sy, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(vx * sx, vy * sy, 0.0f);
        pose.scale(sx * scale, sy * scale, 1.0f);
        g.drawString(font, text, 0, 0, color, true);
        pose.popPose();
    }
}
