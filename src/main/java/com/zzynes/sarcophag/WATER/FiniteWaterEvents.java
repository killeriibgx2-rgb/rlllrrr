package com.zzynes.sarcophag.WATER;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            FiniteWaterManager.tickSafetyWake(level);
        }
    }

    @SubscribeEvent
    public static void onCreateFluidSource(BlockEvent.CreateFluidSourceEvent event) {
        if (FiniteWaterManager.isManagedFluid(event.getState().getFluidState())
                && FiniteWaterManager.isFiniteAt(event.getLevel(), event.getPos())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onFillBucket(FillBucketEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide || !event.getEmptyBucket().is(Items.BUCKET)) {
            return;
        }
        Player player = event.getEntity();
        HitResult hit = player.pick(player.isCreative() ? 5.0D : 4.5D, 1.0F, true);
        BlockHitResult blockHit = hit instanceof BlockHitResult result ? result : null;
        FiniteWaterManager.FiniteFluidKind kind = null;
        if (blockHit != null) {
            kind = FiniteWaterManager.kindAt(level, blockHit.getBlockPos());
            if (kind == null) {
                kind = FiniteWaterManager.kindAt(level, blockHit.getBlockPos().relative(blockHit.getDirection()));
            }
        }
        BlockPos pos;
        if (kind != null) {
            pos = FiniteWaterManager.findBucketPickupPos(level, player, blockHit, true, kind);
        } else {
            kind = FiniteWaterManager.FiniteFluidKind.WATER;
            pos = FiniteWaterManager.findBucketPickupPos(level, player, blockHit, true, kind);
            if (pos == null) {
                kind = FiniteWaterManager.FiniteFluidKind.RADIOACTIVE_WATER;
                pos = FiniteWaterManager.findBucketPickupPos(level, player, blockHit, true, kind);
            }
        }
        if (pos == null || level.getFluidState(pos).isSource()) {
            return;
        }
        Direction face = blockHit == null ? Direction.UP : blockHit.getDirection();
        if (!level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, face, event.getEmptyBucket())) {
            return;
        }
        int pickedMillibuckets = FiniteWaterManager.tryPickupFluidMillibuckets(level, pos, FiniteWaterManager.BUCKET_MILLIBUCKETS, kind);
        if (pickedMillibuckets <= 0) {
            return;
        }
        ItemStack filled = PartialWaterBucketItem.stackForMillibuckets(pickedMillibuckets, kind);
        event.setFilledBucket(filled);
        event.setResult(Event.Result.ALLOW);
        if (player.getAbilities().instabuild) {
            ItemUtils.createFilledResult(event.getEmptyBucket(), player, filled.copy());
        }
        player.awardStat(Stats.ITEM_USED.get(Items.BUCKET));
        level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, filled);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        FiniteWaterManager.FiniteFluidKind kind;
        if (stack.is(Items.WATER_BUCKET)) {
            kind = FiniteWaterManager.FiniteFluidKind.WATER;
        } else if (stack.is(RadioactiveWater.BUCKET.get())) {
            kind = FiniteWaterManager.FiniteFluidKind.RADIOACTIVE_WATER;
        } else {
            return;
        }
        Level level = event.getLevel();
        Player player = event.getEntity();
        Direction face = event.getHitVec().getDirection();
        BlockPos placement = event.getPos().relative(face);
        if (!level.mayInteract(player, placement)
                || !player.mayUseItemAt(placement, face, stack)) {
            return;
        }
        if (!FiniteWaterManager.canAddFluidMillibuckets(level, placement, FiniteWaterManager.BUCKET_MILLIBUCKETS, kind)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide) {
            return;
        }
        int addedMillibuckets = FiniteWaterManager.tryAddFluidMillibuckets(level, placement, FiniteWaterManager.BUCKET_MILLIBUCKETS, kind);
        if (addedMillibuckets <= 0) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(event.getHand(), PartialWaterBucketItem.stackForMillibuckets(FiniteWaterManager.BUCKET_MILLIBUCKETS - addedMillibuckets, kind));
        }
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        level.playSound(player, placement, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, placement);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, placement, stack);
        }
    }
}
