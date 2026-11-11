package com.zzynes.sarcophag.VVER;

import com.zzynes.sarcophag.NET.PacketUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BshuLinkPacket {
    private final BlockPos blockPos;

    public BshuLinkPacket(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public static void encode(BshuLinkPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.blockPos);
    }

    public static BshuLinkPacket decode(FriendlyByteBuf buf) {
        return new BshuLinkPacket(buf.readBlockPos());
    }

    public static void handle(BshuLinkPacket packet, Supplier<NetworkEvent.Context> ctx) {
        PacketUtil.run(ctx, () -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.STICK)) {
                return;
            }
            if (player.position().distanceToSqr(packet.blockPos.getX() + 0.5, packet.blockPos.getY() + 0.5, packet.blockPos.getZ() + 0.5) > 400.0) {
                return;
            }
            VverEvents.linkFromHeldStick(player, packet.blockPos);
        });
    }
}
