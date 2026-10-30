package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BshuInputPacket {
    private final BlockPos blockPos;
    private final int screenOrdinal;
    private final int field;
    private final int value;

    public BshuInputPacket(BlockPos blockPos, BshuScreenType screen, int field, int value) {
        this.blockPos = blockPos;
        this.screenOrdinal = screen.ordinal();
        this.field = field;
        this.value = value;
    }

    private BshuInputPacket(BlockPos blockPos, int screenOrdinal, int field, int value) {
        this.blockPos = blockPos;
        this.screenOrdinal = screenOrdinal;
        this.field = field;
        this.value = value;
    }

    public static void encode(BshuInputPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.blockPos);
        buf.writeByte(packet.screenOrdinal);
        buf.writeByte(packet.field);
        buf.writeShort(packet.value);
    }

    public static BshuInputPacket decode(FriendlyByteBuf buf) {
        return new BshuInputPacket(buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readShort());
    }

    public static void handle(BshuInputPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || packet.screenOrdinal < 0 || packet.screenOrdinal >= BshuScreenType.values().length) {
                return;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(packet.blockPos)) > 400.0) {
                return;
            }
            BlockEntity be = player.serverLevel().getBlockEntity(packet.blockPos);
            if (!(be instanceof BshuBlockEntity bshu)) {
                return;
            }
            int value = Mth.clamp(packet.value, 0, 100);
            BshuScreenType screen = BshuScreenType.values()[packet.screenOrdinal];
            if (screen == BshuScreenType.GCN) {
                bshu.applyGcnInput(packet.field, value);
            } else if (screen == BshuScreenType.CONTROL) {
                bshu.applyRodInput(value);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
