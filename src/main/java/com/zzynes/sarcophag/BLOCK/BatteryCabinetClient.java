package com.zzynes.sarcophag.BLOCK;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BatteryCabinetClient {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockRegistry.BATTERY_CABINET_BLOCK_ENTITY.get(), BatteryCabinetBlockEntityRenderer::new);
    }
}
