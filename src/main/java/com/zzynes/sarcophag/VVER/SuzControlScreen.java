package com.zzynes.sarcophag.VVER;

import com.zzynes.sarcophag.DRONE.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SuzControlScreen extends Screen {

    private static final int PANEL_W = 256;
    private static final int PANEL_H = 188;

    private final BlockPos basePos;
    private final List<Button> groupButtons = new ArrayList<>();
    private EditBox percentInput;
    private String errorText = "";
    private int selectedGroup;

    public SuzControlScreen(BlockPos basePos) {
        super(Component.literal("СУЗ ВВЭР"));
        this.basePos = basePos;
    }

    @Override
    protected void init() {
        this.groupButtons.clear();
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        float initialTarget = 0.0f;
        SuzBlockEntity be = getBlockEntity();
        if (be != null) {
            initialTarget = be.getTargetPercent();
            this.selectedGroup = be.getGroup();
        }

        int groupButtonW = 25;
        int groupGap = 3;
        for (int i = 1; i <= 8; i++) {
            int group = i;
            Button button = this.addRenderableWidget(Button.builder(Component.literal(String.valueOf(i)), btn -> selectGroup(group))
                    .bounds(left + 16 + (i - 1) * (groupButtonW + groupGap), top + 68, groupButtonW, 18)
                    .build());
            this.groupButtons.add(button);
        }
        refreshGroupButtons();

        String initStr = (Math.abs(initialTarget - Math.round(initialTarget)) < 0.05f)
                ? String.valueOf(Math.round(initialTarget))
                : String.format(Locale.ROOT, "%.1f", initialTarget);

        this.percentInput = new EditBox(
                this.font,
                left + 16,
                top + 108,
                126,
                20,
                Component.literal("Процент")
        );
        this.percentInput.setMaxLength(6);
        this.percentInput.setValue(initStr);
        this.percentInput.moveCursorToEnd();
        this.percentInput.setHighlightPos(0);
        this.addRenderableWidget(this.percentInput);
        this.setInitialFocus(this.percentInput);

        this.addRenderableWidget(Button.builder(Component.literal("Применить"), btn -> submitTypedPercent())
                .bounds(left + 148, top + 108, 92, 20)
                .build());

        int[] presets = new int[]{0, 25, 50, 75, 100};
        int btnW = 41;
        int gap = 4;
        int rowX = left + 16;
        int rowY = top + 138;
        for (int i = 0; i < presets.length; i++) {
            int val = presets[i];
            this.addRenderableWidget(Button.builder(Component.literal(val + "%"), btn -> applyPercentAndClose(val))
                    .bounds(rowX + i * (btnW + gap), rowY, btnW, 18)
                    .build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Закрыть"), btn -> this.onClose())
                .bounds(left + 88, top + 162, 80, 18)
                .build());
    }

    private void selectGroup(int group) {
        this.selectedGroup = this.selectedGroup == group ? 0 : group;
        VverNetwork.CHANNEL.sendToServer(new SuzGroupPacket(this.basePos, this.selectedGroup));
        SuzBlockEntity be = getBlockEntity();
        if (be != null) {
            be.setGroup(this.selectedGroup);
        }
        refreshGroupButtons();
    }

    private void refreshGroupButtons() {
        for (int i = 0; i < this.groupButtons.size(); i++) {
            int group = i + 1;
            Button button = this.groupButtons.get(i);
            button.setMessage(Component.literal(group == this.selectedGroup ? "●" + group : String.valueOf(group)));
            button.active = true;
        }
    }

    private void submitTypedPercent() {
        String raw = this.percentInput.getValue().trim().replace('%', ' ').replace(',', '.').trim();
        if (raw.isEmpty()) {
            this.errorText = "Введите число от 0 до 100";
            return;
        }
        try {
            float parsed = Float.parseFloat(raw);
            if (Float.isNaN(parsed) || Float.isInfinite(parsed)) {
                this.errorText = "Некорректное значение";
                return;
            }
            applyPercentAndClose(Mth.clamp(parsed, 0.0f, 100.0f));
        } catch (NumberFormatException ex) {
            this.errorText = "Введите число от 0 до 100";
        }
    }

    private void applyPercentAndClose(float pct) {
        ModNetwork.CHANNEL.sendToServer(new SuzPercentPacket(this.basePos, pct));
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submitTypedPercent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        g.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, 0xFF1B1F24);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0262C34);
        g.fill(left, top, left + PANEL_W, top + 22, 0xFF323B46);

        g.drawCenteredString(this.font, this.title, left + PANEL_W / 2, top + 7, 0xFFE8EEF5);

        float curPct = 0.0f;
        float tgtPct = 0.0f;
        SuzBlockEntity be = getBlockEntity();
        if (be != null) {
            curPct = be.getInterpolatedPercent(partialTick);
            tgtPct = be.getTargetPercent();
        }

        String status = String.format(Locale.ROOT, "Текущее: %.1f%%   |   Задание: %.1f%%", curPct, tgtPct);
        g.drawCenteredString(this.font, status, left + PANEL_W / 2, top + 28, 0xFFB8C7D6);

        int barX1 = left + 16;
        int barY1 = top + 42;
        int barW = PANEL_W - 32;
        int barH = 10;
        g.fill(barX1 - 1, barY1 - 1, barX1 + barW + 1, barY1 + barH + 1, 0xFF14181D);
        g.fill(barX1, barY1, barX1 + barW, barY1 + barH, 0xFF1E242B);
        int fillW = Math.round((curPct / 100.0f) * barW);
        if (fillW > 0) {
            g.fill(barX1, barY1, barX1 + fillW, barY1 + barH, 0xFF3CA370);
        }

        g.drawString(this.font, "Группа СУЗ (повторно — снять):", left + 16, top + 56, 0xFFD8E1EA, false);
        g.drawString(this.font, "Подъём стержней (0–100%):", left + 16, top + 94, 0xFFD8E1EA, false);

        if (!this.errorText.isEmpty()) {
            g.drawString(this.font, this.errorText, left + 16, top + 130, 0xFFFF5555, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private SuzBlockEntity getBlockEntity() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        BlockEntity be = this.minecraft.level.getBlockEntity(this.basePos);
        return be instanceof SuzBlockEntity suz ? suz : null;
    }
}
