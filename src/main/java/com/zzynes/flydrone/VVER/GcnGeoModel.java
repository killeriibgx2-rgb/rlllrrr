package com.zzynes.flydrone.VVER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class GcnGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static final ResourceLocation MODEL = new ResourceLocation(FlyDroneMod.MOD_ID, "geo/block/gcn.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlyDroneMod.MOD_ID, "textures/block/gcn.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(FlyDroneMod.MOD_ID, "animations/block/gcn.animation.json");

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
}
