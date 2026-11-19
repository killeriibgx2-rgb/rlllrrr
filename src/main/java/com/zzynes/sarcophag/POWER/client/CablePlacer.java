package com.zzynes.sarcophag.POWER.client;

import com.zzynes.sarcophag.DRONE.ModNetwork;
import com.zzynes.sarcophag.POWER.Anchor;
import com.zzynes.sarcophag.POWER.CableCore;
import com.zzynes.sarcophag.POWER.CableItem;
import com.zzynes.sarcophag.POWER.CablePlacePacket;
import com.zzynes.sarcophag.POWER.CableRoute;
import com.zzynes.sarcophag.POWER.CableRouter;
import com.zzynes.sarcophag.POWER.CableTier;
import com.zzynes.sarcophag.POWER.PowerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

// Прокладка цепочкой: первый клик задаёт начало, каждый следующий добавляет участок
// и новый конец становится началом следующего. Shift-клик или смена предмета сбрасывают.
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
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            cancel();
            return;
        }
        if (start == null) {
            start = anchor;
            tier = itemTier;
            preview = CableRoute.EMPTY;
            valid = false;
            return;
        }
        if (start.equals(anchor)) {
            cancel();
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new CablePlacePacket(start, anchor, itemTier.ordinal(), coreOf(context.getItemInHand()).ordinal()));
        // Цепочка: конец предыдущего участка становится началом следующего.
        start = anchor;
        tier = itemTier;
        preview = CableRoute.EMPTY;
        valid = false;
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
        BlockHitResult hit = blockHit();
        if (hit == null) {
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

    // Точка, которую видно под прицелом, пока предмет в руке: по ней понятно, откуда начнётся прогон.
    public static Anchor hoverAnchor() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof CableItem item)) {
            return null;
        }
        tier = item.tier();
        BlockHitResult hit = blockHit();
        return hit == null ? null : Anchor.fromHit(hit);
    }

    // Кабель сам стал блоком, поэтому прицел цепляется за него. Для прокладки смотрим сквозь него.
    private static BlockHitResult blockHit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        if (!minecraft.level.getBlockState(hit.getBlockPos()).is(PowerRegistry.CABLE.get())) {
            return hit;
        }
        Vec3 eye = minecraft.player.getEyePosition(1.0f);
        Vec3 direction = hit.getLocation().subtract(eye);
        if (direction.lengthSqr() < 1.0e-6) {
            return hit;
        }
        direction = direction.normalize();
        Vec3 from = hit.getLocation().add(direction.scale(0.06));
        BlockHitResult behind = minecraft.level.clip(new ClipContext(from, from.add(direction.scale(8.0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        return behind != null && behind.getType() == HitResult.Type.BLOCK ? behind : hit;
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
