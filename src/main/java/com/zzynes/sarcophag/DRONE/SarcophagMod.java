package com.zzynes.sarcophag.DRONE;

import com.zzynes.sarcophag.BLOCK.BlockRegistry;
import com.zzynes.sarcophag.DRONE.init.ModBlocks;
import com.zzynes.sarcophag.DRONE.init.ModCreativeTabs;
import com.zzynes.sarcophag.DRONE.init.ModEntityTypes;
import com.zzynes.sarcophag.DRONE.init.ModItems;
import com.zzynes.sarcophag.Domm.DommRegistry;
import com.zzynes.sarcophag.Radio.RadioMod;
import com.zzynes.sarcophag.VVER.VverRegistry;
import com.zzynes.sarcophag.WATER.FiniteWaterBlocks;
import com.zzynes.sarcophag.WATER.FiniteWaterItems;
import com.zzynes.sarcophag.WATER.RadioactiveWater;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("sarcophag")
public class SarcophagMod {
    public static final String MOD_ID = "sarcophag";

    public SarcophagMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModEntityTypes.register(modEventBus);
        DommRegistry.register(modEventBus);
        VverRegistry.register(modEventBus);
        RadioMod.init(modEventBus);
        BlockRegistry.register(modEventBus);
        ModSounds.register(modEventBus);
        BlastParticleTypes.register(modEventBus);
        FiniteWaterItems.register(modEventBus);
        FiniteWaterBlocks.register(modEventBus);
        RadioactiveWater.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ModNetwork.register();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer serverPlayer) {
            FpvCameraAnchor.tickServerPlayer(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer
                && event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            if (FpvCameraAnchor.handleVoidProtection(serverPlayer)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            FpvCameraAnchor.detachImmediate(serverPlayer);
            if (serverPlayer.isUsingItem() && serverPlayer.getUseItem().getItem() == ModItems.HANDHELD_SIREN.get()) {
                HandheldSirenItem.stopSiren(serverPlayer.level(), serverPlayer);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            FpvCameraAnchor.detachImmediate(serverPlayer);
            if (serverPlayer.isUsingItem() && serverPlayer.getUseItem().getItem() == ModItems.HANDHELD_SIREN.get()) {
                HandheldSirenItem.stopSiren(serverPlayer.level(), serverPlayer);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            FpvCameraAnchor.detachImmediate(serverPlayer);
        }
    }
}
