package com.zzynes.sarcophag.POWER;

import com.zzynes.sarcophag.NET.PacketRateLimit;
import com.zzynes.sarcophag.NET.PacketUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

// Клиент присылает только две точки крепления. Трассу сервер считает сам,
// иначе её можно будет проложить сквозь стену.
public class CablePlacePacket {

    private final Anchor start;
    private final Anchor end;
    private final int tier;
    private final int core;

    public CablePlacePacket(Anchor start, Anchor end, int tier, int core) {
        this.start = start;
        this.end = end;
        this.tier = tier;
        this.core = core;
    }

    public static void encode(CablePlacePacket packet, FriendlyByteBuf buf) {
        packet.start.write(buf);
        packet.end.write(buf);
        buf.writeByte(packet.tier);
        buf.writeByte(packet.core);
    }

    public static CablePlacePacket decode(FriendlyByteBuf buf) {
        return new CablePlacePacket(Anchor.read(buf), Anchor.read(buf), buf.readByte(), buf.readByte());
    }

    public static void handle(CablePlacePacket packet, Supplier<NetworkEvent.Context> ctx) {
        PacketUtil.run(ctx, () -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (!PacketRateLimit.allow(player, "cable_place", 4)) {
                return;
            }
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof CableItem item) || item.tier().ordinal() != packet.tier) {
                return;
            }
            Vec3 from = packet.start.point();
            Vec3 to = packet.end.point();
            if (player.position().distanceToSqr(from) > 64.0 || player.position().distanceToSqr(to) > 64.0) {
                return;
            }
            ServerLevel level = player.serverLevel();
            Optional<CableRoute> route = CableRouter.route(level, packet.start, packet.end, item.tier());
            if (route.isEmpty()) {
                return;
            }
            double clearance = item.tier().radius;
            for (CableRunEntity other : level.getEntitiesOfClass(CableRunEntity.class, route.get().bounds().inflate(2.0))) {
                double limit = clearance + other.tier().radius - 0.002;
                if (route.get().distanceTo(other.route()) < limit) {
                    return;
                }
            }
            CableRunEntity run = new CableRunEntity(PowerRegistry.CABLE_RUN.get(), level);
            run.configure(item.tier(), CableCore.byIndex(packet.core), packet.start, packet.end, route.get());
            level.addFreshEntity(run);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        });
    }
}
