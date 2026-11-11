package com.zzynes.sarcophag.DRONE;

import com.zzynes.sarcophag.NET.PacketUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class DroneDisconnectPacket {

    private final UUID droneUUID;

    public DroneDisconnectPacket(UUID droneUUID) {
        this.droneUUID = droneUUID;
    }

    public static void encode(DroneDisconnectPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.droneUUID);
    }

    public static DroneDisconnectPacket decode(FriendlyByteBuf buf) {
        return new DroneDisconnectPacket(buf.readUUID());
    }

    public static void handle(DroneDisconnectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        PacketUtil.run(ctx, () -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DroneInputHandler.forceDisconnectClient(packet.droneUUID));
            }
        });
    }
}
