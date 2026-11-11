package com.zzynes.sarcophag.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SuzGroupPacket {
    private final BlockPos basePos;
    private final int group;

    public SuzGroupPacket(BlockPos basePos, int group) {
        this.basePos = basePos;
        this.group = group;
    }

    public static void encode(SuzGroupPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.basePos);
        buf.writeByte(packet.group);
    }

    public static SuzGroupPacket decode(FriendlyByteBuf buf) {
        return new SuzGroupPacket(buf.readBlockPos(), buf.readByte());
    }

    public static void handle(SuzGroupPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(packet.basePos)) > 256.0) {
                return;
            }
            BlockEntity be = player.serverLevel().getBlockEntity(packet.basePos);
            if (be instanceof SuzBlockEntity suz) {
                int group = Mth.clamp(packet.group, 0, 8);
                suz.setGroup(group);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
