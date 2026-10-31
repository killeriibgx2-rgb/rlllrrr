package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class CoreFillerItem extends Item {
    private static final String KEY_VESSEL = "VverFillerVessel";
    private static final String KEY_TARGET = "VverFillerTarget";
    private static final String KEY_DRAIN = "VverFillerDrain";

    public CoreFillerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !isTarget(level, pos)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer) {
            ReactorVesselData.Vessel vessel = ReactorVesselData.get(serverLevel).findOrScan(serverLevel, pos);
            if (vessel == null) {
                return InteractionResult.FAIL;
            }
            CompoundTag tag = context.getItemInHand().getOrCreateTag();
            tag.putUUID(KEY_VESSEL, vessel.id());
            tag.putLong(KEY_TARGET, pos.asLong());
            tag.putBoolean(KEY_DRAIN, player.isShiftKeyDown());
        }
        player.startUsingItem(context.getHand());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(KEY_VESSEL) || !tag.contains(KEY_TARGET)) {
            player.stopUsingItem();
            return;
        }
        BlockPos target = BlockPos.of(tag.getLong(KEY_TARGET));
        if (!serverLevel.isLoaded(target) || player.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 64.0) {
            player.stopUsingItem();
            return;
        }
        ReactorVesselData data = ReactorVesselData.get(serverLevel);
        UUID vesselId = tag.getUUID(KEY_VESSEL);
        ReactorVesselData.Vessel vessel = data.getVessel(vesselId);
        int elapsed = getUseDuration(stack) - remainingUseDuration;
        if (vessel == null || elapsed % 20 == 0) {
            vessel = data.findOrScan(serverLevel, target);
            if (vessel != null) {
                tag.putUUID(KEY_VESSEL, vessel.id());
            }
        }
        if (vessel == null) {
            player.stopUsingItem();
            return;
        }
        double ratePerSecond = 5.0 + vessel.capacity() * 0.02;
        double delta = ratePerSecond / 20.0;
        if (tag.getBoolean(KEY_DRAIN)) {
            delta = -delta;
        }
        double changed = data.addCoolant(vessel, delta);
        if (Math.abs(changed) < 0.000001) {
            player.stopUsingItem();
            return;
        }
        if (elapsed % 10 == 0) {
            player.playSound(tag.getBoolean(KEY_DRAIN) ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_EMPTY, 0.35f, 0.9f);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(KEY_VESSEL);
            tag.remove(KEY_TARGET);
            tag.remove(KEY_DRAIN);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.fly_drone.vver_core_filler.fill"));
        tooltip.add(Component.translatable("tooltip.fly_drone.vver_core_filler.drain"));
    }

    private static boolean isTarget(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof HullBlock
                || state.is(VverRegistry.TVS_BLOCK.get())
                || state.is(VverRegistry.SUZ_BLOCK.get());
    }
}
