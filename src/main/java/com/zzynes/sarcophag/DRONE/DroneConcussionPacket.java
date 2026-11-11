package com.zzynes.sarcophag.DRONE;

import com.zzynes.sarcophag.NET.PacketUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DroneConcussionPacket {

    public static void encode(DroneConcussionPacket packet, FriendlyByteBuf buf) {
    }

    public static DroneConcussionPacket decode(FriendlyByteBuf buf) {
        return new DroneConcussionPacket();
    }

    public static void handle(DroneConcussionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        PacketUtil.run(ctx, () -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> FpvConcussionClient::trigger);
            }
        });
    }
}
