package com.zzynes.sarcophag.VVER;

import com.zzynes.sarcophag.NET.PacketUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BshuScrollPacket {
    private final BlockPos blockPos;
    private final int scroll;

    public BshuScrollPacket(BlockPos blockPos, int scroll) {
        this.blockPos = blockPos;
        this.scroll = scroll;
    }

    public static void encode(BshuScrollPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.blockPos);
        buf.writeVarInt(packet.scroll);
    }

    public static BshuScrollPacket decode(FriendlyByteBuf buf) {
        return new BshuScrollPacket(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(BshuScrollPacket packet, Supplier<NetworkEvent.Context> ctx) {
        PacketUtil.run(ctx, () -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(packet.blockPos)) > 400.0) {
                return;
            }
            BlockEntity be = player.serverLevel().getBlockEntity(packet.blockPos);
            if (be instanceof BshuBlockEntity bshu && bshu.isScreenPowered(BshuScreenType.TERTIARY)) {
                bshu.setParameterListScroll(Mth.clamp(packet.scroll, 0, 100000));
            }
        });
    }
}
