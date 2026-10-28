package com.zzynes.flydrone.VVER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Map;

@Mod.EventBusSubscriber(modid = FlyDroneMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class VverClient {

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(SuzBlockEntityRenderer.RODS_MODEL_LOC);
        event.register(SuzBlockEntityRenderer.RODS_LOD_MODEL_LOC);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        SuzBlockEntityRenderer.invalidateMeshCache();
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (int part = 0; part < SuzBlock.STRUCTURE_HEIGHT; part++) {
            ModelResourceLocation key = new ModelResourceLocation(FlyDroneMod.MOD_ID, "vver_suz", "part=" + part);
            BakedModel existing = models.get(key);
            if (existing != null) {
                models.put(key, new SuzFrameBakedModel(existing));
            }
        }
        for (int part = 0; part < TvsBlock.STRUCTURE_HEIGHT; part++) {
            for (boolean hasRods : new boolean[]{false, true}) {
                ModelResourceLocation key = new ModelResourceLocation(FlyDroneMod.MOD_ID, "vver_tvs", "has_rods=" + hasRods + ",part=" + part);
                BakedModel existing = models.get(key);
                if (existing != null) {
                    models.put(key, new SuzFrameBakedModel(existing));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(VverRegistry.TVS_MENU.get(), TvsScreen::new));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(VverRegistry.SUZ_BLOCK_ENTITY.get(), SuzBlockEntityRenderer::new);
    }
}
