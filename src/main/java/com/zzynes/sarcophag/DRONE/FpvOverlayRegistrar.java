package com.zzynes.sarcophag.DRONE;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = Dist.CLIENT)
public class FpvOverlayRegistrar {

    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!FpvOverlay.fpvActive && FpvOverlay.connectionLostTimer <= 0 && !FpvKillcamManager.isActive()) {
            return;
        }
        String id = event.getOverlay().id().getPath();
        if (id.equals(VanillaGuiOverlay.CROSSHAIR.id().getPath())
                || id.equals(VanillaGuiOverlay.HOTBAR.id().getPath())
                || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id().getPath())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id().getPath())
                || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id().getPath())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id().getPath())
                || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id().getPath())
                || id.equals(VanillaGuiOverlay.JUMP_BAR.id().getPath())
                || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id().getPath())
                || id.equals(VanillaGuiOverlay.ITEM_NAME.id().getPath())
                || id.equals(VanillaGuiOverlay.POTION_ICONS.id().getPath())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        FpvOverlay.render(event.getGuiGraphics(), event.getPartialTick());
    }
}
