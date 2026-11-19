package com.zzynes.sarcophag.POWER;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class PowerRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SarcophagMod.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SarcophagMod.MOD_ID);

    public static final RegistryObject<Item> CABLE_CONTROL = ITEMS.register("cable_control",
            () -> new CableItem(CableTier.CONTROL, new Item.Properties()));
    public static final RegistryObject<Item> CABLE_POWER = ITEMS.register("cable_power",
            () -> new CableItem(CableTier.POWER, new Item.Properties()));
    public static final RegistryObject<Item> CABLE_MAIN = ITEMS.register("cable_main",
            () -> new CableItem(CableTier.MAIN, new Item.Properties()));

    public static final RegistryObject<EntityType<CableRunEntity>> CABLE_RUN = ENTITIES.register("cable_run",
            () -> EntityType.Builder.<CableRunEntity>of(CableRunEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(12)
                    .updateInterval(20)
                    .build("cable_run"));

    private PowerRegistry() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        ENTITIES.register(eventBus);
    }
}
