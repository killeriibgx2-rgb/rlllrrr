package com.zzynes.sarcophag.DRONE;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BlastParticleTypes {

    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, SarcophagMod.MOD_ID);

    public static final RegistryObject<SimpleParticleType> BLAST_CORE = register("blast_core");
    public static final RegistryObject<SimpleParticleType> BLAST_FLARE = register("blast_flare");
    public static final RegistryObject<SimpleParticleType> BLAST_SMOKE = register("blast_smoke");
    public static final RegistryObject<SimpleParticleType> BLAST_RING = register("blast_ring");
    public static final RegistryObject<SimpleParticleType> BLAST_SPARK = register("blast_spark");

    private BlastParticleTypes() {
    }

    private static RegistryObject<SimpleParticleType> register(String id) {
        return PARTICLE_TYPES.register(id, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}
