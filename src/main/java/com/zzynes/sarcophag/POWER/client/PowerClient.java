package com.zzynes.sarcophag.POWER.client;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import com.zzynes.sarcophag.POWER.PowerRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PowerClient {

    private PowerClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PowerRegistry.CABLE_RUN.get(), CableRunRenderer::new);
    }
}
