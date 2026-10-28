package com.zzynes.flydrone.DRONE;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class DroneImpactCamPacket {

    private final UUID droneUUID;
    private final int droneEntityId;
    private final double impactX;
    private final double impactY;
    private final double impactZ;

    public DroneImpactCamPacket(UUID droneUUID, int droneEntityId, double impactX, double impactY, double impactZ) {
        this.droneUUID = droneUUID;
        this.droneEntityId = droneEntityId;
        this.impactX = impactX;
        this.impactY = impactY;
        this.impactZ = impactZ;
    }

    public static void encode(DroneImpactCamPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.droneUUID);
        buf.writeVarInt(packet.droneEntityId);
        buf.writeDouble(packet.impactX);
        buf.writeDouble(packet.impactY);
        buf.writeDouble(packet.impactZ);
    }

    public static DroneImpactCamPacket decode(FriendlyByteBuf buf) {
        return new DroneImpactCamPacket(
                buf.readUUID(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble()
        );
    }

    public static void handle(DroneImpactCamPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FpvKillcamManager.triggerKillcam(
                        packet.droneUUID,
                        packet.droneEntityId,
                        packet.impactX,
                        packet.impactY,
                        packet.impactZ
                ));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
