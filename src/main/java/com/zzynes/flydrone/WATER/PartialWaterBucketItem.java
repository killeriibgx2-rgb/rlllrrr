package com.zzynes.flydrone.WATER;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class PartialWaterBucketItem extends Item {
    private static final String WATER_UNITS_TAG = "FiniteWaterUnits";

    public PartialWaterBucketItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static int getUnits(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        int units = tag == null ? 0 : tag.getInt(WATER_UNITS_TAG);
        return Mth.clamp(units, 0, FiniteWaterManager.BUCKET_UNITS);
    }

    public static ItemStack stackForUnits(int units) {
        int clamped = Mth.clamp(units, 0, FiniteWaterManager.BUCKET_UNITS);
        if (clamped <= 0) {
            return new ItemStack(Items.BUCKET);
        }
        if (clamped >= FiniteWaterManager.BUCKET_UNITS) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        ItemStack stack = new ItemStack(FiniteWaterItems.PARTIAL_WATER_BUCKET.get());
        stack.getOrCreateTag().putInt(WATER_UNITS_TAG, clamped);
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int units = getUnits(stack);
        return units > 0 && units < FiniteWaterManager.BUCKET_UNITS;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getUnits(stack) / FiniteWaterManager.BUCKET_UNITS);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3F76E4;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int units = getUnits(stack);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos hitPos = hit.getBlockPos();
        Direction face = hit.getDirection();
        BlockPos placement = hitPos.relative(face);
        if (!level.mayInteract(player, hitPos) || !player.mayUseItemAt(placement, face, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.getBlockState(hitPos).is(Blocks.WATER)) {
            int freeUnits = FiniteWaterManager.BUCKET_UNITS - units;
            if (freeUnits <= 0) {
                return InteractionResultHolder.fail(stack);
            }
            if (level.isClientSide) {
                return InteractionResultHolder.success(stack);
            }
            int pickedUnits = FiniteWaterManager.tryPickupWaterUnits(level, hitPos, freeUnits);
            if (pickedUnits <= 0) {
                return InteractionResultHolder.fail(stack);
            }
            ItemStack result = stackForUnits(units + pickedUnits);
            player.setItemInHand(hand, result);
            player.awardStat(Stats.ITEM_USED.get(this));
            level.playSound(player, hitPos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, hitPos);
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, result);
            }
            return InteractionResultHolder.consume(result);
        }
        if (units <= 0) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return FiniteWaterManager.canPlaceWaterUnits(level, placement, units)
                    ? InteractionResultHolder.success(stack)
                    : InteractionResultHolder.fail(stack);
        }
        int addedUnits = FiniteWaterManager.tryPlaceWaterUnits(level, placement, units);
        if (addedUnits <= 0) {
            return InteractionResultHolder.fail(stack);
        }
        ItemStack result = stack;
        if (!player.getAbilities().instabuild) {
            result = stackForUnits(units - addedUnits);
            player.setItemInHand(hand, result);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        level.playSound(player, placement, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, placement);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, placement, stack);
        }
        return InteractionResultHolder.consume(result);
    }
}
