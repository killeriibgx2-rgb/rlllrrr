package com.zzynes.flydrone.WATER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FlyDroneMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FiniteWaterEvents {
    private FiniteWaterEvents() {
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FiniteWaterManager.activateAround(level, event.getPos(), event.getNotifiedSides());
        }
    }

    @SubscribeEvent
    public static void onCreateFluidSource(BlockEvent.CreateFluidSourceEvent event) {
        if (event.getState().getFluidState().is(FluidTags.WATER)
                && FiniteWaterManager.isFiniteAt(event.getLevel(), event.getPos())) {
            event.setResult(Event.Result.DENY);
        }
    }
}
