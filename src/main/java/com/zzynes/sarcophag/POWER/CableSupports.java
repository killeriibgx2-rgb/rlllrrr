package com.zzynes.sarcophag.POWER;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Крепление держится за блок. Убрали блок или рядом рвануло, кабель больше не висит.
@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID)
public final class CableSupports {

    private CableSupports() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        List<CableRunEntity> runs = level.getEntitiesOfClass(CableRunEntity.class, new AABB(pos).inflate(2.0));
        for (CableRunEntity run : runs) {
            if (!run.isSupported()) {
                run.dropAsItems();
                run.discard();
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        List<BlockPos> blocks = event.getAffectedBlocks();
        if (blocks.isEmpty()) {
            return;
        }
        Set<BlockPos> touched = new HashSet<>(blocks);
        AABB area = null;
        for (BlockPos pos : blocks) {
            AABB single = new AABB(pos);
            area = area == null ? single : area.minmax(single);
        }
        List<CableRunEntity> runs = level.getEntitiesOfClass(CableRunEntity.class, area.inflate(2.0));
        for (CableRunEntity run : runs) {
            Vec3 hit = firstPointInside(run.route(), touched);
            if (hit == null) {
                continue;
            }
            List<CableRoute> parts = run.cutAt(hit);
            CableTier tier = run.tier();
            CableCore core = run.core();
            Anchor start = run.start();
            Anchor end = run.end();
            run.discard();
            for (CableRoute part : parts) {
                CableRunEntity piece = new CableRunEntity(PowerRegistry.CABLE_RUN.get(), level);
                piece.configure(tier, core, start, end, part);
                level.addFreshEntity(piece);
            }
        }
    }

    private static Vec3 firstPointInside(CableRoute route, Set<BlockPos> touched) {
        for (int i = 0; i + 1 < route.size(); i++) {
            Vec3 a = route.pointAt(i);
            Vec3 b = route.pointAt(i + 1);
            int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.5));
            for (int s = 0; s <= steps; s++) {
                Vec3 point = a.lerp(b, s / (double) steps);
                if (touched.contains(BlockPos.containing(point.x, point.y, point.z))) {
                    return point;
                }
            }
        }
        return null;
    }
}
