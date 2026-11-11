package com.zzynes.sarcophag.DRONE;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import com.zzynes.sarcophag.DRONE.client.BlastParticlesClient;

import java.util.function.Supplier;

public class BlastVisualPacket {

    private final double x;
    private final double y;
    private final double z;
    private final float radius;
    private final double normalX;
    private final double normalY;
    private final double normalZ;
    private final boolean mushroom;

    public BlastVisualPacket(double x, double y, double z, float radius, double normalX, double normalY, double normalZ, boolean mushroom) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
        this.mushroom = mushroom;
    }

    public static void encode(BlastVisualPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
        buf.writeFloat(packet.radius);
        buf.writeDouble(packet.normalX);
        buf.writeDouble(packet.normalY);
        buf.writeDouble(packet.normalZ);
        buf.writeBoolean(packet.mushroom);
    }

    public static BlastVisualPacket decode(FriendlyByteBuf buf) {
        return new BlastVisualPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }

    public static void handle(BlastVisualPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BlastParticlesClient.receive(
                        packet.x, packet.y, packet.z, packet.radius,
                        packet.normalX, packet.normalY, packet.normalZ, packet.mushroom
                ));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
