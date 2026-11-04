package com.zzynes.flydrone.DRONE.init;

import com.zzynes.flydrone.BLOCK.BlockRegistry;
import com.zzynes.flydrone.Domm.DommRegistry;
import com.zzynes.flydrone.Radio.RadioMod;
import com.zzynes.flydrone.VVER.VverRegistry;
import com.zzynes.flydrone.WATER.FiniteWaterBlocks;
import com.zzynes.flydrone.WATER.FiniteWaterManager;
import com.zzynes.flydrone.WATER.PartialWaterBucketItem;
import com.zzynes.flydrone.WATER.RadioactiveWater;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "fly_drone");

    public static final RegistryObject<CreativeModeTab> FLY_DRONE_TAB = CREATIVE_TABS.register("fly_drone_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.fly_drone"))
                    .icon(() -> new ItemStack(ModItems.BATTERY.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.ALUMINUM_INGOT.get());
                        output.accept(ModItems.LITHIUM_INGOT.get());
                        output.accept(ModItems.COPPER_WIRE.get());
                        output.accept(ModItems.BATTERY.get());
                        output.accept(ModItems.RPG_SHELL.get());
                        output.accept(ModItems.CI4_CHARGE.get());
                        output.accept(ModItems.REMOTE_CONNECTED.get());
                        output.accept(ModItems.REMOTE_DISCONNECTED.get());
                        output.accept(ModItems.ALUMINUM_ORE.get());
                        output.accept(ModItems.LITHIUM_ORE.get());
                        output.accept(ModItems.BATTERY_CELL.get());
                        output.accept(ModItems.CAMERA_FPV.get());
                        output.accept(ModItems.COPPER_PLATE.get());
                        output.accept(ModItems.IRON_PLATE.get());
                        output.accept(ModItems.LITHIUM_PLATE.get());
                        output.accept(ModItems.MICROCONTROLLER.get());
                        output.accept(ModItems.MOTOR_CASING.get());
                        output.accept(ModItems.MOTOR_FPV.get());
                        output.accept(ModItems.MOTOR_SHAFT.get());
                        output.accept(ModItems.MOTOR_WINDING.get());
                        output.accept(ModItems.PROPELLER_FPV.get());
                        output.accept(ModItems.SANDWICH_PANEL.get());
                        output.accept(ModItems.FPV_DRONE_ITEM.get());
                        output.accept(ModItems.STAL_MOLOT.get());
                        output.accept(DommRegistry.DOMM_BODY_ITEM.get());
                        output.accept(DommRegistry.DOMM_INTERFACE_ITEM.get());
                        output.accept(VverRegistry.SUZ_ITEM.get());
                        output.accept(VverRegistry.TVS_ITEM.get());
                        output.accept(ModBlocks.ALUMINUM_BLOCK.get());
                        output.accept(ModBlocks.LITHIUM_BLOCK.get());
                        output.accept(RadioMod.RADIO.get());
                        output.accept(BlockRegistry.CLOTH_WHITE.get());
                        output.accept(BlockRegistry.CLOTH_GREEN.get());
                        output.accept(BlockRegistry.CLOTH_SAND.get());
                        output.accept(BlockRegistry.SANDBAG_DIRT_GREEN_ITEM.get());
                        output.accept(BlockRegistry.SANDBAG_SAND_GREEN_ITEM.get());
                        output.accept(BlockRegistry.SANDBAG_DIRT_SAND_ITEM.get());
                        output.accept(BlockRegistry.SANDBAG_SAND_SAND_ITEM.get());
                        output.accept(BlockRegistry.BUNKER_CONCRETE.get());
                        output.accept(BlockRegistry.BUNKER_CONCRETE_DENSE.get());
                        output.accept(BlockRegistry.BUNKER_REINFORCED_CONCRETE.get());
                        output.accept(BlockRegistry.BUNKER_REINFORCED_CONCRETE_DENSE.get());
                        output.accept(BlockRegistry.BUNKER_CONCRETE_SLAB.get());
                        output.accept(BlockRegistry.BUNKER_CONCRETE_STAIRS.get());
                        output.accept(BlockRegistry.BUNKER_CONCRETE_DENSE_SLAB.get());
                        output.accept(BlockRegistry.BUNKER_CONCRETE_DENSE_STAIRS.get());
                        output.accept(BlockRegistry.BUNKER_REINFORCED_CONCRETE_SLAB.get());
                        output.accept(BlockRegistry.BUNKER_REINFORCED_CONCRETE_STAIRS.get());
                        output.accept(BlockRegistry.BUNKER_REINFORCED_CONCRETE_DENSE_SLAB.get());
                        output.accept(BlockRegistry.BUNKER_REINFORCED_CONCRETE_DENSE_STAIRS.get());
                        output.accept(BlockRegistry.BUNKER_SIGNAL_CONCRETE_UP.get());
                        output.accept(BlockRegistry.BUNKER_SIGNAL_CONCRETE_DOWN.get());
                        output.accept(BlockRegistry.BUNKER_SIGNAL_CONCRETE_UP_DENSE.get());
                        output.accept(BlockRegistry.BUNKER_SIGNAL_CONCRETE_DOWN_DENSE.get());
                        output.accept(BlockRegistry.BUNKER_SIGNAL_REINFORCED_CONCRETE_UP.get());
                        output.accept(BlockRegistry.BUNKER_SIGNAL_REINFORCED_CONCRETE_DOWN.get());
                        output.accept(BlockRegistry.BUNKER_SIGNAL_REINFORCED_CONCRETE_UP_DENSE.get());
                        output.accept(BlockRegistry.BUNKER_SIGNAL_REINFORCED_CONCRETE_DOWN_DENSE.get());
                        output.accept(ModItems.HANDHELD_SIREN.get());
                        output.accept(PartialWaterBucketItem.stackForUnits(5));
                        output.accept(FiniteWaterBlocks.DRAIN_ITEM.get());
                        output.accept(RadioactiveWater.BUCKET.get());
                        output.accept(PartialWaterBucketItem.stackForUnits(5, FiniteWaterManager.FiniteFluidKind.RADIOACTIVE_WATER));
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
