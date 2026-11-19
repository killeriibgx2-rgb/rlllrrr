package com.zzynes.sarcophag.POWER.client;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import com.zzynes.sarcophag.POWER.CableCore;
import com.zzynes.sarcophag.POWER.CableItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Клики по кабелю и выбор жилы. Ванильная ломка блока отменяется, если кабель ближе.
@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class CableInput {

    private CableInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CablePlacer.tick();
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !player.isShiftKeyDown() || minecraft.screen != null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof CableItem)) {
            return;
        }
        CableCore core = CableItem.coreOf(held).next();
        held.getOrCreateTag().putByte("Core", (byte) core.ordinal());
        player.displayClientMessage(Component.translatable("message.sarcophag.cable.core", Component.translatable(core.key())), true);
        event.setCanceled(true);
    }
}
