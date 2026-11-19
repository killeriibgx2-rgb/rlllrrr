package com.zzynes.sarcophag.POWER;

import com.zzynes.sarcophag.NET.PacketRateLimit;
import com.zzynes.sarcophag.NET.PacketUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

// Удар по кабелю: обычный режет прогон пополам, с шифтом снимает целиком.
public class CableCutPacket {

    private final int entityId;
    private final Vec3 point;
    private final boolean whole;

    public CableCutPacket(int entityId, Vec3 point, boolean whole) {
        this.entityId = entityId;
        this.point = point;
        this.whole = whole;
    }

    public static void encode(CableCutPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeDouble(packet.point.x);
        buf.writeDouble(packet.point.y);
        buf.writeDouble(packet.point.z);
        buf.writeBoolean(packet.whole);
    }

    public static CableCutPacket decode(FriendlyByteBuf buf) {
        return new CableCutPacket(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readBoolean());
    }

    public static void handle(CableCutPacket packet, Supplier<NetworkEvent.Context> ctx) {
        PacketUtil.run(ctx, () -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (!PacketRateLimit.allow(player, "cable_cut", 4)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(packet.entityId);
            if (!(entity instanceof CableRunEntity run)) {
                return;
            }
            if (player.distanceToSqr(run) > 100.0) {
                return;
            }
            if (packet.whole) {
                run.dropAsItems();
                run.discard();
                return;
            }
            List<CableRoute> parts = run.cutAt(packet.point);
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
        });
    }
}
