package com.zzynes.sarcophag.VVER;

import com.zzynes.sarcophag.DRONE.SarcophagMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class VverRegistry {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SarcophagMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SarcophagMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SarcophagMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, SarcophagMod.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SarcophagMod.MOD_ID);

    public static final RegistryObject<Block> SUZ_BLOCK =
            BLOCKS.register("vver_suz", SuzBlock::new);

    public static final RegistryObject<Item> SUZ_ITEM =
            ITEMS.register("vver_suz", () -> new BlockItem(SUZ_BLOCK.get(), new Item.Properties()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<SuzBlockEntity>> SUZ_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("vver_suz", () -> BlockEntityType.Builder.of(SuzBlockEntity::new, SUZ_BLOCK.get()).build(null));

    public static final RegistryObject<Block> TVS_BLOCK =
            BLOCKS.register("vver_tvs", TvsBlock::new);

    public static final RegistryObject<Item> TVS_ITEM =
            ITEMS.register("vver_tvs", () -> new BlockItem(TVS_BLOCK.get(), new Item.Properties()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TvsBlockEntity>> TVS_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("vver_tvs", () -> BlockEntityType.Builder.of(TvsBlockEntity::new, TVS_BLOCK.get()).build(null));

    public static final RegistryObject<MenuType<TvsMenu>> TVS_MENU =
            MENUS.register("vver_tvs", () -> new MenuType<>((IContainerFactory<TvsMenu>) (windowId, inv, data) -> new TvsMenu(windowId, inv, data), FeatureFlags.VANILLA_SET));

    public static final RegistryObject<Block> BSHU_BLOCK =
            BLOCKS.register("vver_bshu", BshuBlock::new);

    public static final RegistryObject<Item> BSHU_ITEM =
            ITEMS.register("vver_bshu", () -> new BshuBlockItem(BSHU_BLOCK.get()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BshuBlockEntity>> BSHU_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("vver_bshu", () -> BlockEntityType.Builder.of(BshuBlockEntity::new, BSHU_BLOCK.get()).build(null));

    public static final RegistryObject<Block> GCN_BLOCK =
            BLOCKS.register("vver_gcn", GcnBlock::new);

    public static final RegistryObject<Item> GCN_ITEM =
            ITEMS.register("vver_gcn", () -> new GcnBlockItem(GCN_BLOCK.get()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<GcnBlockEntity>> GCN_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("vver_gcn", () -> BlockEntityType.Builder.of(GcnBlockEntity::new, GCN_BLOCK.get()).build(null));

    public static final RegistryObject<Block> STEAM_GENERATOR_BLOCK =
            BLOCKS.register("vver_steam_generator", SteamGeneratorBlock::new);

    public static final RegistryObject<Item> STEAM_GENERATOR_ITEM =
            ITEMS.register("vver_steam_generator", () -> new SteamGeneratorBlockItem(STEAM_GENERATOR_BLOCK.get()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<SteamGeneratorBlockEntity>> STEAM_GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("vver_steam_generator", () -> BlockEntityType.Builder.of(SteamGeneratorBlockEntity::new, STEAM_GENERATOR_BLOCK.get()).build(null));

    public static final RegistryObject<Block> HULL_WALL_BLOCK =
            BLOCKS.register("vver_reactor_hull", () -> new HullBlock(HullType.WALL));

    public static final RegistryObject<Item> HULL_WALL_ITEM =
            ITEMS.register("vver_reactor_hull", () -> new BlockItem(HULL_WALL_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Block> HULL_BOTTOM_BLOCK =
            BLOCKS.register("vver_reactor_hull_bottom", () -> new HullBlock(HullType.BOTTOM));

    public static final RegistryObject<Item> HULL_BOTTOM_ITEM =
            ITEMS.register("vver_reactor_hull_bottom", () -> new BlockItem(HULL_BOTTOM_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Block> HULL_LID_BLOCK =
            BLOCKS.register("vver_reactor_hull_lid", () -> new HullBlock(HullType.LID));

    public static final RegistryObject<Item> HULL_LID_ITEM =
            ITEMS.register("vver_reactor_hull_lid", () -> new BlockItem(HULL_LID_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Block> HULL_NOZZLE_BLOCK =
            BLOCKS.register("vver_reactor_hull_nozzle", HullNozzleBlock::new);

    public static final RegistryObject<Item> HULL_NOZZLE_ITEM =
            ITEMS.register("vver_reactor_hull_nozzle", () -> new BlockItem(HULL_NOZZLE_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Block> PIPE_BLOCK =
            BLOCKS.register("vver_pipe", PipeBlock::new);

    public static final RegistryObject<Item> PIPE_ITEM =
            ITEMS.register("vver_pipe", () -> new BlockItem(PIPE_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Block> CORNER_PIPE_BLOCK =
            BLOCKS.register("vver_corner_pipe", CornerPipeBlock::new);

    public static final RegistryObject<Item> CORNER_PIPE_ITEM =
            ITEMS.register("vver_corner_pipe", () -> new BlockItem(CORNER_PIPE_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Block> TEE_PIPE_BLOCK =
            BLOCKS.register("vver_t_shaped_pipe", TeePipeBlock::new);

    public static final RegistryObject<Item> TEE_PIPE_ITEM =
            ITEMS.register("vver_t_shaped_pipe", () -> new BlockItem(TEE_PIPE_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Block> CROSS_PIPE_BLOCK =
            BLOCKS.register("vver_x_shaped_pipe", CrossPipeBlock::new);

    public static final RegistryObject<Item> CROSS_PIPE_ITEM =
            ITEMS.register("vver_x_shaped_pipe", () -> new BlockItem(CROSS_PIPE_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Block> VALVE_BLOCK =
            BLOCKS.register("vver_valve", ValveBlock::new);

    public static final RegistryObject<Item> VALVE_ITEM =
            ITEMS.register("vver_valve", () -> new BlockItem(VALVE_BLOCK.get(), new Item.Properties()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ValveBlockEntity>> VALVE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("vver_valve", () -> BlockEntityType.Builder.of(ValveBlockEntity::new, VALVE_BLOCK.get()).build(null));

    public static final RegistryObject<SoundEvent> VALVE_TURN_SOUND =
            SOUND_EVENTS.register("block.vver_valve.turn",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SarcophagMod.MOD_ID, "block.vver_valve.turn")));

    public static final RegistryObject<Item> CORE_FILLER_ITEM =
            ITEMS.register("vver_core_filler", CoreFillerItem::new);

    public static final RegistryObject<Item> PRESSURE_STICK_ITEM =
            ITEMS.register("vver_pressure_stick", PressureStickItem::new);

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENUS.register(eventBus);
        SOUND_EVENTS.register(eventBus);
        eventBus.addListener((FMLCommonSetupEvent event) -> VverNetwork.register());
        eventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            ResourceKey<CreativeModeTab> sarcophagTab = ResourceKey.create(
                    Registries.CREATIVE_MODE_TAB,
                    new ResourceLocation(SarcophagMod.MOD_ID, "sarcophag_tab")
            );
            if (event.getTabKey().equals(sarcophagTab)) {
                event.accept(BSHU_ITEM.get());
                event.accept(GCN_ITEM.get());
                event.accept(STEAM_GENERATOR_ITEM.get());
                event.accept(HULL_WALL_ITEM.get());
                event.accept(HULL_BOTTOM_ITEM.get());
                event.accept(HULL_LID_ITEM.get());
                event.accept(HULL_NOZZLE_ITEM.get());
                event.accept(PIPE_ITEM.get());
                event.accept(CORNER_PIPE_ITEM.get());
                event.accept(TEE_PIPE_ITEM.get());
                event.accept(CROSS_PIPE_ITEM.get());
                event.accept(VALVE_ITEM.get());
                event.accept(CORE_FILLER_ITEM.get());
                event.accept(PRESSURE_STICK_ITEM.get());
            }
        });
    }
}
