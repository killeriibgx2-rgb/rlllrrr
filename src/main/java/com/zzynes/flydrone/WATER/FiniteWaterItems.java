package com.zzynes.flydrone.WATER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FiniteWaterItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FlyDroneMod.MOD_ID);

    public static final RegistryObject<Item> PARTIAL_WATER_BUCKET =
            ITEMS.register("partial_water_bucket", PartialWaterBucketItem::new);

    public static final RegistryObject<Item> PARTIAL_RADIOACTIVE_WATER_BUCKET =
            ITEMS.register("partial_radioactive_water_bucket", () -> new PartialWaterBucketItem(FiniteWaterManager.FiniteFluidKind.RADIOACTIVE_WATER));

    private FiniteWaterItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
