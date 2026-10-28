package com.zzynes.flydrone.DRONE;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RpgImpactPreviewPacket {

    private final List<Vec3> path;

    public RpgImpactPreviewPacket(List<Vec3> path) {
        this.path = path;
    }

    public static void encode(RpgImpactPreviewPacket packet, FriendlyByteBuf buf) {
        int count = Math.min(240, packet.path.size());
        buf.writeVarInt(count);
        int start = packet.path.size() - count;
        for (int i = start; i < packet.path.size(); i++) {
            Vec3 point = packet.path.get(i);
            buf.writeDouble(point.x);
            buf.writeDouble(point.y);
            buf.writeDouble(point.z);
        }
    }

    public static RpgImpactPreviewPacket decode(FriendlyByteBuf buf) {
        int count = Math.max(0, Math.min(240, buf.readVarInt()));
        List<Vec3> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        return new RpgImpactPreviewPacket(points);
    }

    public static void handle(RpgImpactPreviewPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ProjectileImpactPreview.start(packet.path));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
