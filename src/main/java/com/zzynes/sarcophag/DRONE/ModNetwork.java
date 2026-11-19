package com.zzynes.sarcophag.DRONE;

import com.zzynes.sarcophag.POWER.CablePlacePacket;
import com.zzynes.sarcophag.VVER.SuzPercentPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SarcophagMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(DroneControlPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DroneControlPacket::encode)
                .decoder(DroneControlPacket::decode)
                .consumerMainThread(DroneControlPacket::handle)
                .add();

        CHANNEL.messageBuilder(DroneDisconnectPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DroneDisconnectPacket::encode)
                .decoder(DroneDisconnectPacket::decode)
                .consumerMainThread(DroneDisconnectPacket::handle)
                .add();

        CHANNEL.messageBuilder(DronePayloadActionPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DronePayloadActionPacket::encode)
                .decoder(DronePayloadActionPacket::decode)
                .consumerMainThread(DronePayloadActionPacket::handle)
                .add();

        CHANNEL.messageBuilder(RpgImpactPreviewPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RpgImpactPreviewPacket::encode)
                .decoder(RpgImpactPreviewPacket::decode)
                .consumerMainThread(RpgImpactPreviewPacket::handle)
                .add();

        CHANNEL.messageBuilder(BlastVisualPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlastVisualPacket::encode)
                .decoder(BlastVisualPacket::decode)
                .consumerMainThread(BlastVisualPacket::handle)
                .add();

        CHANNEL.messageBuilder(DroneConcussionPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DroneConcussionPacket::encode)
                .decoder(DroneConcussionPacket::decode)
                .consumerMainThread(DroneConcussionPacket::handle)
                .add();

        CHANNEL.messageBuilder(DroneImpactCamPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DroneImpactCamPacket::encode)
                .decoder(DroneImpactCamPacket::decode)
                .consumerMainThread(DroneImpactCamPacket::handle)
                .add();

        CHANNEL.messageBuilder(SuzPercentPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SuzPercentPacket::encode)
                .decoder(SuzPercentPacket::decode)
                .consumerMainThread(SuzPercentPacket::handle)
                .add();

        CHANNEL.messageBuilder(CablePlacePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CablePlacePacket::encode)
                .decoder(CablePlacePacket::decode)
                .consumerMainThread(CablePlacePacket::handle)
                .add();

    }
}
