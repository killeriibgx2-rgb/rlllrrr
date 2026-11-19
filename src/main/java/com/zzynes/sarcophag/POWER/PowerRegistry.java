package com.zzynes.sarcophag.POWER;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class PowerRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SarcophagMod.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SarcophagMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SarcophagMod.MOD_ID);

    public static final RegistryObject<Block> CABLE = BLOCKS.register("cable", CableBlock::new);

    public static final RegistryObject<BlockEntityType<CableBlockEntity>> CABLE_BE = BLOCK_ENTITIES.register("cable",
            () -> BlockEntityType.Builder.of(CableBlockEntity::new, CABLE.get()).build(null));

    public static final RegistryObject<Item> CABLE_CONTROL = ITEMS.register("cable_control",
            () -> new CableItem(CableTier.CONTROL, new Item.Properties()));
    public static final RegistryObject<Item> CABLE_POWER = ITEMS.register("cable_power",
            () -> new CableItem(CableTier.POWER, new Item.Properties()));
    public static final RegistryObject<Item> CABLE_MAIN = ITEMS.register("cable_main",
            () -> new CableItem(CableTier.MAIN, new Item.Properties()));

    private PowerRegistry() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        BLOCKS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}
