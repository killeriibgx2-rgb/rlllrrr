package com.zzynes.flydrone.VVER;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class BshuCoreGroupScreen extends Screen {
    private final BlockPos bshuPos;
    private BlockPos suzPos;
    private boolean cleared;

    public BshuCoreGroupScreen(BlockPos bshuPos, BlockPos suzPos) {
        super(Component.translatable("screen.fly_drone.bshu_core_group"));
        this.bshuPos = bshuPos;
        this.suzPos = suzPos;
    }

    @Override
    protected void init() {
        super.init();
        BshuBlockEntity bshu = getBshu();
        SuzBlockEntity suz = getSuz();
        if (bshu == null || suz == null || !bshu.screensPowered() || !bshu.isLinked()) {
            onClose();
            return;
        }
        BshuClientState.setCoreAssignment(this.bshuPos, this.suzPos);
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.player.isDeadOrDying()) {
            onClose();
            return;
        }
        BshuBlockEntity bshu = getBshu();
        if (bshu == null || getSuz() == null || !bshu.screensPowered() || !bshu.isLinked()) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Component prompt = Component.translatable("message.fly_drone.bshu_press_group_digit");
        int textWidth = this.font.width(prompt);
        int x = (this.width - textWidth) / 2;
        int y = 12;
        graphics.fill(x - 9, y - 6, x + textWidth + 9, y + 15, 0xC010151A);
        graphics.drawString(this.font, prompt, x, y, 0xFFFFD75E, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        int digit = digitForKey(keyCode);
        if (digit >= 0 && digit <= 8) {
            SuzBlockEntity suz = getSuz();
            if (suz != null) {
                suz.setGroup(digit);
            }
            VverNetwork.CHANNEL.sendToServer(new BshuCoreGroupPacket(this.bshuPos, this.suzPos, digit));
            onClose();
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }
        BshuBlockEntity bshu = getBshu();
        if (bshu == null) {
            return true;
        }
        BshuClientGeometry.Hit hit = raycastMouse(mouseX, mouseY, bshu);
        if (hit != null && hit.screen() == BshuScreenType.CORE) {
            BlockPos nextSuz = BshuScreenTextures.findCoreSuzAt(bshu, hit.textureU(), hit.textureV());
            if (nextSuz != null && !nextSuz.equals(this.suzPos)) {
                this.suzPos = nextSuz;
                BshuClientState.setCoreAssignment(this.bshuPos, this.suzPos);
            }
        }
        return true;
    }

    private BshuClientGeometry.Hit raycastMouse(double mouseX, double mouseY, BshuBlockEntity bshu) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return null;
        }
        double ndcX = 2.0 * mouseX / Math.max(1, this.width) - 1.0;
        double ndcY = 1.0 - 2.0 * mouseY / Math.max(1, this.height);
        double aspect = (double) Math.max(1, this.width) / Math.max(1, this.height);
        double tanY = Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5);
        Vec3 forward = Vec3.directionFromRotation(player.getXRot(), player.getYRot()).normalize();
        double yawRad = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(-Math.cos(yawRad), 0.0, -Math.sin(yawRad)).normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 direction = forward.add(right.scale(ndcX * tanY * aspect)).add(up.scale(ndcY * tanY)).normalize();
        Direction facing = bshu.getBlockState().getValue(BshuBlock.FACING);
        return BshuClientGeometry.raycast(this.bshuPos, facing, player.getEyePosition(1.0f), direction, 20.0);
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

    @Override
    public void removed() {
        if (!this.cleared) {
            this.cleared = true;
            BshuClientState.clearCoreAssignment(this.bshuPos);
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private BshuBlockEntity getBshu() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(this.bshuPos);
        return be instanceof BshuBlockEntity bshu ? bshu : null;
    }

    private SuzBlockEntity getSuz() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(this.suzPos);
        return be instanceof SuzBlockEntity suz ? suz : null;
    }
}
