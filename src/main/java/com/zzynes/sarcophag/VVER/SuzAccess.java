package com.zzynes.sarcophag.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SuzAccess {

    private static final long GRANT_TICKS = 1200L;
    private static final Map<UUID, Grant> GRANTS = new ConcurrentHashMap<>();

    private SuzAccess() {
    }

    public static void grant(Player player, BlockPos pos) {
        GRANTS.put(player.getUUID(), new Grant(pos.immutable(), player.level().getGameTime() + GRANT_TICKS));
    }

    public static boolean has(Player player, BlockPos pos) {
        Grant grant = GRANTS.get(player.getUUID());
        if (grant == null || !grant.pos.equals(pos)) {
            return false;
        }
        long now = player.level().getGameTime();
        if (now > grant.expiresAt) {
            GRANTS.remove(player.getUUID());
            return false;
        }
        grant.expiresAt = now + GRANT_TICKS;
        return true;
    }

    public static void forget(Player player) {
        GRANTS.remove(player.getUUID());
    }

    private static final class Grant {
        private final BlockPos pos;
        private long expiresAt;

        private Grant(BlockPos pos, long expiresAt) {
            this.pos = pos;
            this.expiresAt = expiresAt;
        }
    }
}
