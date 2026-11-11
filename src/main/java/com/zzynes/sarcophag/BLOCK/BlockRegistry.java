package com.zzynes.sarcophag.BLOCK;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockRegistry {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SarcophagMod.MOD_ID);

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SarcophagMod.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SarcophagMod.MOD_ID);

    public static final RegistryObject<Item> CLOTH_WHITE = ITEMS.register("cloth_white",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> CLOTH_GREEN = ITEMS.register("cloth_green",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> CLOTH_SAND = ITEMS.register("cloth_sand",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Block> SANDBAG_DIRT_GREEN = BLOCKS.register("sandbag_dirt_green",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT).sound(SoundType.GRAVEL).noOcclusion()));

    public static final RegistryObject<Block> SANDBAG_SAND_GREEN = BLOCKS.register("sandbag_sand_green",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.SAND).sound(SoundType.SAND).noOcclusion()));

    public static final RegistryObject<Block> SANDBAG_DIRT_SAND = BLOCKS.register("sandbag_dirt_sand",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT).sound(SoundType.GRAVEL).noOcclusion()));

    public static final RegistryObject<Block> SANDBAG_SAND_SAND = BLOCKS.register("sandbag_sand_sand",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.SAND).sound(SoundType.SAND).noOcclusion()));

    public static final RegistryObject<Item> SANDBAG_DIRT_GREEN_ITEM = ITEMS.register("sandbag_dirt_green",
            () -> new BlockItem(SANDBAG_DIRT_GREEN.get(), new Item.Properties()));

    public static final RegistryObject<Item> SANDBAG_SAND_GREEN_ITEM = ITEMS.register("sandbag_sand_green",
            () -> new BlockItem(SANDBAG_SAND_GREEN.get(), new Item.Properties()));

    public static final RegistryObject<Item> SANDBAG_DIRT_SAND_ITEM = ITEMS.register("sandbag_dirt_sand",
            () -> new BlockItem(SANDBAG_DIRT_SAND.get(), new Item.Properties()));

    public static final RegistryObject<Item> SANDBAG_SAND_SAND_ITEM = ITEMS.register("sandbag_sand_sand",
            () -> new BlockItem(SANDBAG_SAND_SAND.get(), new Item.Properties()));

    public static final RegistryObject<Block> BUNKER_CONCRETE =
            registerConcrete("bunker_concrete", MapColor.STONE, 5.0f, 12.0f);

    public static final RegistryObject<Block> BUNKER_CONCRETE_DENSE =
            registerConcrete("bunker_concrete_dense", MapColor.COLOR_GRAY, 7.0f, 18.0f);

    public static final RegistryObject<Block> BUNKER_REINFORCED_CONCRETE =
            registerConcrete("bunker_reinforced_concrete", MapColor.STONE, 8.0f, 24.0f);

    public static final RegistryObject<Block> BUNKER_REINFORCED_CONCRETE_DENSE =
            registerConcrete("bunker_reinforced_concrete_dense", MapColor.COLOR_GRAY, 10.0f, 32.0f);

    public static final RegistryObject<Block> BUNKER_SIGNAL_CONCRETE_UP =
            registerConcrete("bunker_signal_concrete_up", MapColor.STONE, 5.0f, 12.0f);

    public static final RegistryObject<Block> BUNKER_SIGNAL_CONCRETE_DOWN =
            registerConcrete("bunker_signal_concrete_down", MapColor.STONE, 5.0f, 12.0f);

    public static final RegistryObject<Block> BUNKER_SIGNAL_CONCRETE_UP_DENSE =
            registerConcrete("bunker_signal_concrete_up_dense", MapColor.COLOR_GRAY, 7.0f, 18.0f);

    public static final RegistryObject<Block> BUNKER_SIGNAL_CONCRETE_DOWN_DENSE =
            registerConcrete("bunker_signal_concrete_down_dense", MapColor.COLOR_GRAY, 7.0f, 18.0f);

    public static final RegistryObject<Block> BUNKER_SIGNAL_REINFORCED_CONCRETE_UP =
            registerConcrete("bunker_signal_reinforced_concrete_up", MapColor.STONE, 8.0f, 24.0f);

    public static final RegistryObject<Block> BUNKER_SIGNAL_REINFORCED_CONCRETE_DOWN =
            registerConcrete("bunker_signal_reinforced_concrete_down", MapColor.STONE, 8.0f, 24.0f);

    public static final RegistryObject<Block> BUNKER_SIGNAL_REINFORCED_CONCRETE_UP_DENSE =
            registerConcrete("bunker_signal_reinforced_concrete_up_dense", MapColor.COLOR_GRAY, 10.0f, 32.0f);

    public static final RegistryObject<Block> BUNKER_SIGNAL_REINFORCED_CONCRETE_DOWN_DENSE =
            registerConcrete("bunker_signal_reinforced_concrete_down_dense", MapColor.COLOR_GRAY, 10.0f, 32.0f);

    public static final RegistryObject<Block> BUNKER_CONCRETE_SLAB =
            registerConcreteSlab("bunker_concrete_slab", MapColor.STONE, 5.0f, 12.0f);

    public static final RegistryObject<Block> BUNKER_CONCRETE_STAIRS =
            registerConcreteStairs("bunker_concrete_stairs", BUNKER_CONCRETE, MapColor.STONE, 5.0f, 12.0f);

    public static final RegistryObject<Block> BUNKER_CONCRETE_DENSE_SLAB =
            registerConcreteSlab("bunker_concrete_dense_slab", MapColor.COLOR_GRAY, 7.0f, 18.0f);

    public static final RegistryObject<Block> BUNKER_CONCRETE_DENSE_STAIRS =
            registerConcreteStairs("bunker_concrete_dense_stairs", BUNKER_CONCRETE_DENSE, MapColor.COLOR_GRAY, 7.0f, 18.0f);

    public static final RegistryObject<Block> BUNKER_REINFORCED_CONCRETE_SLAB =
            registerConcreteSlab("bunker_reinforced_concrete_slab", MapColor.STONE, 8.0f, 24.0f);

    public static final RegistryObject<Block> BUNKER_REINFORCED_CONCRETE_STAIRS =
            registerConcreteStairs("bunker_reinforced_concrete_stairs", BUNKER_REINFORCED_CONCRETE, MapColor.STONE, 8.0f, 24.0f);

    public static final RegistryObject<Block> BUNKER_REINFORCED_CONCRETE_DENSE_SLAB =
            registerConcreteSlab("bunker_reinforced_concrete_dense_slab", MapColor.COLOR_GRAY, 10.0f, 32.0f);

    public static final RegistryObject<Block> BUNKER_REINFORCED_CONCRETE_DENSE_STAIRS =
            registerConcreteStairs("bunker_reinforced_concrete_dense_stairs", BUNKER_REINFORCED_CONCRETE_DENSE, MapColor.COLOR_GRAY, 10.0f, 32.0f);

    public static final RegistryObject<Block> METAL_BEAM_SUPPORT = registerConcreteBlock(
            "metal_beam_support", MetalBeamSupportBlock::new);

    public static final RegistryObject<Block> METAL_BEAM_VERTICAL = registerConcreteBlock(
            "metal_beam_vertical", MetalBeamVerticalBlock::new);

    public static final RegistryObject<Block> BATTERY_CABINET = BLOCKS.register("battery_cabinet",
            BatteryCabinetBlock::new);

    public static final RegistryObject<Item> BATTERY_CABINET_ITEM = ITEMS.register("battery_cabinet",
            () -> new BatteryCabinetBlockItem(BATTERY_CABINET.get()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BatteryCabinetBlockEntity>> BATTERY_CABINET_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("battery_cabinet", () -> BlockEntityType.Builder.of(BatteryCabinetBlockEntity::new, BATTERY_CABINET.get()).build(null));

    private static RegistryObject<Block> registerConcrete(String name, MapColor mapColor, float destroyTime, float explosionResistance) {
        return registerConcreteBlock(name, () -> new Block(concreteProperties(mapColor, destroyTime, explosionResistance)));
    }

    private static RegistryObject<Block> registerConcreteSlab(String name, MapColor mapColor, float destroyTime, float explosionResistance) {
        return registerConcreteBlock(name, () -> new SlabBlock(concreteProperties(mapColor, destroyTime, explosionResistance)));
    }

    private static RegistryObject<Block> registerConcreteStairs(String name, RegistryObject<Block> base, MapColor mapColor, float destroyTime, float explosionResistance) {
        return registerConcreteBlock(name, () -> new StairBlock(
                () -> base.get().defaultBlockState(),
                concreteProperties(mapColor, destroyTime, explosionResistance)));
    }

    private static RegistryObject<Block> registerConcreteBlock(String name, Supplier<Block> supplier) {
        RegistryObject<Block> block = BLOCKS.register(name, supplier);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static BlockBehaviour.Properties concreteProperties(MapColor mapColor, float destroyTime, float explosionResistance) {
        return BlockBehaviour.Properties.copy(Blocks.STONE)
                .mapColor(mapColor)
                .sound(SoundType.STONE)
                .strength(destroyTime, explosionResistance)
                .requiresCorrectToolForDrops();
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        BLOCKS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}