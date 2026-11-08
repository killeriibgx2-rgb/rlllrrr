package com.zzynes.flydrone.BLOCK;

import com.zzynes.flydrone.DRONE.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class BatteryCabinetBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);
    public static final int PART_MAIN = 0;
    private static final double REACH = 5.0;

    public BatteryCabinetBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0f, 30.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, PART_MAIN));
    }

    public static BlockPos mainPos(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        Direction side = BatteryCabinetSlots.sideDirection(state.getValue(FACING));
        return pos.relative(side, -(part % 2)).below(part / 2);
    }

    public static BlockPos partPos(BlockPos mainPos, BlockState state, int part) {
        Direction side = BatteryCabinetSlots.sideDirection(state.getValue(FACING));
        return mainPos.relative(side, part % 2).above(part / 2);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        BlockState state = this.defaultBlockState().setValue(FACING, facing).setValue(PART, PART_MAIN);
        for (int part = 1; part <= 5; part++) {
            BlockPos partPos = partPos(pos, state, part);
            if (!level.isInWorldBounds(partPos) || !level.getBlockState(partPos).canBeReplaced(context)) {
                return null;
            }
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(PART) == PART_MAIN) {
            placeParts(level, pos, state, true);
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos mainPos = mainPos(pos, state);
        BlockState mainState = level.getBlockState(mainPos);
        if (!mainState.is(this) || mainState.getValue(PART) != PART_MAIN) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(mainPos) instanceof BatteryCabinetBlockEntity cabinet)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                boolean newOpen = !cabinet.isOpen();
                cabinet.setOpen(newOpen);
                level.playSound(null, mainPos, newOpen ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.0f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!cabinet.isOpen()) {
            return InteractionResult.PASS;
        }
        Direction facing = mainState.getValue(FACING);
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(REACH));
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.CABINET_BATTERY.get())) {
            int slot = BatteryCabinetSlots.pickSlot(mainPos, facing, cabinet.getBatteriesMask(), false, eye, end);
            if (slot < 0) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                cabinet.setInstalled(slot, true);
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                level.playSound(null, mainPos, SoundEvents.METAL_HIT, SoundSource.BLOCKS, 0.6f, 1.1f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty()) {
            int slot = BatteryCabinetSlots.pickSlot(mainPos, facing, cabinet.getBatteriesMask(), true, eye, end);
            if (slot >= 0) {
                if (!level.isClientSide) {
                    cabinet.setInstalled(slot, false);
                    ItemStack battery = new ItemStack(ModItems.CABINET_BATTERY.get());
                    if (!player.addItem(battery)) {
                        player.drop(battery, false);
                    }
                    level.playSound(null, mainPos, SoundEvents.METAL_HIT, SoundSource.BLOCKS, 0.6f, 0.9f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(PART) != PART_MAIN) {
            BlockPos mainPos = mainPos(pos, state);
            BlockState mainState = level.getBlockState(mainPos);
            if (mainState.is(this)
                    && mainState.getValue(PART) == PART_MAIN
                    && mainState.getValue(FACING) == state.getValue(FACING)) {
                if (!player.isCreative() && player.hasCorrectToolForDrops(mainState)) {
                    BlockEntity blockEntity = level.getBlockEntity(mainPos);
                    Block.dropResources(mainState, level, mainPos, blockEntity, player, player.getMainHandItem());
                }
                level.setBlock(mainPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, mainPos, Block.getId(mainState));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (state.getValue(PART) == PART_MAIN) {
                if (level.getBlockEntity(pos) instanceof BatteryCabinetBlockEntity cabinet) {
                    int count = cabinet.getInstalledCount();
                    for (int i = 0; i < count; i++) {
                        Block.popResource(level, pos, new ItemStack(ModItems.CABINET_BATTERY.get()));
                    }
                }
                for (int part = 1; part <= 5; part++) {
                    BlockPos partPos = partPos(pos, state, part);
                    BlockState partState = level.getBlockState(partPos);
                    if (partState.is(this)
                            && partState.getValue(PART) == part
                            && partState.getValue(FACING) == state.getValue(FACING)) {
                        level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 35);
                    }
                }
            } else {
                BlockPos mainPos = mainPos(pos, state);
                BlockState mainState = level.getBlockState(mainPos);
                if (mainState.is(this)
                        && mainState.getValue(PART) == PART_MAIN
                        && mainState.getValue(FACING) == state.getValue(FACING)) {
                    level.setBlock(mainPos, Blocks.AIR.defaultBlockState(), 35);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == PART_MAIN ? new BatteryCabinetBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != PART_MAIN) {
            return null;
        }
        return createTickerHelper(type, BlockRegistry.BATTERY_CABINET_BLOCK_ENTITY.get(), BatteryCabinetBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == PART_MAIN ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    static boolean placeParts(Level level, BlockPos mainPos, BlockState mainState, boolean force) {
        if (!mainState.is(BlockRegistry.BATTERY_CABINET.get()) || mainState.getValue(PART) != PART_MAIN) {
            return true;
        }
        for (int part = 1; part <= 5; part++) {
            BlockPos partPos = partPos(mainPos, mainState, part);
            if (!level.isLoaded(partPos)) {
                return false;
            }
            BlockState partState = level.getBlockState(partPos);
            if (partState.is(BlockRegistry.BATTERY_CABINET.get())
                    && partState.getValue(PART) == part
                    && partState.getValue(FACING) == mainState.getValue(FACING)) {
                continue;
            }
            if (force || partState.canBeReplaced()) {
                level.setBlock(partPos, mainState.setValue(PART, part), 3);
            }
        }
        return true;
    }
}
