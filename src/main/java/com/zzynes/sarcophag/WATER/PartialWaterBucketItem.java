package com.zzynes.sarcophag.WATER;

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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class PartialWaterBucketItem extends Item {
    private static final String WATER_UNITS_TAG = "FiniteWaterUnits";
    private static final String WATER_MILLIBUCKETS_TAG = "FiniteWaterMillibuckets";

    private final FiniteWaterManager.FiniteFluidKind kind;

    public PartialWaterBucketItem() {
        this(FiniteWaterManager.FiniteFluidKind.WATER);
    }

    public PartialWaterBucketItem(FiniteWaterManager.FiniteFluidKind kind) {
        super(new Item.Properties().stacksTo(1));
        this.kind = kind;
    }

    public static int getUnits(ItemStack stack) {
        return getMillibuckets(stack) / FiniteWaterManager.MILLIBUCKETS_PER_UNIT;
    }

    public static int getMillibuckets(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return 0;
        }
        if (tag.contains(WATER_MILLIBUCKETS_TAG)) {
            return Mth.clamp(tag.getInt(WATER_MILLIBUCKETS_TAG), 0, FiniteWaterManager.BUCKET_MILLIBUCKETS);
        }
        int units = Mth.clamp(tag.getInt(WATER_UNITS_TAG), 0, FiniteWaterManager.BUCKET_UNITS);
        return units * FiniteWaterManager.MILLIBUCKETS_PER_UNIT;
    }

    public static ItemStack stackForUnits(int units) {
        return stackForMillibuckets(units * FiniteWaterManager.MILLIBUCKETS_PER_UNIT);
    }

    public static ItemStack stackForUnits(int units, FiniteWaterManager.FiniteFluidKind kind) {
        return stackForMillibuckets(units * FiniteWaterManager.MILLIBUCKETS_PER_UNIT, kind);
    }

    public static ItemStack stackForMillibuckets(int millibuckets) {
        return stackForMillibuckets(millibuckets, FiniteWaterManager.FiniteFluidKind.WATER);
    }

    public static ItemStack stackForMillibuckets(int millibuckets, FiniteWaterManager.FiniteFluidKind kind) {
        int clamped = Mth.clamp(millibuckets, 0, FiniteWaterManager.BUCKET_MILLIBUCKETS);
        if (clamped <= 0) {
            return new ItemStack(Items.BUCKET);
        }
        if (clamped >= FiniteWaterManager.BUCKET_MILLIBUCKETS) {
            return new ItemStack(kind == FiniteWaterManager.FiniteFluidKind.RADIOACTIVE_WATER
                    ? RadioactiveWater.BUCKET.get()
                    : Items.WATER_BUCKET);
        }
        ItemStack stack = new ItemStack(kind == FiniteWaterManager.FiniteFluidKind.RADIOACTIVE_WATER
                ? FiniteWaterItems.PARTIAL_RADIOACTIVE_WATER_BUCKET.get()
                : FiniteWaterItems.PARTIAL_WATER_BUCKET.get());
        stack.getOrCreateTag().putInt(WATER_MILLIBUCKETS_TAG, clamped);
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int millibuckets = getMillibuckets(stack);
        return millibuckets > 0 && millibuckets < FiniteWaterManager.BUCKET_MILLIBUCKETS;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int millibuckets = getMillibuckets(stack);
        return millibuckets <= 0 ? 0 : Math.max(1, Math.round(13.0F * millibuckets / FiniteWaterManager.BUCKET_MILLIBUCKETS));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return this.kind == FiniteWaterManager.FiniteFluidKind.RADIOACTIVE_WATER ? 0x74C365 : 0x3F76E4;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int millibuckets = getMillibuckets(stack);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        BlockPos pickupPos = FiniteWaterManager.findBucketPickupPos(level, player, hit, false, this.kind);
        if (pickupPos != null) {
            Direction face = hit.getDirection();
            if (!level.mayInteract(player, pickupPos) || !player.mayUseItemAt(pickupPos, face, stack)) {
                return InteractionResultHolder.fail(stack);
            }
            int freeMillibuckets = FiniteWaterManager.BUCKET_MILLIBUCKETS - millibuckets;
            if (freeMillibuckets <= 0) {
                return InteractionResultHolder.fail(stack);
            }
            if (level.isClientSide) {
                return InteractionResultHolder.success(stack);
            }
            int pickedMillibuckets = FiniteWaterManager.tryPickupFluidMillibuckets(level, pickupPos, freeMillibuckets, this.kind);
            if (pickedMillibuckets <= 0) {
                return InteractionResultHolder.fail(stack);
            }
            ItemStack result = stackForMillibuckets(millibuckets + pickedMillibuckets, this.kind);
            player.setItemInHand(hand, result);
            player.awardStat(Stats.ITEM_USED.get(this));
            level.playSound(player, pickupPos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pickupPos);
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, result);
            }
            return InteractionResultHolder.consume(result);
        }
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
        if (millibuckets <= 0) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return FiniteWaterManager.canPlaceFluidMillibuckets(level, placement, millibuckets, this.kind)
                    ? InteractionResultHolder.success(stack)
                    : InteractionResultHolder.fail(stack);
        }
        int addedMillibuckets = FiniteWaterManager.tryPlaceFluidMillibuckets(level, placement, millibuckets, this.kind);
        if (addedMillibuckets <= 0) {
            return InteractionResultHolder.fail(stack);
        }
        ItemStack result = stack;
        if (!player.getAbilities().instabuild) {
            result = stackForMillibuckets(millibuckets - addedMillibuckets, this.kind);
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
