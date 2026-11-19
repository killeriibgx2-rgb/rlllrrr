package com.zzynes.sarcophag.POWER;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Кабель держится за блок. Убрали опору или рядом рвануло — прогон осыпается целиком.
@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID)
public final class CableSupports {

    private CableSupports() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        checkNeighbours(level, List.of(event.getPos()));
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        checkNeighbours(level, event.getAffectedBlocks());
    }

    private static void checkNeighbours(Level level, List<BlockPos> broken) {
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : broken) {
            for (Direction direction : Direction.values()) {
                BlockPos cable = pos.relative(direction);
                if (seen.contains(cable) || !(level.getBlockState(cable).is(PowerRegistry.CABLE.get()))) {
                    continue;
                }
                collapse(level, cable, seen);
            }
        }
    }

    private static void collapse(Level level, BlockPos seed, Set<BlockPos> seen) {
        Set<BlockPos> run = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        run.add(seed);
        boolean supported = false;
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (level.getBlockState(next).is(PowerRegistry.CABLE.get())) {
                    if (run.add(next)) {
                        queue.add(next);
                    }
                    continue;
                }
                supported |= level.getBlockState(next).getCollisionShape(level, next).isEmpty() == false;
            }
        }
        seen.addAll(run);
        if (supported) {
            return;
        }
        for (BlockPos pos : run) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(PowerRegistry.CABLE.get())) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
                for (CableStrand strand : cable.strands()) {
                    Block.popResource(level, pos, stackFor(strand));
                }
            }
            level.removeBlock(pos, false);
        }
    }

    private static ItemStack stackFor(CableStrand strand) {
        Item item = switch (strand.tier) {
            case CONTROL -> PowerRegistry.CABLE_CONTROL.get();
            case POWER -> PowerRegistry.CABLE_POWER.get();
            case MAIN -> PowerRegistry.CABLE_MAIN.get();
        };
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putByte("Core", (byte) strand.core.ordinal());
        return stack;
    }
}
