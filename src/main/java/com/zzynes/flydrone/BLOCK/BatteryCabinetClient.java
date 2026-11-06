package com.zzynes.flydrone.BLOCK;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzynes.flydrone.DRONE.FlyDroneMod;
import com.zzynes.flydrone.DRONE.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FlyDroneMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BatteryCabinetClient {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockRegistry.BATTERY_CABINET_BLOCK_ENTITY.get(), BatteryCabinetBlockEntityRenderer::new);
    }

    @Mod.EventBusSubscriber(modid = FlyDroneMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class Highlight {
        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || minecraft.level == null) {
                return;
            }
            if (!(minecraft.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = minecraft.level.getBlockState(hitPos);
            if (!(state.getBlock() instanceof BatteryCabinetBlock)) {
                return;
            }
            BlockPos mainPos = BatteryCabinetBlock.mainPos(hitPos, state);
            BlockState mainState = minecraft.level.getBlockState(mainPos);
            if (!mainState.is(state.getBlock()) || mainState.getValue(BatteryCabinetBlock.PART) != BatteryCabinetBlock.PART_MAIN) {
                return;
            }
            if (!(minecraft.level.getBlockEntity(mainPos) instanceof BatteryCabinetBlockEntity cabinet)) {
                return;
            }
            Direction facing = mainState.getValue(BatteryCabinetBlock.FACING);
            float partialTick = event.getPartialTick();
            Vec3 eye = player.getEyePosition(partialTick);
            Vec3 end = eye.add(player.getViewVector(partialTick).scale(5.0));
            int door = BatteryCabinetSlots.hitDoorPanel(mainPos, facing, cabinet.getDoorProgress(), eye, end);
            if (door != 0) {
                drawDoorOutline(minecraft, event, BatteryCabinetSlots.doorPanelWorldCorners(mainPos, facing, cabinet.getDoorProgress(), door));
                return;
            }
            if (!cabinet.isOpen()
                    || (!player.getMainHandItem().is(ModItems.CABINET_BATTERY.get())
                    && !player.getOffhandItem().is(ModItems.CABINET_BATTERY.get()))) {
                return;
            }
            int slot = BatteryCabinetSlots.pickSlot(mainPos, facing, cabinet.getBatteriesMask(), false, eye, end);
            if (slot >= 0) {
                drawOutline(minecraft, event, BatteryCabinetSlots.slotWorldBox(mainPos, facing, slot).inflate(0.002));
            }
        }

        private static void drawDoorOutline(Minecraft minecraft, RenderLevelStageEvent event, Vec3[] corners) {
            Vec3 camera = event.getCamera().getPosition();
            VertexConsumer buffer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            int[][] edges = {
                    {0, 1}, {2, 3}, {4, 5}, {6, 7},
                    {0, 2}, {1, 3}, {4, 6}, {5, 7},
                    {0, 4}, {1, 5}, {2, 6}, {3, 7}
            };
            for (int[] edge : edges) {
                Vec3 a = corners[edge[0]].subtract(camera);
                Vec3 b = corners[edge[1]].subtract(camera);
                buffer.vertex(a.x, a.y, a.z).color(0.25f, 1.0f, 0.35f, 1.0f).normal(0.0f, 1.0f, 0.0f).endVertex();
                buffer.vertex(b.x, b.y, b.z).color(0.25f, 1.0f, 0.35f, 1.0f).normal(0.0f, 1.0f, 0.0f).endVertex();
            }
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }

        private static void drawOutline(Minecraft minecraft, RenderLevelStageEvent event, AABB box) {
            Vec3 camera = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer buffer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(poseStack, buffer, box, 0.25f, 1.0f, 0.35f, 1.0f);
            poseStack.popPose();
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }
    }
}
