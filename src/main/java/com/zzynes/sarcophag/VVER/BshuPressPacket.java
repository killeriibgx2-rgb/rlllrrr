package com.zzynes.sarcophag.VVER;

import com.zzynes.sarcophag.NET.PacketRateLimit;
import com.zzynes.sarcophag.NET.PacketUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BshuPressPacket {
    private final BlockPos blockPos;
    private final int controlOrdinal;
    private final boolean pairOn;
    private final Vec3 hitPos;

    public BshuPressPacket(BlockPos blockPos, BshuControl control, boolean pairOn, Vec3 hitPos) {
        this.blockPos = blockPos;
        this.controlOrdinal = control.ordinal();
        this.pairOn = pairOn;
        this.hitPos = hitPos;
    }

    private BshuPressPacket(BlockPos blockPos, int controlOrdinal, boolean pairOn, Vec3 hitPos) {
        this.blockPos = blockPos;
        this.controlOrdinal = controlOrdinal;
        this.pairOn = pairOn;
        this.hitPos = hitPos;
    }

    public static void encode(BshuPressPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.blockPos);
        buf.writeVarInt(packet.controlOrdinal);
        buf.writeBoolean(packet.pairOn);
        buf.writeDouble(packet.hitPos.x);
        buf.writeDouble(packet.hitPos.y);
        buf.writeDouble(packet.hitPos.z);
    }

    public static BshuPressPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int control = buf.readVarInt();
        boolean pairOn = buf.readBoolean();
        Vec3 hit = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new BshuPressPacket(pos, control, pairOn, hit);
    }

    public static void handle(BshuPressPacket packet, Supplier<NetworkEvent.Context> ctx) {
        PacketUtil.run(ctx, () -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || packet.controlOrdinal < 0 || packet.controlOrdinal >= BshuControl.values().length) {
                return;
            }
            if (!PacketRateLimit.allow(player, "bshu_press", 6)) {
                return;
            }
            Vec3 eye = player.getEyePosition();
            double distance = eye.distanceTo(packet.hitPos);
            if (distance > 14.0 || player.position().distanceToSqr(Vec3.atCenterOf(packet.blockPos)) > 400.0) {
                return;
            }
            BlockHitResult clip = player.serverLevel().clip(new ClipContext(
                    eye,
                    packet.hitPos,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));
            if (clip.getType() == HitResult.Type.BLOCK && !clip.getBlockPos().equals(packet.blockPos)
                    && eye.distanceTo(clip.getLocation()) < distance - 0.08) {
                return;
            }
            BlockEntity be = player.serverLevel().getBlockEntity(packet.blockPos);
            if (be instanceof BshuBlockEntity bshu) {
                bshu.press(BshuControl.values()[packet.controlOrdinal], packet.pairOn);
            }
        });
    }
}
