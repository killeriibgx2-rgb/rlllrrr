package com.zzynes.flydrone.VVER;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class BshuFocusScreen extends Screen {
    private final BlockPos blockPos;
    private final BshuScreenType screenType;
    private int field;
    private String buffer;
    private boolean replaceOnDigit = true;
    private Entity cameraMarker;
    private CameraType previousCameraType;
    private Vec3 savedPlayerPos;
    private float savedPlayerYaw;
    private float savedPlayerPitch;
    private float savedPlayerHeadYaw;
    private float savedPlayerBodyYaw;
    private float cameraYaw;
    private float cameraPitch;
    private boolean viewSetup;
    private boolean restored;

    public BshuFocusScreen(BlockPos blockPos, BshuScreenType screenType, int field) {
        super(Component.translatable("screen.fly_drone.bshu_focus"));
        this.blockPos = blockPos;
        this.screenType = screenType;
        this.field = Mth.clamp(field, 0, 2);
        this.buffer = "";
    }

    @Override
    protected void init() {
        super.init();
        if (!this.viewSetup) {
            setupCamera();
        }
    }

    private void setupCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        BshuBlockEntity blockEntity = getBlockEntity();
        if (this.screenType == BshuScreenType.TERTIARY) {
            setupFrozenPlayerView(minecraft, blockEntity);
            return;
        }
        BshuClientGeometry.ScreenPlane plane = BshuClientGeometry.getScreenPlane(this.screenType);
        if (blockEntity == null || plane == null || minecraft.level == null || minecraft.player == null) {
            onClose();
            return;
        }
        Direction facing = blockEntity.getBlockState().getValue(BshuBlock.FACING);
        Vec3 center = BshuClientGeometry.localToWorld(this.blockPos, facing, plane.center());
        Vec3 normal = BshuClientGeometry.normalToWorld(facing, plane.normal());
        double width = plane.u().length();
        double height = plane.v().length();
        double aspect = Math.max(0.5, (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight()));
        double tanY = Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5);
        double distance = Math.max(height / (2.0 * tanY), width / (2.0 * tanY * aspect)) * 1.28 + 0.04;
        distance = Mth.clamp(distance, 0.42, 2.5);
        Vec3 cameraPos = center.add(normal.scale(distance));
        Vec3 look = normal.scale(-1.0).normalize();
        this.cameraYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        this.cameraPitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(look.y, -1.0, 1.0)));
        Entity marker = EntityType.MARKER.create(minecraft.level);
        if (marker == null) {
            onClose();
            return;
        }
        marker.setPos(cameraPos.x, cameraPos.y, cameraPos.z);
        marker.setYRot(this.cameraYaw);
        marker.setXRot(this.cameraPitch);
        marker.setYHeadRot(this.cameraYaw);
        marker.setOldPosAndRot();
        marker.setNoGravity(true);
        marker.noPhysics = true;
        this.cameraMarker = marker;
        this.previousCameraType = minecraft.options.getCameraType();
        this.savedPlayerPos = minecraft.player.position();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(marker);
        minecraft.levelRenderer.needsUpdate();
        this.buffer = currentValueText(blockEntity);
        this.replaceOnDigit = true;
        this.viewSetup = true;
        BshuClientState.setFocus(this.blockPos, this.screenType, this.field, this.buffer);
    }

    private void setupFrozenPlayerView(Minecraft minecraft, BshuBlockEntity blockEntity) {
        if (blockEntity == null || minecraft.level == null || minecraft.player == null) {
            onClose();
            return;
        }
        this.savedPlayerPos = minecraft.player.position();
        this.savedPlayerYaw = minecraft.player.getYRot();
        this.savedPlayerPitch = minecraft.player.getXRot();
        this.savedPlayerHeadYaw = minecraft.player.getYHeadRot();
        this.savedPlayerBodyYaw = minecraft.player.yBodyRot;
        this.previousCameraType = minecraft.options.getCameraType();
        this.buffer = currentValueText(blockEntity);
        this.replaceOnDigit = true;
        this.viewSetup = true;
        lockPlayerView(minecraft);
        BshuClientState.setFocus(this.blockPos, this.screenType, this.field, this.buffer);
    }

    private boolean usesDetachedCamera() {
        return this.screenType != BshuScreenType.TERTIARY;
    }

    private void lockPlayerView(Minecraft minecraft) {
        if (minecraft.player == null || this.savedPlayerPos == null) {
            return;
        }
        minecraft.player.setDeltaMovement(Vec3.ZERO);
        minecraft.player.fallDistance = 0.0f;
        minecraft.player.setPos(this.savedPlayerPos.x, this.savedPlayerPos.y, this.savedPlayerPos.z);
        minecraft.player.setYRot(this.savedPlayerYaw);
        minecraft.player.setXRot(this.savedPlayerPitch);
        minecraft.player.setYHeadRot(this.savedPlayerHeadYaw);
        minecraft.player.yBodyRot = this.savedPlayerBodyYaw;
        minecraft.player.yRotO = this.savedPlayerYaw;
        minecraft.player.xRotO = this.savedPlayerPitch;
        minecraft.player.yHeadRotO = this.savedPlayerHeadYaw;
        minecraft.player.yBodyRotO = this.savedPlayerBodyYaw;
        minecraft.player.setOldPosAndRot();
        if (this.previousCameraType != null && minecraft.options.getCameraType() != this.previousCameraType) {
            minecraft.options.setCameraType(this.previousCameraType);
        }
        if (minecraft.getCameraEntity() != minecraft.player) {
            minecraft.setCameraEntity(minecraft.player);
            if (minecraft.levelRenderer != null) {
                minecraft.levelRenderer.needsUpdate();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.player.isDeadOrDying()) {
            onClose();
            return;
        }
        BshuBlockEntity blockEntity = getBlockEntity();
        if (blockEntity == null || !blockEntity.isScreenPowered(this.screenType)) {
            onClose();
            return;
        }
        if (!usesDetachedCamera()) {
            lockPlayerView(minecraft);
            return;
        }
        if (this.savedPlayerPos != null) {
            minecraft.player.setDeltaMovement(Vec3.ZERO);
            minecraft.player.fallDistance = 0.0f;
            minecraft.player.setPos(this.savedPlayerPos.x, this.savedPlayerPos.y, this.savedPlayerPos.z);
        }
        if (this.cameraMarker != null && minecraft.getCameraEntity() != this.cameraMarker) {
            minecraft.setCameraEntity(this.cameraMarker);
        }
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    public void reassertCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!usesDetachedCamera()) {
            lockPlayerView(minecraft);
            return;
        }
        if (this.cameraMarker != null && minecraft.player != null && minecraft.getCameraEntity() != this.cameraMarker) {
            minecraft.setCameraEntity(this.cameraMarker);
            if (minecraft.levelRenderer != null) {
                minecraft.levelRenderer.needsUpdate();
            }
        }
        if (minecraft.options != null && minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }
        if (this.screenType != BshuScreenType.GCN) {
            return true;
        }
        BshuClientGeometry.Hit hit = raycastMouse(mouseX, mouseY);
        if (hit != null && hit.screen() == BshuScreenType.GCN) {
            int nextField = hit.textureV() < 0.34f ? 0 : hit.textureV() < 0.67f ? 1 : 2;
            if (nextField != this.field) {
                this.field = nextField;
                BshuBlockEntity blockEntity = getBlockEntity();
                this.buffer = blockEntity == null ? "" : currentValueText(blockEntity);
                this.replaceOnDigit = true;
                BshuClientState.updateFocusField(this.field, this.buffer);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.screenType == BshuScreenType.TERTIARY && delta != 0.0) {
            scrollParameterList(delta > 0.0 ? -3 : 3);
        }
        return true;
    }

    private void scrollParameterList(int deltaLines) {
        BshuBlockEntity blockEntity = getBlockEntity();
        if (blockEntity == null) {
            return;
        }
        int maxScroll = BshuScreenTextures.getParameterListMaxScroll(blockEntity);
        int next = Mth.clamp(blockEntity.getParameterListScroll() + deltaLines, 0, maxScroll);
        if (next == blockEntity.getParameterListScroll()) {
            return;
        }
        blockEntity.setParameterListScroll(next);
        VverNetwork.CHANNEL.sendToServer(new BshuScrollPacket(this.blockPos, next));
        BshuClientState.updateFocusText(String.valueOf(next));
    }

    private BshuClientGeometry.Hit raycastMouse(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        BshuBlockEntity blockEntity = getBlockEntity();
        if (blockEntity == null || this.cameraMarker == null) {
            return null;
        }
        double ndcX = 2.0 * mouseX / Math.max(1, this.width) - 1.0;
        double ndcY = 1.0 - 2.0 * mouseY / Math.max(1, this.height);
        double aspect = (double) Math.max(1, this.width) / Math.max(1, this.height);
        double tanY = Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5);
        Vec3 forward = Vec3.directionFromRotation(this.cameraPitch, this.cameraYaw).normalize();
        double yawRad = Math.toRadians(this.cameraYaw);
        Vec3 right = new Vec3(-Math.cos(yawRad), 0.0, -Math.sin(yawRad)).normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 direction = forward.add(right.scale(ndcX * tanY * aspect)).add(up.scale(ndcY * tanY)).normalize();
        Direction facing = blockEntity.getBlockState().getValue(BshuBlock.FACING);
        return BshuClientGeometry.raycast(this.blockPos, facing, this.cameraMarker.getEyePosition(), direction, 5.0);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (this.screenType == BshuScreenType.TERTIARY) {
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                scrollParameterList(3);
            } else if (keyCode == GLFW.GLFW_KEY_UP) {
                scrollParameterList(-3);
            } else if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_SPACE) {
                scrollParameterList(8);
            } else if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                scrollParameterList(-8);
            } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                scrollParameterList(-100000);
            } else if (keyCode == GLFW.GLFW_KEY_END) {
                scrollParameterList(100000);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyInput();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!this.buffer.isEmpty()) {
                this.buffer = this.buffer.substring(0, this.buffer.length() - 1);
                this.replaceOnDigit = false;
                BshuClientState.updateFocusText(this.buffer);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            this.buffer = "";
            this.replaceOnDigit = false;
            BshuClientState.updateFocusText(this.buffer);
            return true;
        }
        int digit = digitForKey(keyCode);
        if (digit >= 0) {
            if (this.replaceOnDigit) {
                this.buffer = String.valueOf(digit);
                this.replaceOnDigit = false;
            } else if (this.buffer.length() < 3) {
                this.buffer += digit;
            }
            BshuClientState.updateFocusText(this.buffer);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return true;
    }

    private int digitForKey(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return keyCode - GLFW.GLFW_KEY_0;
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            return keyCode - GLFW.GLFW_KEY_KP_0;
        }
        return -1;
    }

    private void applyInput() {
        if (this.screenType != BshuScreenType.CONTROL && this.screenType != BshuScreenType.GCN) {
            return;
        }
        if (this.buffer.isEmpty()) {
            return;
        }
        int value;
        try {
            value = Integer.parseInt(this.buffer);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (value < 0 || value > 100) {
            return;
        }
        VverNetwork.CHANNEL.sendToServer(new BshuInputPacket(this.blockPos, this.screenType, this.field, value));
        this.buffer = String.valueOf(value);
        this.replaceOnDigit = true;
        BshuClientState.updateFocusText(this.buffer);
        if (this.screenType == BshuScreenType.CONTROL) {
            onClose();
        }
    }

    private String currentValueText(BshuBlockEntity blockEntity) {
        if (this.screenType == BshuScreenType.GCN) {
            return String.valueOf(blockEntity.getGcnSpeed(this.field));
        }
        if (this.screenType == BshuScreenType.TERTIARY) {
            return String.valueOf(blockEntity.getParameterListScroll());
        }
        return String.valueOf(blockEntity.getLastRodInput());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String hint = this.screenType == BshuScreenType.GCN
                ? "ГЦН-" + (this.field + 1) + "  •  Enter — применить  •  Esc — выход"
                : this.screenType == BshuScreenType.TERTIARY
                ? "ВСЕ ПАРАМЕТРЫ  •  колесо мыши — прокрутка  •  Esc — выход"
                : "СУЗ  •  Enter — применить и выйти  •  Esc — отмена";
        int textWidth = this.font.width(hint);
        int x = (this.width - textWidth) / 2;
        int y = this.height - 24;
        graphics.fill(x - 7, y - 5, x + textWidth + 7, y + 14, 0xB010151A);
        graphics.drawString(this.font, hint, x, y, 0xFFE2ECF5, false);
    }

    @Override
    public void removed() {
        restoreCamera();
        super.removed();
    }

    private void restoreCamera() {
        if (this.restored) {
            return;
        }
        this.restored = true;
        BshuClientState.clearFocus(this.blockPos);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            if (!usesDetachedCamera()) {
                lockPlayerView(minecraft);
            } else if (this.savedPlayerPos != null) {
                minecraft.player.setDeltaMovement(Vec3.ZERO);
                minecraft.player.setPos(this.savedPlayerPos.x, this.savedPlayerPos.y, this.savedPlayerPos.z);
            }
            if (minecraft.getCameraEntity() == this.cameraMarker) {
                minecraft.setCameraEntity(minecraft.player);
            }
            if (this.previousCameraType != null) {
                minecraft.options.setCameraType(this.previousCameraType);
            }
            if (minecraft.levelRenderer != null) {
                minecraft.levelRenderer.needsUpdate();
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private BshuBlockEntity getBlockEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(this.blockPos);
        return be instanceof BshuBlockEntity bshu ? bshu : null;
    }
}
