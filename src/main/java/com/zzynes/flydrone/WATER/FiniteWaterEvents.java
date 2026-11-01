package com.zzynes.flydrone.WATER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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

    @SubscribeEvent
    public static void onFillBucket(FillBucketEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide || !event.getEmptyBucket().is(Items.BUCKET)) {
            return;
        }
        Player player = event.getEntity();
        HitResult hit = player.pick(player.isCreative() ? 5.0D : 4.5D, 1.0F, true);
        if (!(hit instanceof BlockHitResult blockHit)) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (level.getFluidState(pos).isSource()) {
            return;
        }
        if (!level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, blockHit.getDirection(), event.getEmptyBucket())) {
            return;
        }
        if (!FiniteWaterManager.tryPickupBucket(level, pos)) {
            return;
        }
        ItemStack filled = new ItemStack(Items.WATER_BUCKET);
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
        if (!stack.is(Items.WATER_BUCKET)) {
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
        if (!FiniteWaterManager.canAddBucket(level, placement)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide) {
            return;
        }
        if (!FiniteWaterManager.tryAddBucket(level, placement)) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(event.getHand(), new ItemStack(Items.BUCKET));
        }
        player.awardStat(Stats.ITEM_USED.get(Items.WATER_BUCKET));
        level.playSound(player, placement, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, placement);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, placement, stack);
        }
    }
}
