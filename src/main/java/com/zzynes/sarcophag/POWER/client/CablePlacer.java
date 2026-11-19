package com.zzynes.sarcophag.POWER.client;

import com.zzynes.sarcophag.DRONE.ModNetwork;
import com.zzynes.sarcophag.POWER.Anchor;
import com.zzynes.sarcophag.POWER.CableCore;
import com.zzynes.sarcophag.POWER.CableItem;
import com.zzynes.sarcophag.POWER.CablePlacePacket;
import com.zzynes.sarcophag.POWER.CableRoute;
import com.zzynes.sarcophag.POWER.CableRouter;
import com.zzynes.sarcophag.POWER.CableTier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

// Прокладка: первый клик задаёт начало, второй заканчивает прогон.
// Призрак считается на клиенте каждый тик, сервер потом пересчитает по-своему.
public final class CablePlacer {

    private static Anchor start;
    private static CableTier tier = CableTier.CONTROL;
    private static CableRoute preview = CableRoute.EMPTY;
    private static boolean valid;

    private CablePlacer() {
    }

    public static void click(UseOnContext context, CableTier itemTier) {
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        Anchor anchor = Anchor.fromHit(hit);
        if (start == null) {
            start = anchor;
            tier = itemTier;
            preview = CableRoute.EMPTY;
            valid = false;
            return;
        }
        if (!start.equals(anchor)) {
            ModNetwork.CHANNEL.sendToServer(new CablePlacePacket(start, anchor, itemTier.ordinal(), coreOf(context.getItemInHand()).ordinal()));
        }
        cancel();
    }

    public static void tick() {
        if (start == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            cancel();
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof CableItem item) || item.tier() != tier) {
            cancel();
            return;
        }
        if (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            preview = CableRoute.EMPTY;
            valid = false;
            return;
        }
        Anchor target = Anchor.fromHit(hit);
        if (target.equals(start)) {
            preview = CableRoute.EMPTY;
            valid = false;
            return;
        }
        Optional<CableRoute> route = CableRouter.route(player.level(), start, target, tier);
        valid = route.isPresent();
        preview = route.orElse(CableRoute.EMPTY);
    }

    public static void cancel() {
        start = null;
        preview = CableRoute.EMPTY;
        valid = false;
    }

    public static boolean active() {
        return start != null;
    }

    public static Anchor startAnchor() {
        return start;
    }

    public static CableRoute preview() {
        return preview;
    }

    public static boolean valid() {
        return valid;
    }

    public static CableTier tier() {
        return tier;
    }

    private static CableCore coreOf(ItemStack stack) {
        return CableItem.coreOf(stack);
    }
}
