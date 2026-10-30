package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SuzPercentPacket {

    private final BlockPos basePos;
    private final float percent;

    public SuzPercentPacket(BlockPos basePos, float percent) {
        this.basePos = basePos;
        this.percent = percent;
    }

    public static void encode(SuzPercentPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.basePos);
        buf.writeFloat(packet.percent);
    }

    public static SuzPercentPacket decode(FriendlyByteBuf buf) {
        return new SuzPercentPacket(buf.readBlockPos(), buf.readFloat());
    }

    public static void handle(SuzPercentPacket packet, Supplier<NetworkEvent.Context> ctx) {
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
                float clamped = Mth.clamp(packet.percent, 0.0f, 100.0f);
                suz.setTargetPercent(clamped);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
