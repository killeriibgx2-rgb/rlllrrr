package com.zzynes.flydrone.WATER;

import com.zzynes.flydrone.DRONE.FlyDroneMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

import java.util.function.Consumer;

public final class RadioactiveWater {
    private static final ResourceLocation STILL_TEXTURE = new ResourceLocation(FlyDroneMod.MOD_ID, "block/radioactive_water_still");
    private static final ResourceLocation FLOWING_TEXTURE = new ResourceLocation(FlyDroneMod.MOD_ID, "block/radioactive_water_flow");
    private static final ResourceLocation OVERLAY_TEXTURE = new ResourceLocation(FlyDroneMod.MOD_ID, "block/radioactive_water_overlay");
    private static final ResourceLocation UNDERWATER_TEXTURE = new ResourceLocation("minecraft", "textures/misc/underwater.png");

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, FlyDroneMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, FlyDroneMod.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FlyDroneMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FlyDroneMod.MOD_ID);

    public static final RegistryObject<FluidType> TYPE = FLUID_TYPES.register("radioactive_water", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid.fly_drone.radioactive_water")
            .fallDistanceModifier(0.0F)
            .canExtinguish(true)
            .canConvertToSource(false)
            .supportsBoating(true)
            .canHydrate(false)
            .density(1000)
            .viscosity(1000)
            .temperature(300)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
            .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)) {
        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return STILL_TEXTURE;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return FLOWING_TEXTURE;
                }

                @Override
                public ResourceLocation getOverlayTexture() {
                    return OVERLAY_TEXTURE;
                }

                @Override
                public ResourceLocation getRenderOverlayTexture(Minecraft minecraft) {
                    return UNDERWATER_TEXTURE;
                }

                @Override
                public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
                    return new Vector3f(0.035F, 0.16F, 0.012F);
                }

                @Override
                public int getTintColor() {
                    return 0xFFFFFFFF;
                }
            });
        }
    });

    public static final RegistryObject<FlowingFluid> SOURCE =
            FLUIDS.register("radioactive_water", () -> new ForgeFlowingFluid.Source(makeProperties()));

    public static final RegistryObject<FlowingFluid> FLOWING =
            FLUIDS.register("flowing_radioactive_water", () -> new ForgeFlowingFluid.Flowing(makeProperties()));

    public static final RegistryObject<LiquidBlock> BLOCK =
            BLOCKS.register("radioactive_water", () -> new LiquidBlock(SOURCE, BlockBehaviour.Properties.copy(Blocks.WATER)
                    .mapColor(MapColor.COLOR_GREEN)));

    public static final RegistryObject<Item> BUCKET =
            ITEMS.register("radioactive_water_bucket", () -> new BucketItem(SOURCE, new Item.Properties()
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1)));

    private RadioactiveWater() {
    }

    private static ForgeFlowingFluid.Properties makeProperties() {
        return new ForgeFlowingFluid.Properties(TYPE, SOURCE, FLOWING)
                .block(BLOCK)
                .bucket(BUCKET)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .explosionResistance(100.0F)
                .tickRate(2);
    }

    public static void register(IEventBus eventBus) {
        FLUID_TYPES.register(eventBus);
        FLUIDS.register(eventBus);
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
