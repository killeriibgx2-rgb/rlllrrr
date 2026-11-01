package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

public class PressureStickItem extends Item {
    private static final double PRESSURE_STEP_ATM = 10.0;

    public PressureStickItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!isTarget(pos, state)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (level instanceof ServerLevel serverLevel) {
            double delta = player != null && player.isShiftKeyDown() ? -PRESSURE_STEP_ATM : PRESSURE_STEP_ATM;
            boolean changed = PipePorts.isPipe(state)
                    ? PipePressureManager.adjustFromPipe(serverLevel, pos, delta)
                    : PipePressureManager.adjustFromEndpointBlock(serverLevel, pos, delta);
            if (!changed) {
                return InteractionResult.FAIL;
            }
            level.playSound(null, pos, delta > 0.0 ? SoundEvents.PISTON_EXTEND : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.45f, delta > 0.0 ? 0.8f : 1.1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.fly_drone.vver_pressure_stick.increase"));
        tooltip.add(Component.translatable("tooltip.fly_drone.vver_pressure_stick.decrease"));
    }

    private static boolean isTarget(BlockPos pos, BlockState state) {
        return PipePorts.isPipe(state)
                || state.getBlock() instanceof GcnBlock
                || state.getBlock() instanceof HullNozzleBlock
                || SteamGeneratorBlock.portAt(pos, state) != null;
    }
}
