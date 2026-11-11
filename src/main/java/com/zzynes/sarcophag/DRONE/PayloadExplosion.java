package com.zzynes.sarcophag.DRONE;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class PayloadExplosion {

    private PayloadExplosion() {
    }

    public static void detonate(Level level, Entity source, Vec3 position, float radius, Vec3 incomingDirection, boolean impact) {
        if (level.isClientSide()) {
            return;
        }
        Vec3 normal = incomingDirection.lengthSqr() > 1.0e-5 ? incomingDirection.normalize().scale(-1.0) : new Vec3(0.0, 1.0, 0.0);
        boolean mushroom = impact && hasClearBackPath(level, position, normal);
        if (level instanceof ServerLevel serverLevel) {
            BlastVisualPacket visuals = new BlastVisualPacket(position.x, position.y, position.z, radius, normal.x, normal.y, normal.z, mushroom);
            for (ServerPlayer viewer : serverLevel.players()) {
                if (viewer.position().distanceToSqr(position) <= 96.0 * 96.0) {
                    ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> viewer), visuals);
                }
            }
            applyConcussion(serverLevel, position);
        }
        level.explode(source, null, null, position.x, position.y, position.z, radius, false, Level.ExplosionInteraction.TNT);
    }

    private static boolean hasClearBackPath(Level level, Vec3 origin, Vec3 normal) {
        Vec3 start = origin.add(normal.scale(0.15));
        Vec3 end = start.add(normal.scale(7.0));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static void applyConcussion(ServerLevel level, Vec3 position) {
        List<ServerPlayer> players = level.players();
        for (ServerPlayer player : players) {
            if (player.position().distanceToSqr(position) <= 400.0) {
                ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new DroneConcussionPacket());
            }
        }
    }
}
