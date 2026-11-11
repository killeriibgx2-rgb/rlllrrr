package com.zzynes.sarcophag.DRONE;

import com.zzynes.sarcophag.DRONE.entity.FpvDroneEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class DronePayloadActionPacket {

    private final UUID droneUuid;
    private final int action;

    public DronePayloadActionPacket(UUID droneUuid, int action) {
        this.droneUuid = droneUuid;
        this.action = action;
    }

    public static void encode(DronePayloadActionPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.droneUuid);
        buf.writeVarInt(packet.action);
    }

    public static DronePayloadActionPacket decode(FriendlyByteBuf buf) {
        return new DronePayloadActionPacket(buf.readUUID(), buf.readVarInt());
    }

    public static void handle(DronePayloadActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || packet.action < 0 || packet.action > 1) {
                return;
            }
            Entity entity = player.serverLevel().getEntity(packet.droneUuid);
            if (!(entity instanceof FpvDroneEntity drone)
                    || !drone.isAlive()
                    || !player.getUUID().equals(drone.getControllerUUID())
                    || player.position().distanceToSqr(drone.position()) > DroneControlPacket.MAX_RANGE * DroneControlPacket.MAX_RANGE
                    || !hasConnectedRemote(player, packet.droneUuid)) {
                return;
            }
            if (packet.action == 0) {
                drone.releaseRpgProjectile(player);
            } else {
                drone.selfDestruct(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean hasConnectedRemote(ServerPlayer player, UUID droneUuid) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == com.zzynes.sarcophag.DRONE.init.ModItems.REMOTE_CONNECTED.get()
                    && stack.hasTag()
                    && stack.getTag() != null
                    && stack.getTag().hasUUID("DroneUUID")
                    && droneUuid.equals(stack.getTag().getUUID("DroneUUID"))) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() == com.zzynes.sarcophag.DRONE.init.ModItems.REMOTE_CONNECTED.get()
                    && stack.hasTag()
                    && stack.getTag() != null
                    && stack.getTag().hasUUID("DroneUUID")
                    && droneUuid.equals(stack.getTag().getUUID("DroneUUID"))) {
                return true;
            }
        }
        return false;
    }
}
