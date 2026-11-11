package com.zzynes.sarcophag.VVER;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VverEvents {
    private static final String KEY_DIMENSION = "SarcophagBshuDimension";
    private static final String KEY_POS = "SarcophagBshuPos";

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ReactorVesselData.get(level).tick(level);
            if (level.getGameTime() % 100L == 0L) {
                PipePressureData.get(level).cleanup(level);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        PipePressureData data = PipePressureData.get(level);
        if (PipePorts.isPipe(state)) {
            data.removePipePressure(event.getPos());
        }
        if (state.getBlock() instanceof GcnBlock) {
            data.removeEndpointPressure(GcnBlock.fluidMainPos(event.getPos(), state));
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && PipePorts.isPipe(event.getPlacedBlock())) {
            PipePressureData.get(level).removePipePressure(event.getPos());
        }
    }

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
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(KEY_POS) || !tag.contains(KEY_DIMENSION)) {
            return;
        }
        if (!tag.getString(KEY_DIMENSION).equals(player.serverLevel().dimension().location().toString())) {
            return;
        }
        BlockPos target = BlockPos.of(tag.getLong(KEY_POS));
        if (!bshu.linkTo(target)) {
            return;
        }
        tag.remove(KEY_POS);
        tag.remove(KEY_DIMENSION);
    }
}
