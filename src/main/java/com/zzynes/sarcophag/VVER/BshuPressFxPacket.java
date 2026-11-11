package com.zzynes.sarcophag.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BshuPressFxPacket {
    private final BlockPos blockPos;
    private final int controlOrdinal;

    public BshuPressFxPacket(BlockPos blockPos, BshuControl control) {
        this.blockPos = blockPos;
        this.controlOrdinal = control.ordinal();
    }

    private BshuPressFxPacket(BlockPos blockPos, int controlOrdinal) {
        this.blockPos = blockPos;
        this.controlOrdinal = controlOrdinal;
    }

    public static void encode(BshuPressFxPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.blockPos);
        buf.writeVarInt(packet.controlOrdinal);
    }

    public static BshuPressFxPacket decode(FriendlyByteBuf buf) {
        return new BshuPressFxPacket(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(BshuPressFxPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (packet.controlOrdinal >= 0 && packet.controlOrdinal < BshuControl.values().length) {
                BshuClientState.startPress(packet.blockPos, BshuControl.values()[packet.controlOrdinal]);
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}
