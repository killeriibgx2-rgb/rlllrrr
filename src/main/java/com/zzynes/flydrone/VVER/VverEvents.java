package com.zzynes.flydrone.VVER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FlyDroneMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VverEvents {
    private static final String KEY_DIMENSION = "FlyDroneBshuDimension";
    private static final String KEY_POS = "FlyDroneBshuPos";

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.STICK)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos clicked = event.getPos();
        BlockState state = level.getBlockState(clicked);
        boolean bshu = state.is(VverRegistry.BSHU_BLOCK.get());
        BlockPos component = bshu ? null : BshuBlockEntity.componentBase(level, clicked);
        if (!bshu && component == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (bshu) {
            linkFromHeldStick(player, clicked);
        } else {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong(KEY_POS, component.asLong());
            tag.putString(KEY_DIMENSION, level.dimension().location().toString());
            player.displayClientMessage(
                    Component.translatable("message.fly_drone.bshu_target_saved", component.getX(), component.getY(), component.getZ()).withStyle(ChatFormatting.GREEN),
                    true
            );
        }
    }

    public static void linkFromHeldStick(ServerPlayer player, BlockPos bshuPos) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(Items.STICK)) {
            return;
        }
        BlockEntity be = player.serverLevel().getBlockEntity(bshuPos);
        if (!(be instanceof BshuBlockEntity bshu)) {
            return;
        }
        if (bshu.isLinked()) {
            player.displayClientMessage(Component.translatable("message.fly_drone.bshu_already_linked").withStyle(ChatFormatting.RED), true);
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(KEY_POS) || !tag.contains(KEY_DIMENSION)) {
            player.displayClientMessage(Component.translatable("message.fly_drone.bshu_select_target").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!tag.getString(KEY_DIMENSION).equals(player.serverLevel().dimension().location().toString())) {
            player.displayClientMessage(Component.translatable("message.fly_drone.bshu_wrong_dimension").withStyle(ChatFormatting.RED), true);
            return;
        }
        BlockPos target = BlockPos.of(tag.getLong(KEY_POS));
        if (!bshu.linkTo(target)) {
            player.displayClientMessage(Component.translatable("message.fly_drone.bshu_link_failed").withStyle(ChatFormatting.RED), true);
            return;
        }
        tag.remove(KEY_POS);
        tag.remove(KEY_DIMENSION);
        player.displayClientMessage(
                Component.translatable(
                        "message.fly_drone.bshu_linked",
                        bshu.getSuzPositions().size(),
                        bshu.getTvsPositions().size()
                ).withStyle(ChatFormatting.GREEN),
                true
        );
    }
}
