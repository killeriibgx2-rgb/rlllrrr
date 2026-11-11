package com.zzynes.sarcophag.BLOCK;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class BatteryCabinetGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static final ResourceLocation MODEL = new ResourceLocation(SarcophagMod.MOD_ID, "geo/block/battery_cabinet.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(SarcophagMod.MOD_ID, "textures/block/battery_cabinet.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(SarcophagMod.MOD_ID, "animations/block/battery_cabinet.animation.json");
    private static final float DOOR_OPEN_RADIANS = (float) Math.toRadians(80.0);

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
        BatteryCabinetBlockEntity cabinet = animatable instanceof BatteryCabinetBlockEntity blockEntity ? blockEntity : null;
        for (int i = 0; i < BatteryCabinetSlots.SLOT_COUNT; i++) {
            boolean installed = cabinet != null && cabinet.isInstalled(i);
            String boneName = BatteryCabinetSlots.SLOTS[i].boneName();
            getBone(boneName).ifPresent(bone -> bone.setHidden(!installed));
        }
        float progress = cabinet == null ? 0.0f : cabinet.getDoorProgress();
        getBone("door1").ifPresent(bone -> bone.setRotY(-DOOR_OPEN_RADIANS * progress));
        getBone("door2").ifPresent(bone -> bone.setRotY(DOOR_OPEN_RADIANS * progress));
    }
}
