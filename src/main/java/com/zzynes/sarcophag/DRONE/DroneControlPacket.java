package com.zzynes.sarcophag.DRONE;

import com.zzynes.sarcophag.NET.PacketRateLimit;
import com.zzynes.sarcophag.NET.PacketUtil;
import com.zzynes.sarcophag.DRONE.entity.FpvDroneEntity;
import com.zzynes.sarcophag.DRONE.init.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class DroneControlPacket {
    // предел пульта, дальше рвём связь и глушим дрон
    public static final double MAX_RANGE = 2100.0;
    // за 300 блоков до предела начинаем предупреждать пилота
    public static final double WARNING_LEAD_BLOCKS = 300.0;
    public static final double WARNING_START_RANGE = MAX_RANGE - WARNING_LEAD_BLOCKS;

    private final UUID droneUUID;
    private final float throttle;
    private final float pitch;
    private final float roll;
    private final float yaw;
    private final int renderDistance;
    private final boolean activeFpv;

    public DroneControlPacket(UUID droneUUID, float throttle, float pitch, float roll, float yaw, int renderDistance) {
        this(droneUUID, throttle, pitch, roll, yaw, renderDistance, true);
    }

    public DroneControlPacket(UUID droneUUID, float throttle, float pitch, float roll, float yaw, int renderDistance, boolean activeFpv) {
        this.droneUUID = droneUUID;
        this.throttle = throttle;
        this.pitch = pitch;
        this.roll = roll;
        this.yaw = yaw;
        this.renderDistance = renderDistance;
        this.activeFpv = activeFpv;
    }

    public static void encode(DroneControlPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.droneUUID);
        buf.writeFloat(packet.throttle);
        buf.writeFloat(packet.pitch);
        buf.writeFloat(packet.roll);
        buf.writeFloat(packet.yaw);
        buf.writeVarInt(packet.renderDistance);
        buf.writeBoolean(packet.activeFpv);
    }

    public static DroneControlPacket decode(FriendlyByteBuf buf) {
        return new DroneControlPacket(
                buf.readUUID(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readBoolean()
        );
    }

    public static void handle(DroneControlPacket packet, Supplier<NetworkEvent.Context> ctx) {
        PacketUtil.run(ctx, () -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!PacketRateLimit.allow(player, "drone_control", 4)) {
                return;
            }

            Entity entity = player.serverLevel().getEntity(packet.droneUUID);

            if (!packet.activeFpv) {
                if (entity instanceof FpvDroneEntity drone) {
                    drone.setThrottle(0f);
                    drone.pauseFpvSession();
                }
                FpvCameraAnchor.detach(player);
                return;
            }

            if (!hasConnectedRemoteInHand(player, packet.droneUUID)) {
                FpvCameraAnchor.detach(player);
                return;
            }

            if (!(entity instanceof FpvDroneEntity drone) || !drone.isAlive()) {
                FpvCameraAnchor.detach(player);
                return;
            }

            if (player.position().distanceTo(drone.position()) >= MAX_RANGE) {
                drone.setController(player.getUUID());
                drone.forceDisconnect(DroneText.LINK_LOST);
                return;
            }

            if (!drone.canAcceptControlFrom(player)) {
                drone.setController(player.getUUID());
                drone.forceDisconnect();
                FpvCameraAnchor.detach(player);
                return;
            }

            drone.setController(player.getUUID());
            drone.setRenderDistance(packet.renderDistance);
            drone.setThrottle(packet.throttle);
            drone.addPitchRate(packet.pitch);
            drone.addRollRate(packet.roll);
            drone.addYawRate(packet.yaw);
            FpvCameraAnchor.attachOrSync(player, drone);
        });
    }

    private static boolean hasConnectedRemoteInHand(ServerPlayer player, UUID droneUUID) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack st = player.getItemInHand(hand);
            if (st.getItem() == ModItems.REMOTE_CONNECTED.get()
                    && st.hasTag()
                    && st.getTag() != null
                    && st.getTag().hasUUID("DroneUUID")
                    && droneUUID.equals(st.getTag().getUUID("DroneUUID"))) {
                return true;
            }
        }
        return false;
    }
}
