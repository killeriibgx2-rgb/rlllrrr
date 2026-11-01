package com.zzynes.flydrone.VVER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class SteamGeneratorGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static final ResourceLocation MODEL = new ResourceLocation(FlyDroneMod.MOD_ID, "geo/block/steam_generator.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlyDroneMod.MOD_ID, "textures/block/steam_generator.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(FlyDroneMod.MOD_ID, "animations/block/steam_generator.animation.json");
    private static final double NEEDLE_SWEEP_DEGREES = 210.0;

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return ANIMATIONS;
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        double primaryPressure = 0.0;
        double secondaryPressure = 0.0;
        if (animatable instanceof SteamGeneratorBlockEntity blockEntity) {
            primaryPressure = blockEntity.getDisplayPressureAtm(SteamGeneratorBlock.Circuit.PRIMARY);
            secondaryPressure = blockEntity.getDisplayPressureAtm(SteamGeneratorBlock.Circuit.SECONDARY);
        }
        setNeedle("manumetr1k", primaryPressure, SteamGeneratorBlockEntity.PRIMARY_GAUGE_MAX_ATM);
        setNeedle("manumetr2k", secondaryPressure, SteamGeneratorBlockEntity.SECONDARY_GAUGE_MAX_ATM);
    }

    private void setNeedle(String boneName, double pressureAtm, double maxPressureAtm) {
        double fraction = Mth.clamp(pressureAtm / maxPressureAtm, 0.0, 1.0);
        float angle = (float) Math.toRadians(-NEEDLE_SWEEP_DEGREES * fraction);
        getBone(boneName).ifPresent(bone -> bone.setRotX(angle));
    }
}
