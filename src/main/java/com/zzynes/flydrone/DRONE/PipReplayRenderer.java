package com.zzynes.flydrone.DRONE;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.zzynes.flydrone.DRONE.entity.FpvDroneEntity;
import com.zzynes.flydrone.DRONE.init.ModEntityTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;


@Mod.EventBusSubscriber(modid = FlyDroneMod.MOD_ID, value = Dist.CLIENT)
public final class PipReplayRenderer {

    private static final int TARGET_WIDTH = 480;
    private static final int TARGET_HEIGHT = 270;
    private static final Matrix4f MAIN_PROJECTION = new Matrix4f();
    private static RenderTarget target;
    private static boolean rendering;
    private static boolean projectionCaptured;
    private static Vec3 cameraPosition = Vec3.ZERO;
    private static float cameraYaw;
    private static float cameraPitch;
    private static double replaySequence;

    private PipReplayRenderer() {
    }

    public static boolean isRendering() {
        return rendering;
    }

    public static Vec3 getCameraPosition() {
        return cameraPosition;
    }

    public static double getReplaySequence() {
        return replaySequence;
    }

    public static void setCameraPose(Vec3 position, float yaw, float pitch, double sequence) {
        cameraPosition = position;
        cameraYaw = yaw;
        cameraPitch = pitch;
        replaySequence = sequence;
    }

    public static void drawReplay(GuiGraphics gui, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !projectionCaptured || !ProjectileImpactPreview.isActive()) {
            return;
        }
        if (target == null) {
            target = new TextureTarget(TARGET_WIDTH, TARGET_HEIGHT, true, Minecraft.ON_OSX);
            target.setClearColor(0.015f, 0.022f, 0.03f, 1.0f);
        }
        if (target.width != TARGET_WIDTH || target.height != TARGET_HEIGHT) {
            target.resize(TARGET_WIDTH, TARGET_HEIGHT, Minecraft.ON_OSX);
        }

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70.0), TARGET_WIDTH / (float) TARGET_HEIGHT, 0.05f, 256.0f);
        RenderTarget mainTarget = mc.getMainRenderTarget();
        int windowWidth = mc.getWindow().getWidth();
        int windowHeight = mc.getWindow().getHeight();
        rendering = true;
        RenderSystem.getModelViewStack().pushPose();
        try {
            FpvDroneEntity anchor = new FpvDroneEntity(ModEntityTypes.FPV_DRONE.get(), mc.level);
            anchor.setPos(cameraPosition.x, cameraPosition.y - anchor.getEyeHeight(), cameraPosition.z);
            anchor.xo = anchor.getX();
            anchor.yo = anchor.getY();
            anchor.zo = anchor.getZ();
            anchor.setYRot(cameraYaw);
            anchor.yRotO = cameraYaw;
            anchor.setXRot(cameraPitch);
            anchor.xRotO = cameraPitch;
            Camera camera = new Camera();
            camera.setup(mc.level, anchor, false, false, 1.0f);
            target.bindWrite(true);
            target.setClearColor(0.015f, 0.022f, 0.03f, 1.0f);
            target.clear(Minecraft.ON_OSX);
            RenderSystem.viewport(0, 0, TARGET_WIDTH, TARGET_HEIGHT);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            mc.levelRenderer.renderLevel(new PoseStack(), 1.0f, System.nanoTime(), false, camera, mc.gameRenderer, mc.gameRenderer.lightTexture(), projection);
        } finally {
            mainTarget.bindWrite(true);
            RenderSystem.viewport(0, 0, windowWidth, windowHeight);
            RenderSystem.getModelViewStack().popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            rendering = false;
        }
        gui.flush();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = gui.pose().last().pose();
        builder.vertex(matrix, x, y + height, 0.0f).uv(0.0f, 1.0f).color(255, 255, 255, 255).endVertex();
        builder.vertex(matrix, x + width, y + height, 0.0f).uv(1.0f, 1.0f).color(255, 255, 255, 255).endVertex();
        builder.vertex(matrix, x + width, y, 0.0f).uv(1.0f, 0.0f).color(255, 255, 255, 255).endVertex();
        builder.vertex(matrix, x, y, 0.0f).uv(0.0f, 0.0f).color(255, 255, 255, 255).endVertex();
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (rendering) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
                renderHistoricalBlocks(event);
            }
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            MAIN_PROJECTION.set(event.getProjectionMatrix());
            projectionCaptured = true;
        }
    }

    private static void renderHistoricalBlocks(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || event.getPoseStack() == null) return;
        Long2ObjectOpenHashMap<BlockState> blocks = FpvKillcamManager.getProjectileReplayBlocks();
        if (blocks.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        for (Long2ObjectMap.Entry<BlockState> entry : blocks.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            BlockState historical = entry.getValue();
            if (mc.level.getBlockState(pos).equals(historical) || historical.isAir()) continue;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            blockRenderer.renderSingleBlock(historical, poseStack, buffers, LevelRenderer.getLightColor(mc.level, pos), OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    public static void captureProjection(Matrix4f matrix) {
        if (!rendering) {
            MAIN_PROJECTION.set(matrix);
            projectionCaptured = true;
        }
    }

}
