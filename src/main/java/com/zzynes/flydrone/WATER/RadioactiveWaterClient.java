package com.zzynes.flydrone.WATER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = FlyDroneMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
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
