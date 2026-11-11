package com.zzynes.sarcophag.VVER;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class SuzClientHooks {

    private SuzClientHooks() {
    }

    public static void openScreen(BlockPos basePos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        mc.setScreen(new SuzControlScreen(basePos));
    }
}
