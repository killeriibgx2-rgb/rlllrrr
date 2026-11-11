package com.zzynes.sarcophag.DRONE;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = Dist.CLIENT)
public final class FpvConcussionClient {

    private static int ticksRemaining;
    private static int age;

    private FpvConcussionClient() {
    }

    public static void trigger() {
        ticksRemaining = 44;
        age = 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ticksRemaining <= 0) {
            return;
        }
        if (Minecraft.getInstance().player == null) {
            ticksRemaining = 0;
            age = 0;
            return;
        }
        ticksRemaining--;
        age++;
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (PipReplayRenderer.isRendering() || ticksRemaining <= 0) {
            return;
        }
        float fade = Math.min(1.0f, ticksRemaining / 12.0f);
        float t = (age + (float) event.getPartialTick()) * 1.65f;
        float amplitude = 1.9f * fade;
        event.setYaw(event.getYaw() + Mth.sin(t * 1.11f) * amplitude);
        event.setPitch(event.getPitch() + Mth.cos(t * 1.73f) * amplitude * 0.72f);
        event.setRoll(event.getRoll() + Mth.sin(t * 0.83f) * amplitude * 0.62f);
    }
}
