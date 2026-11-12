package com.zzynes.sarcophag.NET;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketRateLimit {

    private static final Map<UUID, Counters> PLAYERS = new ConcurrentHashMap<>();
    private static final int CLEANUP_THRESHOLD = 512;
    private static final long STALE_TICKS = 40L;

    private PacketRateLimit() {
    }

    public static boolean allow(ServerPlayer player, String channel, int perTick) {
        long now = player.serverLevel().getGameTime();
        if (PLAYERS.size() > CLEANUP_THRESHOLD) {
            PLAYERS.values().removeIf(counters -> now - counters.lastTick > STALE_TICKS);
        }
        Counters counters = PLAYERS.computeIfAbsent(player.getUUID(), k -> new Counters());
        return counters.accept(channel, now, perTick);
    }

    public static void forget(ServerPlayer player) {
        PLAYERS.remove(player.getUUID());
    }

    private static final class Counters {
        private final Map<String, int[]> slots = new HashMap<>(8);
        private long lastTick;

        boolean accept(String channel, long now, int perTick) {
            this.lastTick = now;
            int[] slot = this.slots.get(channel);
            if (slot == null) {
                slot = new int[]{-1, 0};
                this.slots.put(channel, slot);
            }
            if (slot[0] != (int) now) {
                slot[0] = (int) now;
                slot[1] = 0;
            }
            return ++slot[1] <= perTick;
        }
    }
}
