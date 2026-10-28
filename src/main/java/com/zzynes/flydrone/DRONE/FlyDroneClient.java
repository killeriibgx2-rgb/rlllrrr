package com.zzynes.flydrone.DRONE;

import com.zzynes.flydrone.DRONE.entity.FpvDroneRenderer;
import com.zzynes.flydrone.DRONE.entity.RpgProjectileEntity;
import com.zzynes.flydrone.DRONE.entity.client.RpgProjectileRenderer;
import com.zzynes.flydrone.DRONE.entity.client.RpgShellModel;
import com.zzynes.flydrone.DRONE.entity.client.Ci4Model;
import com.zzynes.flydrone.DRONE.BlastParticleTypes;
import com.zzynes.flydrone.DRONE.client.BlastParticle;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import com.zzynes.flydrone.DRONE.init.ModEntityTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FlyDroneMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class FlyDroneClient {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.FPV_DRONE.get(), FpvDroneRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.RPG_PROJECTILE.get(), RpgProjectileRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BlastParticleTypes.BLAST_CORE.get(), set -> new BlastParticle.Provider(set, BlastParticle.Style.CORE));
        event.registerSpriteSet(BlastParticleTypes.BLAST_FLARE.get(), set -> new BlastParticle.Provider(set, BlastParticle.Style.FLARE));
        event.registerSpriteSet(BlastParticleTypes.BLAST_SMOKE.get(), set -> new BlastParticle.Provider(set, BlastParticle.Style.SMOKE));
        event.registerSpriteSet(BlastParticleTypes.BLAST_RING.get(), set -> new BlastParticle.Provider(set, BlastParticle.Style.RING));
        event.registerSpriteSet(BlastParticleTypes.BLAST_SPARK.get(), set -> new BlastParticle.Provider(set, BlastParticle.Style.SPARK));
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RpgShellModel.LAYER_LOCATION, RpgShellModel::createBodyLayer);
        event.registerLayerDefinition(Ci4Model.LAYER_LOCATION, Ci4Model::createBodyLayer);
    }
}