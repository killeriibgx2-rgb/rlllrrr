package com.zzynes.flydrone.VVER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class VverNetwork {
    private static final String PROTOCOL_VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FlyDroneMod.MOD_ID, "vver"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(SuzGroupPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SuzGroupPacket::encode)
                .decoder(SuzGroupPacket::decode)
                .consumerMainThread(SuzGroupPacket::handle)
                .add();
        CHANNEL.messageBuilder(BshuPressPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BshuPressPacket::encode)
                .decoder(BshuPressPacket::decode)
                .consumerMainThread(BshuPressPacket::handle)
                .add();
        CHANNEL.messageBuilder(BshuInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BshuInputPacket::encode)
                .decoder(BshuInputPacket::decode)
                .consumerMainThread(BshuInputPacket::handle)
                .add();
        CHANNEL.messageBuilder(BshuLinkPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BshuLinkPacket::encode)
                .decoder(BshuLinkPacket::decode)
                .consumerMainThread(BshuLinkPacket::handle)
                .add();
        CHANNEL.messageBuilder(BshuPressFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BshuPressFxPacket::encode)
                .decoder(BshuPressFxPacket::decode)
                .consumerMainThread(BshuPressFxPacket::handle)
                .add();
        CHANNEL.messageBuilder(BshuCoreGroupPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BshuCoreGroupPacket::encode)
                .decoder(BshuCoreGroupPacket::decode)
                .consumerMainThread(BshuCoreGroupPacket::handle)
                .add();
        CHANNEL.messageBuilder(BshuScrollPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BshuScrollPacket::encode)
                .decoder(BshuScrollPacket::decode)
                .consumerMainThread(BshuScrollPacket::handle)
                .add();
    }

    public static void broadcastPress(BshuBlockEntity blockEntity, BshuControl control) {
        if (!(blockEntity.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(blockEntity.getBlockPos())),
                new BshuPressFxPacket(blockEntity.getBlockPos(), control)
        );
    }
}
