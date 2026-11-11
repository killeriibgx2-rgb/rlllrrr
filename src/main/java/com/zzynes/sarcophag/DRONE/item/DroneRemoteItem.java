package com.zzynes.sarcophag.DRONE.item;

import com.zzynes.sarcophag.DRONE.DroneText;
import com.zzynes.sarcophag.DRONE.DroneControlPacket;
import com.zzynes.sarcophag.DRONE.DroneDisconnectPacket;
import com.zzynes.sarcophag.DRONE.FpvCameraAnchor;
import com.zzynes.sarcophag.DRONE.ModNetwork;
import com.zzynes.sarcophag.DRONE.entity.FpvDroneEntity;
import com.zzynes.sarcophag.DRONE.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class DroneRemoteItem extends Item {

    private static final String TAG_DRONE_UUID = "DroneUUID";
    private static final String TAG_CHUNK_X = "DroneChunkX";
    private static final String TAG_CHUNK_Z = "DroneChunkZ";

    private final boolean connected;

    public DroneRemoteItem(Properties properties, boolean connected) {
        super(properties);
        this.connected = connected;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof FpvDroneEntity drone && !connected) {
            ItemStack newStack = new ItemStack(ModItems.REMOTE_CONNECTED.get());
            CompoundTag newTag = newStack.getOrCreateTag();
            newTag.putUUID(TAG_DRONE_UUID, drone.getUUID());
            newTag.putInt(TAG_CHUNK_X, drone.chunkPosition().x);
            newTag.putInt(TAG_CHUNK_Z, drone.chunkPosition().z);
            player.setItemInHand(hand, newStack);

            player.displayClientMessage(Component.literal(ChatFormatting.GREEN + "Пульт подключён к дрону!"), true);
            return InteractionResult.sidedSuccess(player.level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!connected) {
            player.displayClientMessage(Component.literal(ChatFormatting.RED + "Сначала привяжи пульт к дрону (ПКМ по дрону)"), true);
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(TAG_DRONE_UUID)) {
            UUID droneUUID = tag.getUUID(TAG_DRONE_UUID);
            FpvDroneEntity drone = findDroneServer(serverLevel, droneUUID);

            if (drone == null) {
                if (isLastChunkLoaded(serverLevel, tag)) {
                    disconnectHand(serverPlayer, hand, droneUUID, DroneText.DRONE_LOST);
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
                player.displayClientMessage(Component.literal(ChatFormatting.YELLOW + "Дрон вне прогруженных чанков"), true);
                return InteractionResultHolder.pass(stack);
            }

            if (!drone.isAlive()) {
                disconnectHand(serverPlayer, hand, droneUUID, DroneText.DRONE_LOST);
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }

            if (player.position().distanceTo(drone.position()) >= DroneControlPacket.MAX_RANGE) {
                drone.forceDisconnect(DroneText.LINK_LOST);
                disconnectHand(serverPlayer, hand, droneUUID, DroneText.LINK_LOST);
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }

            updateDroneChunk(tag, drone);
            player.displayClientMessage(Component.literal(ChatFormatting.YELLOW + "Сброс наряда невозможен - груз не установлен"), true);
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!connected || level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) return;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(TAG_DRONE_UUID)) return;

        UUID droneUUID = tag.getUUID(TAG_DRONE_UUID);
        FpvDroneEntity drone = findDroneServer(serverLevel, droneUUID);

        if (drone != null) {
            if (!drone.isAlive()) {
                disconnectSlot(player, stack, slot, droneUUID, DroneText.DRONE_LOST);
                return;
            }
            if (player.position().distanceTo(drone.position()) >= DroneControlPacket.MAX_RANGE) {
                drone.forceDisconnect(DroneText.LINK_LOST);
                disconnectSlot(player, stack, slot, droneUUID, DroneText.LINK_LOST);
                return;
            }
            updateDroneChunk(tag, drone);
        } else if (selected && isLastChunkLoaded(serverLevel, tag)) {
            disconnectSlot(player, stack, slot, droneUUID, DroneText.DRONE_LOST);
        }
    }

    private static void updateDroneChunk(CompoundTag tag, FpvDroneEntity drone) {
        int cx = drone.chunkPosition().x;
        int cz = drone.chunkPosition().z;
        if (!tag.contains(TAG_CHUNK_X) || tag.getInt(TAG_CHUNK_X) != cx || tag.getInt(TAG_CHUNK_Z) != cz) {
            tag.putInt(TAG_CHUNK_X, cx);
            tag.putInt(TAG_CHUNK_Z, cz);
        }
    }

    private static boolean isLastChunkLoaded(ServerLevel level, CompoundTag tag) {
        if (!tag.contains(TAG_CHUNK_X) || !tag.contains(TAG_CHUNK_Z)) {
            return true;
        }
        return level.hasChunk(tag.getInt(TAG_CHUNK_X), tag.getInt(TAG_CHUNK_Z));
    }

    private void disconnectHand(ServerPlayer player, InteractionHand hand, UUID droneUUID, String message) {
        FpvCameraAnchor.detach(player);
        player.setItemInHand(hand, new ItemStack(ModItems.REMOTE_DISCONNECTED.get()));
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DroneDisconnectPacket(droneUUID));
        player.displayClientMessage(Component.literal(ChatFormatting.RED + message), true);
    }

    private void disconnectSlot(ServerPlayer player, ItemStack targetStack, int slot, UUID droneUUID, String message) {
        FpvCameraAnchor.detach(player);
        ItemStack disconnected = new ItemStack(ModItems.REMOTE_DISCONNECTED.get());
        if (player.getMainHandItem() == targetStack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, disconnected);
        } else if (player.getOffhandItem() == targetStack) {
            player.setItemInHand(InteractionHand.OFF_HAND, disconnected);
        } else if (slot >= 0 && slot < player.getInventory().items.size() && player.getInventory().items.get(slot) == targetStack) {
            player.getInventory().items.set(slot, disconnected);
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DroneDisconnectPacket(droneUUID));
        player.displayClientMessage(Component.literal(ChatFormatting.RED + message), true);
    }

    @Nullable
    private static FpvDroneEntity findDroneServer(ServerLevel level, UUID uuid) {
        Entity entity = level.getEntity(uuid);
        if (entity instanceof FpvDroneEntity drone) {
            return drone;
        }
        return null;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (stack.hasTag() && stack.getTag() != null && stack.getTag().hasUUID(TAG_DRONE_UUID)) {
            tooltip.add(Component.literal(ChatFormatting.GRAY + "Привязан к: " +
                    stack.getTag().getUUID(TAG_DRONE_UUID).toString().substring(0, 8) + "..."));
        }
    }
}
