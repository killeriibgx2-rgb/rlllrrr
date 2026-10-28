package com.zzynes.flydrone.DRONE;

import com.zzynes.flydrone.DRONE.entity.FpvDroneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FpvCameraAnchor {

    private static final TicketType<UUID> PLAYER_BODY_TICKET =
            TicketType.create("fly_drone_player_body", UUID::compareTo);
    private static final TicketType<UUID> PLAYER_RETURN_TICKET =
            TicketType.create("fly_drone_body_return", UUID::compareTo, 60);
    private static final TicketType<UUID> IMPACT_HOLD_TICKET =
            TicketType.create("fly_drone_impact_hold", UUID::compareTo);

    private static final int BODY_TICKET_RADIUS = 3;
    private static final int IMPACT_TICKET_RADIUS = 3;
    private static final long RETURN_PROTECTION_TICKS = 60L;

    private static final Map<UUID, FpvDroneEntity> ACTIVE_DRONES = new ConcurrentHashMap<>();
    private static final Map<UUID, ImpactHold> IMPACT_HOLDS = new ConcurrentHashMap<>();
    private static final Map<UUID, BodySnapshot> BODY_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, ReturnProtection> RETURN_PROTECTIONS = new ConcurrentHashMap<>();

    private FpvCameraAnchor() {
    }

    public static Entity getAnchorEntity(ServerPlayer player) {
        FpvDroneEntity drone = ACTIVE_DRONES.get(player.getUUID());
        if (drone != null && drone.isAlive() && drone.level() == player.level()) {
            return drone;
        }
        return player;
    }

    @Nullable
    private static ImpactHold getActiveImpactHold(ServerPlayer player) {
        ImpactHold hold = IMPACT_HOLDS.get(player.getUUID());
        if (hold != null && hold.level == player.level() && hold.level.getGameTime() <= hold.untilGameTime) {
            return hold;
        }
        return null;
    }

    public static SectionPos getAnchorSectionPos(ServerPlayer player) {
        Entity drone = getAnchorEntity(player);
        if (drone != player) {
            return SectionPos.of(drone);
        }
        ImpactHold hold = getActiveImpactHold(player);
        if (hold != null) {
            return hold.sectionPos;
        }
        return SectionPos.of(player);
    }

    public static int getAnchorBlockX(ServerPlayer player) {
        Entity drone = getAnchorEntity(player);
        if (drone != player) {
            return drone.getBlockX();
        }
        ImpactHold hold = getActiveImpactHold(player);
        if (hold != null) {
            return Mth.floor(hold.pos.x);
        }
        return player.getBlockX();
    }

    public static int getAnchorBlockZ(ServerPlayer player) {
        Entity drone = getAnchorEntity(player);
        if (drone != player) {
            return drone.getBlockZ();
        }
        ImpactHold hold = getActiveImpactHold(player);
        if (hold != null) {
            return Mth.floor(hold.pos.z);
        }
        return player.getBlockZ();
    }

    @Nullable
    public static Vec3 getAnchorPos(ServerPlayer player) {
        Entity drone = getAnchorEntity(player);
        if (drone != player) {
            return drone.position();
        }
        ImpactHold hold = getActiveImpactHold(player);
        if (hold != null) {
            return hold.pos;
        }
        return null;
    }

    public static boolean hasActiveAnchor(ServerPlayer player) {
        return getAnchorEntity(player) != player || getActiveImpactHold(player) != null;
    }

    public static void attachOrSync(ServerPlayer player, FpvDroneEntity drone) {
        if (player == null || drone == null || !drone.isAlive() || player.level() != drone.level()) {
            return;
        }
        ServerLevel sl = player.serverLevel();
        UUID playerId = player.getUUID();

        RETURN_PROTECTIONS.remove(playerId);
        releaseImpactHold(playerId);

        FpvDroneEntity prev = ACTIVE_DRONES.put(playerId, drone);
        ensurePlayerBodyTicket(player, sl, prev == null);

        player.fallDistance = 0f;
        player.setDeltaMovement(Vec3.ZERO);

        SectionPos droneSection = SectionPos.of(drone);
        if (prev != drone || !droneSection.equals(player.getLastSectionPos())) {
            sl.getChunkSource().move(player);
        }
    }

    public static void holdAtImpact(ServerPlayer player, Vec3 impactPos, int holdTicks) {
        if (player == null || impactPos == null || !(player.level() instanceof ServerLevel sl)) {
            return;
        }
        UUID playerId = player.getUUID();
        RETURN_PROTECTIONS.remove(playerId);
        ACTIVE_DRONES.remove(playerId);
        releaseImpactHold(playerId);

        ensurePlayerBodyTicket(player, sl, false);

        BlockPos blockPos = BlockPos.containing(impactPos);
        ChunkPos impactChunk = new ChunkPos(blockPos);
        SectionPos sectionPos = SectionPos.of(blockPos);

        sl.getChunkSource().addRegionTicket(IMPACT_HOLD_TICKET, impactChunk, IMPACT_TICKET_RADIUS, playerId, true);
        IMPACT_HOLDS.put(playerId, new ImpactHold(sl, impactPos, impactChunk, sectionPos, sl.getGameTime() + holdTicks));

        player.fallDistance = 0f;
        player.setDeltaMovement(Vec3.ZERO);

        if (!sectionPos.equals(player.getLastSectionPos())) {
            sl.getChunkSource().move(player);
        }
    }

    private static boolean releaseImpactHold(UUID playerId) {
        ImpactHold oldHold = IMPACT_HOLDS.remove(playerId);
        if (oldHold != null) {
            oldHold.level.getChunkSource().removeRegionTicket(IMPACT_HOLD_TICKET, oldHold.chunkPos, IMPACT_TICKET_RADIUS, playerId, true);
            return true;
        }
        return false;
    }

    public static void detach(ServerPlayer player) {
        detachInternal(player, true);
    }

    public static void detachImmediate(ServerPlayer player) {
        detachInternal(player, false);
    }

    private static void detachInternal(ServerPlayer player, boolean grantReturnProtection) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        FpvDroneEntity removed = ACTIVE_DRONES.remove(playerId);
        boolean removedHold = releaseImpactHold(playerId);
        BodySnapshot snapshot = BODY_SNAPSHOTS.remove(playerId);

        if (!grantReturnProtection) {
            RETURN_PROTECTIONS.remove(playerId);
        }

        if (removed == null && !removedHold && snapshot == null) {
            return;
        }

        if (player.level() instanceof ServerLevel sl) {
            if (grantReturnProtection) {
                ChunkPos currentChunk = player.chunkPosition();
                sl.getChunkSource().addRegionTicket(PLAYER_RETURN_TICKET, currentChunk, BODY_TICKET_RADIUS, playerId, true);
                if (snapshot != null && snapshot.level == sl && !snapshot.chunkPos.equals(currentChunk)) {
                    sl.getChunkSource().addRegionTicket(PLAYER_RETURN_TICKET, snapshot.chunkPos, BODY_TICKET_RADIUS, playerId, true);
                }
                Vec3 safePos = (snapshot != null && snapshot.level == sl) ? snapshot.pos : player.position();
                RETURN_PROTECTIONS.put(playerId, new ReturnProtection(sl, safePos, sl.getGameTime() + RETURN_PROTECTION_TICKS));
                player.fallDistance = 0f;
                player.setDeltaMovement(Vec3.ZERO);
            }

            if (removed != null || removedHold) {
                sl.getChunkSource().move(player);
            }
        }

        if (snapshot != null) {
            snapshot.level.getChunkSource().removeRegionTicket(PLAYER_BODY_TICKET, snapshot.chunkPos, BODY_TICKET_RADIUS, playerId, true);
        }
    }

    public static void detachByDrone(FpvDroneEntity drone) {
        if (drone == null || drone.level().isClientSide() || drone.level().getServer() == null) {
            return;
        }
        for (Map.Entry<UUID, FpvDroneEntity> entry : ACTIVE_DRONES.entrySet()) {
            if (entry.getValue() == drone || entry.getValue().getUUID().equals(drone.getUUID())) {
                UUID playerId = entry.getKey();
                ServerPlayer sp = drone.level().getServer().getPlayerList().getPlayer(playerId);
                if (sp != null) {
                    detach(sp);
                } else {
                    ACTIVE_DRONES.remove(playerId);
                    releaseImpactHold(playerId);
                    RETURN_PROTECTIONS.remove(playerId);
                    BodySnapshot snapshot = BODY_SNAPSHOTS.remove(playerId);
                    if (snapshot != null) {
                        snapshot.level.getChunkSource().removeRegionTicket(PLAYER_BODY_TICKET, snapshot.chunkPos, BODY_TICKET_RADIUS, playerId, true);
                    }
                }
            }
        }
    }

    public static void tickServerPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ImpactHold hold = IMPACT_HOLDS.get(playerId);
        if (hold != null && (hold.level != player.level() || player.level().getGameTime() > hold.untilGameTime)) {
            detach(player);
        }

        if (hasActiveAnchor(player)) {
            player.fallDistance = 0f;
            player.setDeltaMovement(Vec3.ZERO);
            BodySnapshot snapshot = BODY_SNAPSHOTS.get(playerId);
            if (snapshot != null && snapshot.level == player.level()
                    && snapshot.pos.y >= snapshot.level.getMinBuildHeight()
                    && (player.getY() < snapshot.level.getMinBuildHeight() || player.getY() < snapshot.pos.y - 1.5)) {
                restorePlayerBody(player, snapshot.pos);
            }
            return;
        }

        ReturnProtection prot = RETURN_PROTECTIONS.get(playerId);
        if (prot != null) {
            if (prot.level != player.level() || player.level().getGameTime() > prot.untilGameTime) {
                RETURN_PROTECTIONS.remove(playerId);
                return;
            }
            player.fallDistance = 0f;
            if (prot.pos.y >= prot.level.getMinBuildHeight() && player.getY() < prot.level.getMinBuildHeight()) {
                restorePlayerBody(player, prot.pos);
            }
        }
    }

    public static boolean handleVoidProtection(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (hasActiveAnchor(player)) {
            BodySnapshot snapshot = BODY_SNAPSHOTS.get(playerId);
            if (snapshot != null && snapshot.level == player.level() && snapshot.pos.y >= snapshot.level.getMinBuildHeight()) {
                restorePlayerBody(player, snapshot.pos);
                return true;
            }
        }
        ReturnProtection prot = RETURN_PROTECTIONS.get(playerId);
        if (prot != null && prot.level == player.level() && player.level().getGameTime() <= prot.untilGameTime) {
            if (prot.pos.y >= prot.level.getMinBuildHeight()) {
                restorePlayerBody(player, prot.pos);
                return true;
            }
        }
        return false;
    }

    private static void restorePlayerBody(ServerPlayer player, Vec3 pos) {
        player.fallDistance = 0f;
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(pos.x, pos.y, pos.z);
    }

    private static void ensurePlayerBodyTicket(ServerPlayer player, ServerLevel level, boolean freshAttach) {
        UUID playerId = player.getUUID();
        ChunkPos center = player.chunkPosition();
        BodySnapshot existing = BODY_SNAPSHOTS.get(playerId);
        if (existing != null && existing.level == level && existing.chunkPos.equals(center)) {
            return;
        }
        Vec3 safePos = (existing != null && !freshAttach && existing.level == level) ? existing.pos : player.position();
        if (existing != null) {
            existing.level.getChunkSource().removeRegionTicket(PLAYER_BODY_TICKET, existing.chunkPos, BODY_TICKET_RADIUS, playerId, true);
        }
        level.getChunkSource().addRegionTicket(PLAYER_BODY_TICKET, center, BODY_TICKET_RADIUS, playerId, true);
        BODY_SNAPSHOTS.put(playerId, new BodySnapshot(level, safePos, center));
    }

    private record ImpactHold(ServerLevel level, Vec3 pos, ChunkPos chunkPos, SectionPos sectionPos, long untilGameTime) {
    }

    private record BodySnapshot(ServerLevel level, Vec3 pos, ChunkPos chunkPos) {
    }

    private record ReturnProtection(ServerLevel level, Vec3 pos, long untilGameTime) {
    }
}
