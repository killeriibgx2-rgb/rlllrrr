package com.zzynes.sarcophag.WATER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Optional;

public class DrainBlock extends Block implements LiquidBlockContainer, BucketPickup {
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 8);

    private static final VoxelShape BOTTOM_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);
    private static final VoxelShape TOP_SHAPE = Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public DrainBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(4.0F, 12.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, Half.BOTTOM)
                .setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, LEVEL);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        Half half = face == Direction.DOWN
                || face != Direction.UP && context.getClickLocation().y - (double) pos.getY() > 0.5D
                ? Half.TOP
                : Half.BOTTOM;
        int waterLevel = 0;
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.is(FluidTags.WATER)) {
            waterLevel = fluidState.isSource() ? 8 : Math.max(1, Math.min(8, fluidState.getAmount()));
        }
        return this.defaultBlockState().setValue(HALF, half).setValue(LEVEL, waterLevel);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        int waterLevel = state.getValue(LEVEL);
        if (waterLevel <= 0) {
            return Fluids.EMPTY.defaultFluidState();
        }
        if (waterLevel >= 8) {
            return Fluids.WATER.getSource(false);
        }
        return Fluids.FLOWING_WATER.getFlowing(waterLevel, false);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (state.getValue(LEVEL) > 0) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(LEVEL) > 0) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return fluid == Fluids.WATER && state.getValue(LEVEL) == 0;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (fluidState.getType() != Fluids.WATER || state.getValue(LEVEL) != 0) {
            return false;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(LEVEL, 8), 3);
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return true;
    }

    @Override
    public ItemStack pickupBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(LEVEL) < 8) {
            return ItemStack.EMPTY;
        }
        level.setBlock(pos, state.setValue(LEVEL, 0), 3);
        return new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == Half.TOP ? TOP_SHAPE : BOTTOM_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
