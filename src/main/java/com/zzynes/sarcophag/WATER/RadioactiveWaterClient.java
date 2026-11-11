package com.zzynes.sarcophag.WATER;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RadioactiveWaterClient {
    private RadioactiveWaterClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(RadioactiveWater.SOURCE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(RadioactiveWater.FLOWING.get(), RenderType.translucent());
        });
    }
}
