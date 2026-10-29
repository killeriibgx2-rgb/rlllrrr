package com.zzynes.flydrone.VVER;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
                String text = group == 0 ? "СУЗ: группа не назначена" : "СУЗ: назначена группа " + group;
                player.displayClientMessage(Component.literal(ChatFormatting.GREEN + text), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
