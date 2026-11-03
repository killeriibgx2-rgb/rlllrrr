package com.zzynes.flydrone.WATER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FiniteWaterBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FlyDroneMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FlyDroneMod.MOD_ID);

    public static final RegistryObject<Block> DRAIN =
            BLOCKS.register("drain", DrainBlock::new);

    public static final RegistryObject<Item> DRAIN_ITEM =
            ITEMS.register("drain", () -> new BlockItem(DRAIN.get(), new Item.Properties()));

    private FiniteWaterBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
