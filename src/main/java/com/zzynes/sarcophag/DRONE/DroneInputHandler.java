package com.zzynes.sarcophag.DRONE;

import com.zzynes.sarcophag.DRONE.entity.FpvDroneEntity;
import com.zzynes.sarcophag.DRONE.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = SarcophagMod.MOD_ID, value = Dist.CLIENT)
public class DroneInputHandler {

    private static float throttle = 0f;
    private static double lastMouseX = 0;
    private static double lastMouseY = 0;
    private static boolean mouseInit = false;
    private static UUID lastUuid = null;
    private static int connectGraceTicks = 0;
    private static Vec3 savedPlayerPos = null;
    private static int returnChunkWaitTicks = 0;

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || event.getAction() != GLFW.GLFW_PRESS) return;

        if ((event.getButton() == 0 || event.getButton() == 1) && FpvOverlay.fpvActive && FpvOverlay.drone != null) {
            event.setCanceled(true);
            ModNetwork.CHANNEL.sendToServer(new DronePayloadActionPacket(FpvOverlay.drone.getUUID(), event.getButton() == 0 ? 0 : 1));
            return;
        }

        if (event.getButton() == 1) {
            if (FpvOverlay.fpvActive || FpvKillcamManager.isActive()) {
                event.setCanceled(true);
                return;
            }

            boolean aimingEntity = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY;
            ItemStack remote = getMainHandConnectedRemote(mc.player);

            if (remote != null && !aimingEntity) {
                UUID uuid = remote.getTag().getUUID("DroneUUID");
                lastUuid = uuid;
                connectGraceTicks = 40;
                savedPlayerPos = mc.player.position();
                returnChunkWaitTicks = 0;
                FpvOverlay.connectionLostTimer = 0;
                FpvOverlay.fpvActive = true;
                mouseInit = false;
                resetMouseKeys(mc);
                ModNetwork.CHANNEL.sendToServer(new DroneControlPacket(
                        uuid, 0f, 0f, 0f, 0f,
                        mc.options.renderDistance().get(),
                        true
                ));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE && event.getAction() == 1) {
            if (FpvKillcamManager.isActive()) {
                FpvKillcamManager.stopEarlyByUser(mc);
                event.setCanceled(true);
                return;
            }
            if (FpvOverlay.fpvActive) {
                exitFpv(mc);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (FpvOverlay.fpvActive || FpvKillcamManager.isActive() || returnChunkWaitTicks > 0) {
            Input input = event.getInput();
            input.forwardImpulse = 0f;
            input.leftImpulse = 0f;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            FpvOverlay.fpvActive = false;
            FpvOverlay.drone = null;
            connectGraceTicks = 0;
            savedPlayerPos = null;
            returnChunkWaitTicks = 0;
            return;
        }

        if (FpvOverlay.connectionLostTimer > 0) FpvOverlay.connectionLostTimer--;
        if (connectGraceTicks > 0) connectGraceTicks--;

        if (FpvKillcamManager.isActive()) {
            FpvOverlay.connectionLostTimer = 0;
            if (savedPlayerPos == null) {
                savedPlayerPos = player.position();
            }
            holdPlayerBodyStill(player);
            if (mc.getCameraEntity() != player) {
                mc.setCameraEntity(player);
            }
            return;
        }

        if (!FpvOverlay.fpvActive && returnChunkWaitTicks > 0) {
            if (areBodyChunksLoaded(level, player)) {
                returnChunkWaitTicks = 0;
                savedPlayerPos = null;
            } else {
                returnChunkWaitTicks--;
                holdPlayerBodyStill(player);
                if (returnChunkWaitTicks == 0) {
                    savedPlayerPos = null;
                }
            }
        }

        ItemStack stack = getConnectedRemote(player);
        if (stack == null) {
            exitFpv(mc);
            FpvOverlay.drone = null;
            return;
        }

        UUID uuid = stack.getTag().getUUID("DroneUUID");
        lastUuid = uuid;
        FpvDroneEntity drone = findDrone(mc, uuid);
        if (drone == null) {
            if (FpvOverlay.fpvActive && connectGraceTicks > 0) {
                holdPlayerBodyStill(player);
                ModNetwork.CHANNEL.sendToServer(new DroneControlPacket(
                        uuid, 0f, 0f, 0f, 0f,
                        mc.options.renderDistance().get(),
                        true
                ));
                return;
            }
            if (FpvOverlay.fpvActive) FpvOverlay.connectionLostTimer = 100;
            exitFpv(mc);
            FpvOverlay.drone = null;
            return;
        }

        connectGraceTicks = 0;
        double dist = player.position().distanceTo(drone.position());
        FpvOverlay.distance = (float) dist;
        FpvOverlay.drone = drone;

        if (FpvOverlay.fpvActive) {
            if (savedPlayerPos == null) {
                savedPlayerPos = player.position();
            }
            holdPlayerBodyStill(player);

            if (mc.getCameraEntity() != drone) {
                mc.setCameraEntity(drone);
                mc.levelRenderer.needsUpdate();
            }

            if (drone.getBatteryTicks() <= 0 || drone.getBatteryTemp() >= 100.0f || dist >= DroneControlPacket.MAX_RANGE) {
                throttle = 0f;
            } else if (mc.options.keyUp.isDown()) {
                throttle = Math.min(1f, throttle + 0.25f);
            } else if (mc.options.keyDown.isDown()) {
                throttle = Math.max(0f, throttle - 0.06f);
            } else {
                throttle *= 0.6f;
                if (throttle < 0.01f) throttle = 0f;
            }

            double dx = 0, dy = 0;
            if (mouseInit) {
                dx = mc.mouseHandler.xpos() - lastMouseX;
                dy = mc.mouseHandler.ypos() - lastMouseY;
            }
            mouseInit = true;
            lastMouseX = mc.mouseHandler.xpos();
            lastMouseY = mc.mouseHandler.ypos();

            float pitchIn = (float) (dy * 0.8);
            float rollIn = (float) (dx * 0.8);

            float yawIn = 0f;
            if (mc.options.keyLeft.isDown()) yawIn += 2.0f;
            if (mc.options.keyRight.isDown()) yawIn -= 2.0f;

            ModNetwork.CHANNEL.sendToServer(new DroneControlPacket(
                    uuid, throttle, pitchIn, rollIn, yawIn,
                    mc.options.renderDistance().get(),
                    true
            ));
        } else {
            if (mc.getCameraEntity() != player) {
                mc.setCameraEntity(player);
                mc.levelRenderer.needsUpdate();
            }
            mouseInit = false;
            throttle = 0f;
        }
    }

    public static void enterKillcamFromImpact(Minecraft mc, UUID droneUUID) {
        lastUuid = droneUUID;
        FpvOverlay.fpvActive = false;
        FpvOverlay.connectionLostTimer = 0;
        FpvOverlay.drone = null;
        throttle = 0f;
        connectGraceTicks = 0;
        mouseInit = false;
        resetMouseKeys(mc);
        if (mc.player != null) {
            if (savedPlayerPos == null) {
                savedPlayerPos = mc.player.position();
            }
            holdPlayerBodyStill(mc.player);
            if (mc.getCameraEntity() != mc.player) {
                mc.setCameraEntity(mc.player);
            }
        }
    }

    public static void finishKillcamReturn(Minecraft mc, boolean userSkipped) {
        if (userSkipped && lastUuid != null) {
            ModNetwork.CHANNEL.sendToServer(new DroneControlPacket(
                    lastUuid, 0f, 0f, 0f, 0f,
                    mc.options != null ? mc.options.renderDistance().get() : 10,
                    false
            ));
        }
        FpvOverlay.fpvActive = false;
        FpvOverlay.connectionLostTimer = 0;
        throttle = 0f;
        connectGraceTicks = 0;
        mouseInit = false;
        resetMouseKeys(mc);

        if (mc.player != null) {
            holdPlayerBodyStill(mc.player);
            if (mc.level != null && !areBodyChunksLoaded(mc.level, mc.player)) {
                returnChunkWaitTicks = 60;
            } else {
                returnChunkWaitTicks = 0;
                savedPlayerPos = null;
            }
            if (mc.getCameraEntity() != mc.player) {
                mc.setCameraEntity(mc.player);
            }
            if (mc.levelRenderer != null) {
                mc.levelRenderer.needsUpdate();
            }
        }
    }

    public static void forceDisconnectClient(UUID droneUUID) {
        if (FpvKillcamManager.isActive()) {
            return;
        }
        throttle = 0f;
        connectGraceTicks = 0;
        Minecraft mc = Minecraft.getInstance();
        if (FpvOverlay.fpvActive && (lastUuid == null || droneUUID.equals(lastUuid))) {
            FpvOverlay.connectionLostTimer = 100;
            exitFpv(mc);
        }
    }

    private static void exitFpv(Minecraft mc) {
        if (!FpvOverlay.fpvActive) return;

        if (lastUuid != null) {
            ModNetwork.CHANNEL.sendToServer(new DroneControlPacket(
                    lastUuid, 0f, 0f, 0f, 0f,
                    mc.options != null ? mc.options.renderDistance().get() : 10,
                    false
            ));
        }
        FpvOverlay.fpvActive = false;
        throttle = 0f;
        connectGraceTicks = 0;
        mouseInit = false;
        resetMouseKeys(mc);

        if (mc.player != null) {
            holdPlayerBodyStill(mc.player);
            if (mc.level != null && !areBodyChunksLoaded(mc.level, mc.player)) {
                returnChunkWaitTicks = 60;
            } else {
                returnChunkWaitTicks = 0;
                savedPlayerPos = null;
            }
            if (mc.getCameraEntity() != mc.player) {
                mc.setCameraEntity(mc.player);
                mc.levelRenderer.needsUpdate();
            }
        }
    }

    private static void holdPlayerBodyStill(LocalPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0f;
        if (savedPlayerPos != null && player.getY() < savedPlayerPos.y) {
            player.setPos(savedPlayerPos.x, savedPlayerPos.y, savedPlayerPos.z);
        }
    }

    private static boolean areBodyChunksLoaded(ClientLevel level, LocalPlayer player) {
        ChunkPos center = player.chunkPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getChunkSource().hasChunk(center.x + dx, center.z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void resetMouseKeys(Minecraft mc) {
        mc.options.keyUse.setDown(false);
        mc.options.keyAttack.setDown(false);
        if (mc.player != null) {
            mc.player.swinging = false;
            mc.player.swingTime = 0;
        }
    }

    private static ItemStack getMainHandConnectedRemote(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() == ModItems.REMOTE_CONNECTED.get() && stack.hasTag() && stack.getTag().hasUUID("DroneUUID")) return stack;
        return null;
    }

    private static ItemStack getConnectedRemote(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() == ModItems.REMOTE_CONNECTED.get() && stack.hasTag() && stack.getTag().hasUUID("DroneUUID")) return stack;
        stack = player.getOffhandItem();
        if (stack.getItem() == ModItems.REMOTE_CONNECTED.get() && stack.hasTag() && stack.getTag().hasUUID("DroneUUID")) return stack;
        return null;
    }

    private static FpvDroneEntity findDrone(Minecraft mc, UUID uuid) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof FpvDroneEntity d && d.getUUID().equals(uuid)) {
                return d;
            }
        }
        return null;
    }

    public static void resetThrottle() {
        throttle = 0f;
    }
}
