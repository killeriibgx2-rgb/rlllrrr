package com.zzynes.flydrone.VVER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class BshuGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static final ResourceLocation MODEL = new ResourceLocation(FlyDroneMod.MOD_ID, "geo/block/bshu_full.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlyDroneMod.MOD_ID, "textures/block/bshu_full.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(FlyDroneMod.MOD_ID, "animations/block/bshu_full.animation.json");

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
        getBone("screen").ifPresent(bone -> bone.setHidden(true));
        if (!(animatable instanceof BshuBlockEntity blockEntity)) {
            return;
        }
        for (BshuControl control : BshuControl.values()) {
            if (!control.pressable()) {
                continue;
            }
            getBone(control.boneName()).ifPresent(bone -> {
                float amount = BshuClientState.getPressAmount(blockEntity.getBlockPos(), control);
                Vec3 direction = BshuClientGeometry.getPressDirection(control.boneName());
                double distance = amount * 0.2;
                bone.updatePosition(
                        (float) (-direction.x * distance),
                        (float) (direction.y * distance),
                        (float) (direction.z * distance)
                );
            });
        }
    }
}
