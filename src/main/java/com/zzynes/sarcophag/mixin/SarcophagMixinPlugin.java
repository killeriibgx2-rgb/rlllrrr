package com.zzynes.sarcophag.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class SarcophagMixinPlugin implements IMixinConfigPlugin {

    private static final String VANILLA_ORIGIN_MIXIN = "com.zzynes.sarcophag.mixin.LevelRendererVanillaOriginMixin";
    private static final String[] REPLACEMENT_RENDERER_MODS = {
            "embeddium",
            "rubidium",
            "sodium",
            "xenon",
            "vulkanmod",
            "oculus",
            "iris"
    };

    private Boolean replacementRendererLoaded;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!VANILLA_ORIGIN_MIXIN.equals(mixinClassName)) {
            return true;
        }
        return !isReplacementRendererLoaded();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private boolean isReplacementRendererLoaded() {
        if (this.replacementRendererLoaded != null) {
            return this.replacementRendererLoaded;
        }
        boolean loaded = false;
        try {
            LoadingModList loadingModList = FMLLoader.getLoadingModList();
            if (loadingModList == null) {
                return false;
            }
            for (String modId : REPLACEMENT_RENDERER_MODS) {
                if (loadingModList.getModFileById(modId) != null) {
                    loaded = true;
                    break;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        this.replacementRendererLoaded = loaded;
        return loaded;
    }
}
