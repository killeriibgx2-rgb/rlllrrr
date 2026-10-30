package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BshuCoreGroupPacket {
    private final BlockPos bshuPos;
    private final BlockPos suzPos;
    private final int group;

    public BshuCoreGroupPacket(BlockPos bshuPos, BlockPos suzPos, int group) {
        this.bshuPos = bshuPos;
        this.suzPos = suzPos;
        this.group = group;
    }

    public static void encode(BshuCoreGroupPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.bshuPos);
        buf.writeBlockPos(packet.suzPos);
        buf.writeByte(packet.group);
    }

    public static BshuCoreGroupPacket decode(FriendlyByteBuf buf) {
        return new BshuCoreGroupPacket(buf.readBlockPos(), buf.readBlockPos(), buf.readByte());
    }

    public static void handle(BshuCoreGroupPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(packet.bshuPos)) > 400.0) {
                return;
            }
            BlockEntity bshuBe = player.serverLevel().getBlockEntity(packet.bshuPos);
            if (!(bshuBe instanceof BshuBlockEntity bshu) || !bshu.isScreenPowered(BshuScreenType.CORE) || !bshu.isLinked()) {
                return;
            }
            bshu.scanCore();
            if (!bshu.getSuzPositions().contains(packet.suzPos)) {
                return;
            }
            BlockEntity suzBe = player.serverLevel().getBlockEntity(packet.suzPos);
            if (suzBe instanceof SuzBlockEntity suz) {
                int group = Mth.clamp(packet.group, 0, 8);
                suz.setGroup(group);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
