package com.zzynes.flydrone.VVER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import com.zzynes.flydrone.DRONE.FpvOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = FlyDroneMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BshuClientHandler {

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (FpvOverlay.fpvActive || minecraft.getCameraEntity() != minecraft.player) {
            return;
        }
        BshuClientGeometry.Hit hit = BshuClientState.findLookHit(minecraft);
        if (hit == null) {
            return;
        }
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            double vanillaDistance = minecraft.hitResult.getLocation().distanceTo(minecraft.player.getEyePosition(minecraft.getFrameTime()));
            boolean sameBshu = false;
            if (minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                BlockPos vanillaPos = blockHit.getBlockPos();
                if (vanillaPos.equals(hit.blockPos())) {
                    sameBshu = true;
                } else {
                    BlockState vanillaState = minecraft.level.getBlockState(vanillaPos);
                    BlockPos partMaster = BshuCollision.masterPosForPart(vanillaPos, vanillaState);
                    sameBshu = hit.blockPos().equals(partMaster);
                }
            }
            if (!sameBshu && vanillaDistance < hit.distance() - 0.08) {
                return;
            }
        }
        BlockEntity be = minecraft.level.getBlockEntity(hit.blockPos());
        if (!(be instanceof BshuBlockEntity bshu)) {
            return;
        }
        event.setCanceled(true);
        minecraft.options.keyUse.setDown(false);
        if (minecraft.player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.STICK)) {
            VverNetwork.CHANNEL.sendToServer(new BshuLinkPacket(hit.blockPos()));
            minecraft.player.swing(InteractionHand.MAIN_HAND);
            return;
        }
        if (hit.screen() != null) {
            if (hit.screen() == BshuScreenType.CORE) {
                if (bshu.isScreenPowered(hit.screen())) {
                    net.minecraft.core.BlockPos suzPos = BshuScreenTextures.findCoreSuzAt(bshu, hit.textureU(), hit.textureV());
                    if (suzPos != null) {
                        minecraft.setScreen(new BshuCoreGroupScreen(hit.blockPos(), suzPos));
                    }
                }
                return;
            }
            if (hit.screen().isInput() && bshu.isScreenPowered(hit.screen())) {
                int field = 0;
                if (hit.screen() == BshuScreenType.GCN) {
                    field = hit.textureV() < 0.34f ? 0 : hit.textureV() < 0.67f ? 1 : 2;
                }
                minecraft.setScreen(new BshuFocusScreen(hit.blockPos(), hit.screen(), field));
            }
            return;
        }
        if (hit.control() != null) {
            BshuClientState.startPress(hit.blockPos(), hit.control());
            VverNetwork.CHANNEL.sendToServer(new BshuPressPacket(hit.blockPos(), hit.control(), hit.pairOn(), hit.worldPos()));
            minecraft.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(Minecraft.getInstance().screen instanceof BshuFocusScreen)
                && !(Minecraft.getInstance().screen instanceof BshuCoreGroupScreen)) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen instanceof BshuFocusScreen focusScreen) {
            focusScreen.reassertCamera();
        }
        BshuClientState.refreshKnownBshus(minecraft.level, minecraft.player);
        if (minecraft.level.getGameTime() % 100L == 0L) {
            BshuScreenTextures.cleanup(minecraft.level);
        }
    }
}
