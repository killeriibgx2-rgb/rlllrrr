package com.zzynes.flydrone.VVER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
            DeferredRegister.create(ForgeRegistries.BLOCKS, FlyDroneMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FlyDroneMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FlyDroneMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, FlyDroneMod.MOD_ID);

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

    public static final RegistryObject<Block> BSHU_PART_BLOCK =
            BLOCKS.register("vver_bshu_part", BshuPartBlock::new);

    public static final RegistryObject<Item> BSHU_ITEM =
            ITEMS.register("vver_bshu", () -> new BshuBlockItem(BSHU_BLOCK.get()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BshuBlockEntity>> BSHU_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("vver_bshu", () -> BlockEntityType.Builder.of(BshuBlockEntity::new, BSHU_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENUS.register(eventBus);
        eventBus.addListener((FMLCommonSetupEvent event) -> VverNetwork.register());
        eventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            ResourceKey<CreativeModeTab> flyDroneTab = ResourceKey.create(
                    Registries.CREATIVE_MODE_TAB,
                    new ResourceLocation(FlyDroneMod.MOD_ID, "fly_drone_tab")
            );
            if (event.getTabKey().equals(flyDroneTab)) {
                event.accept(BSHU_ITEM.get());
            }
        });
    }
}
