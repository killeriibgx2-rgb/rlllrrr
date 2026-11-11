package com.zzynes.sarcophag.VVER;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class BshuScreenTextures {
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final int PARAMETER_LIST_VISIBLE_ROWS = 8;
    private static final Color BG = new Color(7, 17, 24);
    private static final Color PANEL = new Color(13, 27, 38);
    private static final Color PANEL_LIGHT = new Color(20, 40, 55);
    private static final Color GRID = new Color(25, 49, 65);
    private static final Color TEXT = new Color(219, 231, 239);
    private static final Color MUTED = new Color(143, 166, 181);
    private static final Color GREEN = new Color(62, 207, 112);
    private static final Color RED = new Color(239, 68, 68);
    private static final Color AMBER = new Color(242, 193, 78);
    private static final Color CYAN = new Color(86, 184, 205);
    private static final Color[] GROUP_COLORS = new Color[]{
            new Color(120, 128, 138),
            new Color(62, 207, 112),
            new Color(86, 184, 205),
            new Color(96, 140, 255),
            new Color(170, 110, 255),
            new Color(242, 193, 78),
            new Color(255, 126, 72),
            new Color(86, 220, 190),
            new Color(220, 220, 230)
    };

    private BshuScreenTextures() {
    }

    private static final class Entry {
        private final BlockPos pos;
        private final BshuScreenType type;
        private final DynamicTexture texture;
        private final ResourceLocation id;
        private String key = "";
        private long lastUpdateTick = -100L;

        private Entry(BlockPos pos, BshuScreenType type, DynamicTexture texture, ResourceLocation id) {
            this.pos = pos;
            this.type = type;
            this.texture = texture;
            this.id = id;
        }
    }

    public static void renderScreens(BshuBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        if (!BshuClientGeometry.isBuilt() || blockEntity.getLevel() == null) {
            return;
        }
        Direction facing = blockEntity.getBlockState().getValue(BshuBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(BshuClientGeometry.facingAngle(facing)));
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();
        for (BshuScreenType type : BshuScreenType.values()) {
            BshuClientGeometry.ScreenPlane plane = BshuClientGeometry.getScreenPlane(type);
            if (plane == null) {
                continue;
            }
            ResourceLocation textureId = getTexture(blockEntity, type, partialTick);
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(textureId));
            Vec3 offset = plane.normal().scale(0.0012);
            Vector3f normal = normalMatrix.transform(new Vector3f((float) plane.normal().x, (float) plane.normal().y, (float) plane.normal().z));
            addVertex(buffer, matrix, plane.bottomLeft().add(offset), 0.0f, 1.0f, normal);
            addVertex(buffer, matrix, plane.bottomRight().add(offset), 1.0f, 1.0f, normal);
            addVertex(buffer, matrix, plane.topRight().add(offset), 1.0f, 0.0f, normal);
            addVertex(buffer, matrix, plane.topLeft().add(offset), 0.0f, 0.0f, normal);
        }
        poseStack.popPose();
    }

    private static void addVertex(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, float u, float v, Vector3f normal) {
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal.x, normal.y, normal.z)
                .endVertex();
    }

    public static void cleanup(ClientLevel level) {
        Iterator<Map.Entry<String, Entry>> iterator = ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (!(level.getBlockEntity(entry.pos) instanceof BshuBlockEntity)) {
                Minecraft.getInstance().getTextureManager().release(entry.id);
                iterator.remove();
            }
        }
    }

    private static ResourceLocation getTexture(BshuBlockEntity blockEntity, BshuScreenType type, float partialTick) {
        String key = blockEntity.getBlockPos().asLong() + ":" + type.name();
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            NativeImage image = new NativeImage(type.width(), type.height(), false);
            DynamicTexture texture = new DynamicTexture(image);
            String prefix = "bshu_" + Long.toUnsignedString(blockEntity.getBlockPos().asLong(), 16) + "_" + type.name().toLowerCase(Locale.ROOT);
            ResourceLocation id = Minecraft.getInstance().getTextureManager().register(prefix, texture);
            entry = new Entry(blockEntity.getBlockPos(), type, texture, id);
            ENTRIES.put(key, entry);
        }
        long gameTime = blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime();
        String stateKey = stateKey(blockEntity, type, gameTime);
        if (stateKey.equals(entry.key)) {
            return entry.id;
        }
        if (!type.isInput() && gameTime - entry.lastUpdateTick < 4L && !entry.key.isEmpty()) {
            return entry.id;
        }
        entry.key = stateKey;
        entry.lastUpdateTick = gameTime;
        BufferedImage image = draw(blockEntity, type, partialTick);
        NativeImage pixels = entry.texture.getPixels();
        if (pixels != null) {
            for (int y = 0; y < type.height(); y++) {
                for (int x = 0; x < type.width(); x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = argb >>> 24 & 0xFF;
                    int red = argb >>> 16 & 0xFF;
                    int green = argb >>> 8 & 0xFF;
                    int blue = argb & 0xFF;
                    pixels.setPixelRGBA(x, y, alpha << 24 | blue << 16 | green << 8 | red);
                }
            }
            entry.texture.upload();
        }
        return entry.id;
    }

    private static String stateKey(BshuBlockEntity be, BshuScreenType type, long gameTime) {
        StringBuilder builder = new StringBuilder();
        builder.append(be.isPowered(1)).append(be.isPowered(2)).append(be.isPowered(3));
        builder.append('|').append(be.isLinked()).append('|').append(be.getSuzPositions()).append('|').append(be.getTvsPositions());
        for (BshuToggle toggle : BshuToggle.values()) {
            builder.append(be.getToggle(toggle) ? '1' : '0');
        }
        for (int i = 1; i <= 8; i++) {
            builder.append(be.isRodBankSelected(i) ? '1' : '0');
        }
        builder.append('|').append(be.getGcnSpeed(0)).append(',').append(be.getGcnSpeed(1)).append(',').append(be.getGcnSpeed(2));
        builder.append('|').append(be.getLastRodInput()).append('|').append(be.isAlarmActive()).append('|').append(be.isAz5Active()).append('|').append(be.getDieselState());
        builder.append('|').append(be.computeActiveErrorMask()).append('|').append(be.getAcknowledgedErrorMask());
        if (type == BshuScreenType.ERRORS) {
            builder.append('|').append((gameTime / 10L) % 2L);
        }
        if (type == BshuScreenType.SCHEMATIC) {
            for (BshuToggle toggle : new BshuToggle[]{BshuToggle.CL_1, BshuToggle.CL_2, BshuToggle.CL_3, BshuToggle.CL_4,
                    BshuToggle.VENT_1, BshuToggle.VENT_2, BshuToggle.VENT_3, BshuToggle.VENT_4, BshuToggle.SUMP_PUMP}) {
                BshuLampState state = be.getDelayedFailureState(toggle, gameTime);
                builder.append(state == BshuLampState.RED ? '2' : state == BshuLampState.YELLOW ? '1' : '0');
            }
        }
        if (type == BshuScreenType.CORE) {
            builder.append('|').append(BshuClientState.getCoreAssignSuzPos(be.getBlockPos())).append('|').append(BshuClientState.getFocusVersion());
        }
        if (type == BshuScreenType.TERTIARY) {
            builder.append('|').append(be.getParameterListScroll());
        }
        if (type == BshuScreenType.CORE || type == BshuScreenType.TERTIARY || type == BshuScreenType.PARAMETERS || type == BshuScreenType.FUEL || type == BshuScreenType.SCHEMATIC) {
            if (be.getLevel() != null) {
                for (BlockPos pos : be.getSuzPositions()) {
                    if (be.getLevel().getBlockEntity(pos) instanceof SuzBlockEntity suz) {
                        builder.append('|').append(Math.round(suz.getCurrentPercent())).append(':').append(Math.round(suz.getTargetPercent())).append(':').append(suz.getGroup())
                                .append(':').append(Math.round(suz.getTemperatureC())).append(':').append(Math.round(suz.getIntegrityPercent()));
                    }
                }
                for (BlockPos pos : be.getTvsPositions()) {
                    BlockState state = be.getLevel().getBlockState(pos);
                    builder.append('|').append(state.is(VverRegistry.TVS_BLOCK.get()) && state.getValue(TvsBlock.HAS_RODS));
                    if (be.getLevel().getBlockEntity(pos) instanceof TvsBlockEntity tvs) {
                        ItemStack stack = tvs.itemHandler.getStackInSlot(0);
                        builder.append(':').append(stack.isEmpty() ? "-" : stack.getHoverName().getString())
                                .append(':').append(Math.round(tvs.getTemperatureC())).append(':').append(Math.round(tvs.getIntegrityPercent()));
                    }
                }
            }
        }
        if (type.isInput() && BshuClientState.isFocused(be.getBlockPos(), type)) {
            builder.append('|').append(BshuClientState.getFocusVersion()).append('|').append((System.currentTimeMillis() / 500L) % 2L);
        }
        return builder.toString();
    }

    private static BufferedImage draw(BshuBlockEntity be, BshuScreenType type, float partialTick) {
        BufferedImage image = new BufferedImage(type.width(), type.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (!be.isScreenPowered(type)) {
            g.setColor(new Color(2, 4, 5));
            g.fillRect(0, 0, type.width(), type.height());
            g.setColor(new Color(8, 13, 16));
            for (int y = 4; y < type.height(); y += 6) {
                g.fillRect(0, y, type.width(), 1);
            }
            g.dispose();
            return image;
        }
        g.setColor(BG);
        g.fillRect(0, 0, type.width(), type.height());
        drawGrid(g, type.width(), type.height());
        switch (type) {
            case CORE -> drawCore(g, be, partialTick);
            case SECONDARY -> drawSecondary(g, be);
            case TERTIARY -> drawParameterList(g, be);
            case SCHEMATIC -> drawSchematic(g, be);
            case FUEL -> drawFuel(g, be);
            case PARAMETERS -> drawParameters(g, be, partialTick);
            case ERRORS -> drawErrors(g, be);
            case CONTROL -> drawControl(g, be);
            case GCN -> drawGcn(g, be);
        }
        g.setColor(new Color(48, 78, 94));
        g.drawRect(0, 0, type.width() - 1, type.height() - 1);
        g.dispose();
        return image;
    }

    private static void drawGrid(Graphics2D g, int width, int height) {
        g.setColor(GRID);
        for (int x = 16; x < width; x += 32) {
            g.drawLine(x, 0, x, height);
        }
        for (int y = 16; y < height; y += 32) {
            g.drawLine(0, y, width, y);
        }
    }

    private static void header(Graphics2D g, int width, String title, String right) {
        g.setColor(new Color(11, 25, 34));
        g.fillRect(0, 0, width, 34);
        g.setColor(CYAN);
        g.fillRect(0, 33, width, 2);
        text(g, title, 12, 23, 17, TEXT, true, false);
        if (right != null && !right.isEmpty()) {
            text(g, right, width - 12, 22, 12, MUTED, false, true);
        }
    }

    private record CoreLayout(int minX, int minZ, int cols, int rows, int cell, int startX, int startY) {
    }

    private static CoreLayout coreLayout(BshuBlockEntity be) {
        List<BlockPos> all = new ArrayList<>();
        all.addAll(be.getSuzPositions());
        all.addAll(be.getTvsPositions());
        if (all.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : all) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int cols = maxX - minX + 1;
        int rows = maxZ - minZ + 1;
        int cell = Math.max(10, Math.min(54, Math.min(452 / cols, 392 / rows)));
        int startX = (512 - cols * cell) / 2;
        int startY = 52 + (392 - rows * cell) / 2;
        return new CoreLayout(minX, minZ, cols, rows, cell, startX, startY);
    }

    public static BlockPos findCoreSuzAt(BshuBlockEntity be, float textureU, float textureV) {
        CoreLayout layout = coreLayout(be);
        if (layout == null || textureU < 0.0f || textureU > 1.0f || textureV < 0.0f || textureV > 1.0f) {
            return null;
        }
        int pixelX = Math.round(textureU * BshuScreenType.CORE.width());
        int pixelY = Math.round(textureV * BshuScreenType.CORE.height());
        int col = Math.floorDiv(pixelX - layout.startX(), layout.cell());
        int row = Math.floorDiv(pixelY - layout.startY(), layout.cell());
        if (col < 0 || col >= layout.cols() || row < 0 || row >= layout.rows()) {
            return null;
        }
        int localX = pixelX - (layout.startX() + col * layout.cell());
        int localY = pixelY - (layout.startY() + row * layout.cell());
        if (localX < 2 || localX >= layout.cell() - 2 || localY < 2 || localY >= layout.cell() - 2) {
            return null;
        }
        int worldX = layout.minX() + col;
        int worldZ = layout.minZ() + row;
        BlockPos found = null;
        for (BlockPos pos : be.getSuzPositions()) {
            if (pos.getX() == worldX && pos.getZ() == worldZ) {
                found = pos;
            }
        }
        return found;
    }

    private static void drawCore(Graphics2D g, BshuBlockEntity be, float partialTick) {
        g.setColor(new Color(11, 25, 34));
        g.fillRect(0, 0, 512, 34);
        g.setColor(CYAN);
        g.fillRect(0, 33, 512, 2);
        centerText(g, "CORE", 256, 23, 17, TEXT, true);
        CoreLayout layout = coreLayout(be);
        if (layout == null) {
            centerText(g, "БЩУ НЕ ПРИВЯЗАН К АКТИВНОЙ ЗОНЕ", 256, 225, 20, AMBER, true);
            centerText(g, "Палочка: ТВС/СУЗ → БЩУ", 256, 255, 15, MUTED, false);
            return;
        }
        int minX = layout.minX();
        int minZ = layout.minZ();
        int cell = layout.cell();
        int startX = layout.startX();
        int startY = layout.startY();
        for (BlockPos pos : be.getTvsPositions()) {
            int x = startX + (pos.getX() - minX) * cell;
            int y = startY + (pos.getZ() - minZ) * cell;
            boolean loaded = false;
            TvsBlockEntity tvs = null;
            if (be.getLevel() != null) {
                BlockState state = be.getLevel().getBlockState(pos);
                loaded = state.is(VverRegistry.TVS_BLOCK.get()) && state.getValue(TvsBlock.HAS_RODS);
                if (be.getLevel().getBlockEntity(pos) instanceof TvsBlockEntity foundTvs) {
                    tvs = foundTvs;
                }
            }
            g.setColor(loaded ? new Color(22, 55, 78) : new Color(42, 35, 35));
            g.fillRect(x + 2, y + 2, cell - 4, cell - 4);
            g.setColor(loaded ? CYAN : RED);
            g.drawRect(x + 2, y + 2, cell - 5, cell - 5);
            if (loaded && cell >= 22) {
                g.setColor(new Color(205, 216, 224));
                int dot = Math.max(2, cell / 10);
                for (int ix = 0; ix < 3; ix++) {
                    for (int iy = 0; iy < 3; iy++) {
                        int cx = x + cell / 2 + (ix - 1) * cell / 5;
                        int cy = y + cell / 2 + (iy - 1) * cell / 5;
                        g.fillOval(cx - dot / 2, cy - dot / 2, dot, dot);
                    }
                }
            }
            drawThermalBars(g, x, y, cell,
                    tvs == null ? VverThermal.AMBIENT_C : tvs.getTemperatureC(),
                    tvs == null ? 100.0f : tvs.getIntegrityPercent(),
                    VverThermal.TVS_NORMAL_C,
                    VverThermal.TVS_SHELL_MELT_C);
            if (cell >= 34) {
                centerText(g, "ТВС", x + cell / 2, y + 13, 9, TEXT, true);
                centerText(g, loaded ? "ЗАГР" : "ПУСТО", x + cell / 2, y + cell - 6, 8, loaded ? MUTED : RED, false);
            }
        }
        for (BlockPos pos : be.getSuzPositions()) {
            int x = startX + (pos.getX() - minX) * cell;
            int y = startY + (pos.getZ() - minZ) * cell;
            SuzBlockEntity suz = be.getLevel() != null && be.getLevel().getBlockEntity(pos) instanceof SuzBlockEntity found ? found : null;
            int group = suz == null ? 0 : suz.getGroup();
            float percent = suz == null ? 0.0f : suz.getInterpolatedPercent(partialTick);
            Color color = GROUP_COLORS[group];
            g.setColor(new Color(24, 31, 38));
            g.fillRect(x + 2, y + 2, cell - 4, cell - 4);
            int fillHeight = Math.round((cell - 6) * percent / 100.0f);
            g.setColor(darken(color, 0.55f));
            g.fillRect(x + 3, y + cell - 3 - fillHeight, cell - 6, fillHeight);
            g.setColor(color);
            g.drawRect(x + 2, y + 2, cell - 5, cell - 5);
            drawThermalBars(g, x, y, cell,
                    suz == null ? VverThermal.AMBIENT_C : suz.getTemperatureC(),
                    suz == null ? 100.0f : suz.getIntegrityPercent(),
                    VverThermal.SUZ_NORMAL_C,
                    VverThermal.SUZ_MELT_C);
            if (pos.equals(BshuClientState.getCoreAssignSuzPos(be.getBlockPos()))) {
                g.setColor(AMBER);
                g.drawRect(x + 1, y + 1, cell - 3, cell - 3);
                g.setColor(TEXT);
                g.drawRect(x, y, cell - 1, cell - 1);
            }
            if (cell >= 30) {
                centerText(g, group == 0 ? "СУЗ -" : "Г" + group, x + cell / 2, y + 13, 9, TEXT, true);
                centerText(g, Math.round(percent) + "%", x + cell / 2, y + cell - 6, 10, TEXT, true);
            } else if (cell >= 18) {
                centerText(g, String.valueOf(Math.round(percent)), x + cell / 2, y + cell / 2 + 4, 9, TEXT, true);
            }
        }
        text(g, "ТВС: " + be.getTvsPositions().size() + "   СУЗ: " + be.getSuzPositions().size(), 14, 478, 14, TEXT, true, false);
        text(g, selectedGroupsText(be), 498, 478, 13, MUTED, false, true);
        text(g, "N", 498, 58, 14, CYAN, true, true);
    }

    private static void drawThermalBars(Graphics2D g, int x, int y, int cell, float temperatureC, float integrityPercent, float normalC, float meltC) {
        if (cell < 14) {
            return;
        }
        int barWidth = cell >= 40 ? 4 : cell >= 24 ? 3 : 2;
        int top = y + 5;
        int height = Math.max(4, cell - 10);
        int leftX = x + 4;
        int rightX = x + cell - 4 - barWidth;
        drawVerticalBar(g, leftX, top, barWidth, height, temperatureFraction(temperatureC), temperatureColor(temperatureC, normalC, meltC));
        drawVerticalBar(g, rightX, top, barWidth, height, VverThermal.clampIntegrity(integrityPercent) / 100.0f, integrityColor(integrityPercent));
    }

    private static void drawVerticalBar(Graphics2D g, int x, int y, int width, int height, float fraction, Color color) {
        float clamped = Math.max(0.0f, Math.min(1.0f, fraction));
        g.setColor(new Color(5, 10, 14, 210));
        g.fillRect(x, y, width, height);
        int fill = clamped <= 0.0f ? 0 : Math.max(1, Math.round(height * clamped));
        if (fill > 0) {
            g.setColor(color);
            g.fillRect(x, y + height - fill, width, fill);
        }
        g.setColor(new Color(120, 145, 160, 220));
        g.drawRect(x, y, Math.max(0, width - 1), Math.max(0, height - 1));
    }

    private static float temperatureFraction(float temperatureC) {
        float value = Math.max(0.0f, temperatureC);
        if (value <= 600.0f) {
            return value / 600.0f * 0.5f;
        }
        if (value >= VverThermal.SCALE_MAX_C) {
            return 1.0f;
        }
        return 0.5f + (value - 600.0f) / (VverThermal.SCALE_MAX_C - 600.0f) * 0.5f;
    }

    private static Color temperatureColor(float temperatureC, float normalC, float meltC) {
        if (temperatureC < normalC) {
            return GREEN;
        }
        if (temperatureC < meltC) {
            return AMBER;
        }
        return RED;
    }

    private static Color integrityColor(float integrityPercent) {
        if (integrityPercent >= 70.0f) {
            return GREEN;
        }
        if (integrityPercent >= 40.0f) {
            return AMBER;
        }
        if (integrityPercent > VverThermal.CRITICAL_INTEGRITY_PERCENT) {
            return new Color(255, 126, 48);
        }
        return RED;
    }

    private static void drawSchematic(Graphics2D g, BshuBlockEntity be) {
        header(g, 512, "МНЕМОСХЕМА СВЯЗЕЙ", be.isLinked() ? "СВЯЗЬ ЕСТЬ" : "НЕТ СВЯЗИ");
        node(g, 18, 62, 104, 58, "БЩУ", "power_2", be.isPowered(2));
        arrow(g, 122, 91, 164, 91, be.isLinked());
        String seed = be.getSeedPos() == null ? "не задан" : be.getSeedPos().getX() + "," + be.getSeedPos().getY() + "," + be.getSeedPos().getZ();
        node(g, 164, 62, 128, 58, "ТОЧКА ВХОДА", seed, be.isLinked());
        arrow(g, 292, 91, 332, 91, be.isLinked());
        node(g, 332, 55, 162, 72, "АКТИВНАЯ ЗОНА", "ТВС " + be.getTvsPositions().size() + " | СУЗ " + be.getSuzPositions().size(), be.isLinked());
        text(g, "БАЙПАС / СЛИВ / ВЕНТИЛЯЦИЯ", 18, 152, 13, TEXT, true, false);
        String[] labels = new String[]{"BP-1", "BP-2", "BP-3", "CL-1", "CL-2", "CL-3", "CL-4", "V-1", "V-2", "V-3", "V-4", "SUMP", "ISO", "P-0"};
        // todo: координаты и подписи когда-нибудь переедут в ресурс, пока так
        BshuToggle[] toggles = new BshuToggle[]{
                BshuToggle.BP_1,
                BshuToggle.BP_2,
                BshuToggle.BP_3,
                BshuToggle.CL_1,
                BshuToggle.CL_2,
                BshuToggle.CL_3,
                BshuToggle.CL_4,
                BshuToggle.VENT_1,
                BshuToggle.VENT_2,
                BshuToggle.VENT_3,
                BshuToggle.VENT_4,
                BshuToggle.SUMP_PUMP,
                BshuToggle.ISOLATE,
                BshuToggle.PRESSURE
        };
        for (int i = 0; i < labels.length; i++) {
            int x = 18 + (i % 7) * 69;
            int y = 164 + (i / 7) * 34;
            boolean on = be.getToggle(toggles[i]);
            int status = on ? 1 : 0;
            if (on && isDelayedFailureToggle(toggles[i])) {
                long gameTime = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
                BshuLampState state = be.getDelayedFailureState(toggles[i], gameTime);
                status = state == BshuLampState.RED ? 3 : state == BshuLampState.YELLOW ? 2 : 1;
            }
            pillStatus(g, x, y, 62, 24, labels[i], status);
        }
        text(g, "Трубные графы и оборудование контуров - следующий этап", 18, 240, 12, MUTED, false, false);
    }

    private static void drawParameters(Graphics2D g, BshuBlockEntity be, float partialTick) {
        header(g, 512, "ПАРАМЕТРЫ РЕАКТОРА", "ТЕКУЩИЙ СКАН");
        int loaded = countLoadedTvs(be);
        float sum = 0.0f;
        float min = 100.0f;
        float max = 0.0f;
        int found = 0;
        int ungrouped = 0;
        if (be.getLevel() != null) {
            for (BlockPos pos : be.getSuzPositions()) {
                if (be.getLevel().getBlockEntity(pos) instanceof SuzBlockEntity suz) {
                    float value = suz.getInterpolatedPercent(partialTick);
                    sum += value;
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    found++;
                    if (suz.getGroup() == 0) {
                        ungrouped++;
                    }
                }
            }
        }
        String average = found == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", sum / found);
        String spread = found == 0 ? "-" : String.format(Locale.ROOT, "%.0f…%.0f%%", min, max);
        String[][] rows = new String[][]{
                {"Связь с зоной", be.isLinked() ? "УСТАНОВЛЕНА" : "НЕТ"},
                {"ТВС загружено", loaded + " / " + be.getTvsPositions().size()},
                {"СУЗ без группы", String.valueOf(ungrouped)},
                {"Средний подъём СУЗ", average},
                {"Диапазон СУЗ", spread},
                {"Выбранные группы", selectedGroupsText(be)},
                {"ГЦН 1 / 2 / 3", be.getGcnSpeed(0) + "% | " + be.getGcnSpeed(1) + "% | " + be.getGcnSpeed(2) + "%"},
                {"Питание отделов", powerText(be)}
        };
        int y = 52;
        for (String[] row : rows) {
            g.setColor(PANEL);
            g.fillRect(14, y - 13, 484, 18);
            text(g, row[0], 22, y, 12, MUTED, false, false);
            text(g, row[1], 490, y, 12, TEXT, true, true);
            y += 19;
        }
        text(g, "Тепловая мощность, температура, давление и расход: расчёт ещё не подключён", 14, 202, 11, AMBER, false, false);
    }

    private static void drawFuel(Graphics2D g, BshuBlockEntity be) {
        header(g, 512, "ТОПЛИВО И ТВС", "ЗАГРУЗКА");
        int total = be.getTvsPositions().size();
        int loaded = countLoadedTvs(be);
        Map<String, Integer> itemCounts = new LinkedHashMap<>();
        if (be.getLevel() != null) {
            for (BlockPos pos : be.getTvsPositions()) {
                if (be.getLevel().getBlockEntity(pos) instanceof TvsBlockEntity tvs) {
                    ItemStack stack = tvs.itemHandler.getStackInSlot(0);
                    if (!stack.isEmpty()) {
                        itemCounts.merge(stack.getHoverName().getString(), 1, Integer::sum);
                    }
                }
            }
        }
        String ratio = total == 0 ? "-" : loaded + " / " + total;
        centerText(g, ratio, 104, 104, 34, TEXT, true);
        centerText(g, "загруженных ТВС", 104, 126, 13, MUTED, false);
        int barW = 168;
        int filled = total == 0 ? 0 : Math.round(barW * (float) loaded / total);
        g.setColor(new Color(16, 29, 38));
        g.fillRect(20, 142, barW, 14);
        g.setColor(CYAN);
        g.fillRect(20, 142, filled, 14);
        g.setColor(GRID);
        g.drawRect(20, 142, barW, 14);
        text(g, "СОДЕРЖИМОЕ СЛОТОВ", 222, 62, 13, TEXT, true, false);
        int y = 84;
        if (itemCounts.isEmpty()) {
            text(g, "Предметы не обнаружены", 222, y, 13, MUTED, false, false);
        } else {
            for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
                if (y > 170) {
                    break;
                }
                text(g, trimToWidth(g, entry.getKey(), 205, 13), 222, y, 13, TEXT, false, false);
                text(g, "×" + entry.getValue(), 492, y, 13, CYAN, true, true);
                y += 20;
            }
        }
        text(g, "Обогащение, выгорание и повреждение топлива появятся с топливным циклом", 18, 198, 12, AMBER, false, false);
    }

    private record ParameterRow(String text, boolean heading) {
    }

    private static void drawSecondary(Graphics2D g, BshuBlockEntity be) {
        header(g, 512, "ВТОРОЙ + ТРЕТИЙ КОНТУРЫ", "МНЕМОСХЕМА");
        int pumps = countToggle(be, BshuToggle.NASOS_1, BshuToggle.NASOS_2, BshuToggle.NASOS_3);
        int valves = countPairs(be, "a") + countPairs(be, "b") + countPairs(be, "c");
        int vents = countToggle(be, BshuToggle.VENT_1, BshuToggle.VENT_2, BshuToggle.VENT_3, BshuToggle.VENT_4);
        int drains = countToggle(be, BshuToggle.CL_1, BshuToggle.CL_2, BshuToggle.CL_3, BshuToggle.CL_4);
        boolean turbineOn = be.getToggle(BshuToggle.TURBINE);
        String turbineRpm = turbineOn ? "-" : "0";
        String generation = turbineOn ? "-" : "0";
        node(g, 16, 44, 86, 48, "ПГ", "T - | P -", be.isLinked());
        arrow(g, 102, 68, 126, 68, turbineOn);
        node(g, 126, 44, 86, 48, "ПАР", "T - | P -", turbineOn);
        arrow(g, 212, 68, 236, 68, turbineOn);
        node(g, 236, 44, 92, 48, "ТУРБИНА", turbineRpm + " об/мин", turbineOn);
        arrow(g, 328, 68, 352, 68, turbineOn);
        node(g, 352, 44, 64, 48, "ГЕН.", generation + " FE/t", turbineOn);
        arrow(g, 416, 68, 438, 68, pumps > 0);
        node(g, 438, 44, 58, 48, "КОНД.", "T - | P -", pumps > 0);
        node(g, 18, 100, 104, 48, "КОНДЕНСАТОР", "T - | P -", be.isLinked());
        arrow(g, 122, 124, 150, 124, vents > 0);
        node(g, 150, 100, 96, 48, "ВОДА", "T - | расх. -", vents > 0);
        arrow(g, 246, 124, 274, 124, vents > 0);
        node(g, 274, 100, 104, 48, "ГРАДИРНЯ", "T - | испар. -", vents > 0);
        arrow(g, 378, 124, 406, 124, drains > 0);
        node(g, 406, 100, 88, 48, "ПОДПИТКА", "объём: -", drains > 0);
        text(g, "Турбина: " + turbineRpm + " об/мин | выработка: " + generation + " FE/t", 18, 164, 11, TEXT, true, false);
        text(g, "Насосы: " + pumps + "/3 | A/B/C: " + valves + "/18 | вентиляция: " + vents + "/4 | слив: " + drains + "/4", 18, 178, 10, MUTED, false, false);
    }

    public static int getParameterListMaxScroll(BshuBlockEntity be) {
        return Math.max(0, parameterRows(be).size() - PARAMETER_LIST_VISIBLE_ROWS);
    }

    private static void drawParameterList(Graphics2D g, BshuBlockEntity be) {
        List<ParameterRow> rows = parameterRows(be);
        int maxScroll = Math.max(0, rows.size() - PARAMETER_LIST_VISIBLE_ROWS);
        int offset = Math.max(0, Math.min(maxScroll, be.getParameterListScroll()));
        int end = Math.min(rows.size(), offset + PARAMETER_LIST_VISIBLE_ROWS);
        String range = rows.isEmpty() ? "0 / 0" : (offset + 1) + "–" + end + " / " + rows.size();
        header(g, 512, "ВСЕ ПАРАМЕТРЫ", range);
        int y = 52;
        for (int i = offset; i < end; i++) {
            ParameterRow row = rows.get(i);
            if (row.heading()) {
                text(g, row.text(), 14, y, 12, CYAN, true, false);
            } else {
                text(g, trimToWidth(g, row.text(), 474, 11), 14, y, 11, TEXT, false, false);
            }
            y += 16;
        }
        if (maxScroll > 0) {
            int trackY = 44;
            int trackH = 126;
            int thumbH = Math.max(12, trackH * PARAMETER_LIST_VISIBLE_ROWS / Math.max(1, rows.size()));
            int thumbY = trackY + (trackH - thumbH) * offset / maxScroll;
            g.setColor(PANEL);
            g.fillRect(499, trackY, 4, trackH);
            g.setColor(CYAN);
            g.fillRect(499, thumbY, 4, thumbH);
        }
    }

    private static List<ParameterRow> parameterRows(BshuBlockEntity be) {
        List<ParameterRow> rows = new ArrayList<>();
        rows.add(new ParameterRow("АКТИВНАЯ ЗОНА - ТВС", true));
        if (!be.isLinked()) {
            rows.add(new ParameterRow("Связь с активной зоной: нет", false));
        }
        if (be.getTvsPositions().isEmpty()) {
            rows.add(new ParameterRow("ТВС: не обнаружены", false));
        }
        float maxTvsTemperature = Float.NEGATIVE_INFINITY;
        float minTvsIntegrity = Float.POSITIVE_INFINITY;
        for (int i = 0; i < be.getTvsPositions().size(); i++) {
            BlockPos pos = be.getTvsPositions().get(i);
            TvsBlockEntity tvs = be.getLevel() != null && be.getLevel().getBlockEntity(pos) instanceof TvsBlockEntity found ? found : null;
            if (tvs == null) {
                rows.add(new ParameterRow("ТВС-" + (i + 1) + " " + positionText(pos) + ": температура - | оболочка - | нет данных", false));
                continue;
            }
            boolean loaded = false;
            if (be.getLevel() != null) {
                BlockState state = be.getLevel().getBlockState(pos);
                loaded = state.is(VverRegistry.TVS_BLOCK.get()) && state.getValue(TvsBlock.HAS_RODS);
            }
            maxTvsTemperature = Math.max(maxTvsTemperature, tvs.getTemperatureC());
            minTvsIntegrity = Math.min(minTvsIntegrity, tvs.getIntegrityPercent());
            rows.add(new ParameterRow("ТВС-"
                    + (i + 1)
                    + " "
                    + positionText(pos)
                    + ": "
                    + temperatureText(tvs.getTemperatureC())
                    + " | оболочка "
                    + integrityText(tvs.getIntegrityPercent())
                    + " | "
                    + (loaded ? "загружена" : "пустая"), false));
        }
        if (Float.isFinite(maxTvsTemperature)) {
            rows.add(new ParameterRow("Макс. температура ТВС: " + temperatureText(maxTvsTemperature) + " | мин. оболочка ТВС: " + integrityText(minTvsIntegrity), false));
        }
        rows.add(new ParameterRow("АКТИВНАЯ ЗОНА - СУЗ", true));
        if (be.getSuzPositions().isEmpty()) {
            rows.add(new ParameterRow("СУЗ: не обнаружены", false));
        }
        float maxSuzTemperature = Float.NEGATIVE_INFINITY;
        float minSuzIntegrity = Float.POSITIVE_INFINITY;
        for (int i = 0; i < be.getSuzPositions().size(); i++) {
            BlockPos pos = be.getSuzPositions().get(i);
            SuzBlockEntity suz = be.getLevel() != null && be.getLevel().getBlockEntity(pos) instanceof SuzBlockEntity found ? found : null;
            if (suz == null) {
                rows.add(new ParameterRow("СУЗ-" + (i + 1) + " " + positionText(pos) + ": температура - | оболочка - | нет данных", false));
                continue;
            }
            maxSuzTemperature = Math.max(maxSuzTemperature, suz.getTemperatureC());
            minSuzIntegrity = Math.min(minSuzIntegrity, suz.getIntegrityPercent());
            String group = suz.getGroup() == 0 ? "без группы" : "группа " + suz.getGroup();
            rows.add(new ParameterRow("СУЗ-"
                    + (i + 1)
                    + " "
                    + positionText(pos)
                    + ": "
                    + temperatureText(suz.getTemperatureC())
                    + " | оболочка "
                    + integrityText(suz.getIntegrityPercent())
                    + " | "
                    + group
                    + " | подъём "
                    + Math.round(suz.getCurrentPercent())
                    + "%", false));
        }
        if (Float.isFinite(maxSuzTemperature)) {
            rows.add(new ParameterRow("Макс. температура СУЗ: " + temperatureText(maxSuzTemperature) + " | мин. оболочка СУЗ: " + integrityText(minSuzIntegrity), false));
        }
        rows.add(new ParameterRow("ОБОЛОЧКИ, КОНТУРЫ И ТРУБЫ", true));
        if (be.isVesselPresent()) {
        rows.add(new ParameterRow("Корпус реактора: "
                    + (be.isVesselSealed() ? "герметичен" : "не герметичен")
                    + " | прочность обшивки "
                    + be.getVesselIntegrityPercent()
                    + "% | объём "
                    + be.getVesselCapacityM3()
                    + " м³", false));
        rows.add(new ParameterRow("Первый контур: температура "
                    + be.getVesselTemperatureC()
                    + "°C | давление "
                    + pressureText(be.getVesselPressureAtm())
                    + " атм | предел "
                    + pressureText(be.getVesselPressureLimitAtm())
                    + " атм | вода "
                    + be.getVesselCoolantM3()
                    + "/"
                    + be.getVesselCapacityM3()
                    + " м³ | подушка "
                    + be.getVesselGasPercent()
                    + "% | расход -", false));
        rows.add(new ParameterRow("Патрубки корпуса: сообщаются с полостью "
                    + be.getVesselConnectedNozzleCount()
                    + " | производительность петель "
                    + be.getVesselNozzlePerformancePercent()
                    + "%", false));
        } else {
            rows.add(new ParameterRow("Корпус реактора: не обнаружен | прочность обшивки -", false));
            rows.add(new ParameterRow("Первый контур: температура - | давление - | расход -", false));
        }
        rows.add(new ParameterRow("Трубы первого контура: температура - | давление - | прочность -", false));
        for (int i = 0; i < 3; i++) {
            String strength = be.isGcnPresent(i) ? integrityText(be.getGcnIntegrity(i)) : "-";
            String presence = be.isGcnPresent(i) ? "есть" : "оборудования нет";
            rows.add(new ParameterRow("ГЦН-" + (i + 1) + ": " + presence + " | задание " + be.getGcnSpeed(i) + "% | прочность " + strength, false));
        }
        rows.add(new ParameterRow("Парогенератор: температура - | давление - | уровень - | прочность -", false));
        rows.add(new ParameterRow("Второй контур: температура - | давление - | расход -", false));
        rows.add(new ParameterRow("Трубы второго контура: температура - | давление - | прочность -", false));
        rows.add(new ParameterRow("Конденсатор: температура - | давление - | прочность -", false));
        rows.add(new ParameterRow("Третий контур: температура - | давление - | расход -", false));
        rows.add(new ParameterRow("Градирня: температура - | испарение - | прочность -", false));
        rows.add(new ParameterRow("Подпитка: объём - | расход -", false));
        rows.add(new ParameterRow("ТУРБИНА, ГЕНЕРАТОР И СИСТЕМЫ", true));
        boolean turbineOn = be.getToggle(BshuToggle.TURBINE);
        rows.add(new ParameterRow("Турбина: " + (turbineOn ? "ВКЛ" : "ВЫКЛ") + " | обороты " + (turbineOn ? "-" : "0") + " об/мин | температура - | прочность -", false));
        rows.add(new ParameterRow("Генератор: выработка " + (turbineOn ? "-" : "0") + " FE/t", false));
        rows.add(new ParameterRow("Вентиляция: " + delayedSystemText(be, BshuToggle.VENT_1, BshuToggle.VENT_2, BshuToggle.VENT_3, BshuToggle.VENT_4), false));
        rows.add(new ParameterRow("Слив: " + delayedSystemText(be, BshuToggle.CL_1, BshuToggle.CL_2, BshuToggle.CL_3, BshuToggle.CL_4, BshuToggle.SUMP_PUMP), false));
        rows.add(new ParameterRow("Питание отделов: " + powerText(be), false));
        return rows;
    }

    private static String positionText(BlockPos pos) {
        return "[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]";
    }

    private static String temperatureText(float temperatureC) {
        return Math.round(temperatureC) + "°C";
    }

    private static String pressureText(double pressureAtm) {
        return String.format(java.util.Locale.ROOT, "%.1f", pressureAtm);
    }

    private static String integrityText(float integrityPercent) {
        return Math.round(VverThermal.clampIntegrity(integrityPercent)) + "%";
    }

    private static String delayedSystemText(BshuBlockEntity be, BshuToggle... toggles) {
        int enabled = 0;
        boolean starting = false;
        boolean failed = false;
        long gameTime = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        for (BshuToggle toggle : toggles) {
            if (!be.getToggle(toggle)) {
                continue;
            }
            enabled++;
            BshuLampState state = be.getDelayedFailureState(toggle, gameTime);
            if (state == BshuLampState.RED) {
                failed = true;
            } else if (state == BshuLampState.YELLOW) {
                starting = true;
            }
        }
        String state = failed ? "отказ" : starting ? "запуск" : enabled > 0 ? "включено" : "выключено";
        return state + " | " + enabled + "/" + toggles.length;
    }

    private static void drawErrors(Graphics2D g, BshuBlockEntity be) {
        header(g, 512, "ТАБЛО ОШИБОК", be.hasUnacknowledgedErrors() ? "ТРЕБУЕТСЯ КВИТИРОВАНИЕ" : "КОНТРОЛЬ");
        long active = be.computeActiveErrorMask();
        long acknowledged = be.getAcknowledgedErrorMask();
        long time = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        boolean phase = (time / 10L) % 2L == 0L;
        BshuError[] errors = BshuError.values();
        int cols = 6;
        int tileW = 79;
        int tileH = 27;
        for (int i = 0; i < errors.length; i++) {
            int x = 9 + (i % cols) * (tileW + 4);
            int y = 41 + (i / cols) * (tileH + 3);
            boolean isActive = (active & errors[i].bit()) != 0L;
            boolean isAck = (acknowledged & errors[i].bit()) != 0L;
            boolean critical = be.isErrorCritical(errors[i]);
            Color fill = new Color(12, 21, 28);
            Color border = new Color(35, 55, 68);
            Color label = new Color(91, 112, 126);
            if (isActive && (!isAck && !phase)) {
                fill = new Color(39, 24, 24);
            } else if (isActive) {
                fill = critical ? new Color(125, 29, 29) : new Color(122, 83, 18);
                border = critical ? RED : AMBER;
                label = TEXT;
            }
            g.setColor(fill);
            g.fillRect(x, y, tileW, tileH);
            g.setColor(border);
            g.drawRect(x, y, tileW - 1, tileH - 1);
            drawWrappedCenter(g, errors[i].label(), x + tileW / 2, y + tileH / 2, tileW - 6, 8, label);
        }
        text(g, "Мигание = не квитировано | ровный свет = подтверждено кнопкой KVITIR", 10, 201, 9, MUTED, false, false);
    }

    private static void drawControl(Graphics2D g, BshuBlockEntity be) {
        boolean focused = BshuClientState.isFocused(be.getBlockPos(), BshuScreenType.CONTROL);
        String value = focused ? BshuClientState.getFocusText() : String.valueOf(be.getLastRodInput());
        text(g, "СУЗ", 14, 28, 17, CYAN, true, false);
        text(g, selectedGroupsText(be), 370, 27, 13, MUTED, false, true);
        g.setColor(focused ? new Color(18, 48, 43) : PANEL);
        g.fillRect(14, 38, 356, 58);
        g.setColor(focused ? GREEN : GRID);
        g.drawRect(14, 38, 355, 57);
        String shown = value.isEmpty() ? "___" : value;
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            shown += "_";
        }
        centerText(g, shown + " %", 192, 82, 38, inputColor(value), true);
        centerText(g, focused ? "Enter - применить и выйти" : "ПКМ - ввод процента выбранных групп", 192, 111, 12, MUTED, false);
    }

    private static void drawGcn(Graphics2D g, BshuBlockEntity be) {
        boolean focused = BshuClientState.isFocused(be.getBlockPos(), BshuScreenType.GCN);
        int selected = BshuClientState.getFocusField();
        for (int i = 0; i < 3; i++) {
            int y = 12 + i * 166;
            boolean isSelected = focused && selected == i;
            g.setColor(isSelected ? new Color(18, 48, 43) : PANEL);
            g.fillRect(10, y, 140, 150);
            g.setColor(isSelected ? GREEN : GRID);
            g.drawRect(10, y, 139, 149);
            centerText(g, "ГЦН-" + (i + 1), 80, y + 30, 18, TEXT, true);
            String value = isSelected ? BshuClientState.getFocusText() : String.valueOf(be.getGcnSpeed(i));
            String shown = value.isEmpty() ? "___" : value;
            if (isSelected && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                shown += "_";
            }
            centerText(g, shown, 80, y + 83, 34, inputColor(value), true);
            centerText(g, "%", 80, y + 108, 16, MUTED, false);
            BshuToggle toggle = i == 0 ? BshuToggle.GCN_1 : i == 1 ? BshuToggle.GCN_2 : BshuToggle.GCN_3;
            centerText(g, be.getToggle(toggle) ? "ПИТАНИЕ ВКЛ" : "ПИТАНИЕ ВЫКЛ", 80, y + 134, 11, be.getToggle(toggle) ? GREEN : MUTED, true);
        }
    }

    private static Color inputColor(String value) {
        if (value == null || value.isEmpty()) {
            return MUTED;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 && parsed <= 100 ? TEXT : RED;
        } catch (NumberFormatException ignored) {
            return RED;
        }
    }

    private static int countLoadedTvs(BshuBlockEntity be) {
        int loaded = 0;
        if (be.getLevel() == null) {
            return 0;
        }
        for (BlockPos pos : be.getTvsPositions()) {
            BlockState state = be.getLevel().getBlockState(pos);
            if (state.is(VverRegistry.TVS_BLOCK.get()) && state.getValue(TvsBlock.HAS_RODS)) {
                loaded++;
            }
        }
        return loaded;
    }

    private static int countToggle(BshuBlockEntity be, BshuToggle... toggles) {
        int count = 0;
        for (BshuToggle toggle : toggles) {
            if (be.getToggle(toggle)) {
                count++;
            }
        }
        return count;
    }

    private static int countPairs(BshuBlockEntity be, String prefix) {
        int count = 0;
        for (int i = 1; i <= 6; i++) {
            BshuToggle toggle = BshuToggle.byBone(prefix + i);
            if (toggle != null && be.getToggle(toggle)) {
                count++;
            }
        }
        return count;
    }

    private static String selectedGroupsText(BshuBlockEntity be) {
        if (be.allRodBanksSelected()) {
            return "группы: ВСЕ";
        }
        List<String> groups = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            if (be.isRodBankSelected(i)) {
                groups.add(String.valueOf(i));
            }
        }
        return groups.isEmpty() ? "группы: -" : "группы: " + String.join(",", groups);
    }

    private static String powerText(BshuBlockEntity be) {
        return (be.isPowered(1) ? "1" : "-") + " | " + (be.isPowered(2) ? "2" : "-") + " | " + (be.isPowered(3) ? "3" : "-");
    }

    private static void node(Graphics2D g, int x, int y, int w, int h, String title, String subtitle, boolean active) {
        g.setColor(active ? PANEL_LIGHT : PANEL);
        g.fillRect(x, y, w, h);
        g.setColor(active ? CYAN : GRID);
        g.drawRect(x, y, w - 1, h - 1);
        drawWrappedCenter(g, title, x + w / 2, y + h / 2 - 8, w - 8, 13, TEXT);
        centerText(g, trimToWidth(g, subtitle, w - 8, 10), x + w / 2, y + h - 10, 10, MUTED, false);
    }

    private static void pill(Graphics2D g, int x, int y, int w, int h, String label, boolean active) {
        pillStatus(g, x, y, w, h, label, active ? 1 : 0);
    }

    private static void pillStatus(Graphics2D g, int x, int y, int w, int h, String label, int status) {
        Color fill = switch (status) {
            case 1 -> new Color(20, 70, 45);
            case 2 -> new Color(105, 77, 16);
            case 3 -> new Color(105, 25, 25);
            default -> new Color(14, 25, 33);
        };
        Color border = switch (status) {
            case 1 -> GREEN;
            case 2 -> AMBER;
            case 3 -> RED;
            default -> GRID;
        };
        g.setColor(fill);
        g.fillRect(x, y, w, h);
        g.setColor(border);
        g.drawRect(x, y, w - 1, h - 1);
        centerText(g, label, x + w / 2, y + 16, 11, status == 0 ? MUTED : TEXT, true);
    }

    private static boolean isDelayedFailureToggle(BshuToggle toggle) {
        return switch (toggle) {
            case CL_1, CL_2, CL_3, CL_4, VENT_1, VENT_2, VENT_3, VENT_4, SUMP_PUMP -> true;
            default -> false;
        };
    }

    private static void arrow(Graphics2D g, int x1, int y, int x2, int y2, boolean active) {
        g.setColor(active ? GREEN : GRID);
        g.drawLine(x1, y, x2, y2);
        g.drawLine(x2, y2, x2 - 6, y2 - 4);
        g.drawLine(x2, y2, x2 - 6, y2 + 4);
    }

    private static void text(Graphics2D g, String value, int x, int y, int size, Color color, boolean bold, boolean rightAlign) {
        g.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        g.setColor(color);
        int drawX = rightAlign ? x - g.getFontMetrics().stringWidth(value) : x;
        g.drawString(value, drawX, y);
    }

    private static void centerText(Graphics2D g, String value, int centerX, int y, int size, Color color, boolean bold) {
        g.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        g.setColor(color);
        int x = centerX - g.getFontMetrics().stringWidth(value) / 2;
        g.drawString(value, x, y);
    }

    private static String trimToWidth(Graphics2D g, String value, int maxWidth, int size) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(value) <= maxWidth) {
            return value;
        }
        String result = value;
        while (result.length() > 3 && metrics.stringWidth(result + "…") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private static void drawWrappedCenter(Graphics2D g, String value, int centerX, int centerY, int maxWidth, int size, Color color) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        FontMetrics metrics = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        String current = "";
        for (String word : value.split(" ")) {
            String next = current.isEmpty() ? word : current + " " + word;
            if (metrics.stringWidth(next) <= maxWidth || current.isEmpty()) {
                current = next;
            } else {
                lines.add(current);
                current = word;
            }
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        int startY = centerY - (lines.size() - 1) * (size + 2) / 2 + size / 3;
        g.setColor(color);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            g.drawString(line, centerX - metrics.stringWidth(line) / 2, startY + i * (size + 2));
        }
    }

    private static Color darken(Color color, float factor) {
        return new Color(
                Math.max(0, Math.min(255, Math.round(color.getRed() * factor))),
                Math.max(0, Math.min(255, Math.round(color.getGreen() * factor))),
                Math.max(0, Math.min(255, Math.round(color.getBlue() * factor)))
        );
    }
}
