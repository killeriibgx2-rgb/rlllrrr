package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class GcnBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    public static final int PART_MAIN = 0;
    public static final int PART_MIDDLE = 1;
    public static final int PART_BACK = 2;

    public GcnBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(6.0f, 40.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, PART_MAIN));
    }

    public static Direction inletDirection(BlockState state) {
        return state.getValue(FACING);
    }

    public static Direction outletDirection(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    public static Direction electricalDirection(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    public static boolean hasFluidPort(BlockState state, Direction towardNeighbor) {
        if (!(state.getBlock() instanceof GcnBlock)) {
            return false;
        }
        int part = state.getValue(PART);
        return part == PART_MAIN
                && (towardNeighbor == inletDirection(state) || towardNeighbor == outletDirection(state));
    }

    public static BlockPos fluidMainPos(BlockPos pos, BlockState state) {
        return mainPos(pos, state);
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
        for (int part = PART_MIDDLE; part <= PART_BACK; part++) {
            BlockPos partPos = pos.relative(facing, offsetForPart(part));
            if (!level.isInWorldBounds(partPos) || !level.getBlockState(partPos).canBeReplaced(context)) {
                return null;
            }
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, PART_MAIN);
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
            Direction facing = state.getValue(FACING);
            BlockPos mainPos = mainPos(pos, state);
            for (int part = PART_MAIN; part <= PART_BACK; part++) {
                BlockPos partPos = mainPos.relative(facing, offsetForPart(part));
                if (partPos.equals(pos)) {
                    continue;
                }
                BlockState partState = level.getBlockState(partPos);
                if (partState.is(this)
                        && partState.getValue(PART) == part
                        && partState.getValue(FACING) == facing) {
                    level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 35);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == PART_MAIN ? new GcnBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != PART_MAIN) {
            return null;
        }
        return createTickerHelper(type, VverRegistry.GCN_BLOCK_ENTITY.get(), GcnBlockEntity::tick);
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
        if (!mainState.is(VverRegistry.GCN_BLOCK.get()) || mainState.getValue(PART) != PART_MAIN) {
            return true;
        }
        Direction facing = mainState.getValue(FACING);
        for (int part = PART_MIDDLE; part <= PART_BACK; part++) {
            BlockPos partPos = mainPos.relative(facing, offsetForPart(part));
            if (!level.isLoaded(partPos)) {
                return false;
            }
            BlockState partState = level.getBlockState(partPos);
            if (partState.is(VverRegistry.GCN_BLOCK.get())
                    && partState.getValue(PART) == part
                    && partState.getValue(FACING) == facing) {
                continue;
            }
            if (force || partState.canBeReplaced()) {
                level.setBlock(partPos, mainState.setValue(PART, part), 3);
            }
        }
        return true;
    }

    private static BlockPos mainPos(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING), -offsetForPart(state.getValue(PART)));
    }

    private static int offsetForPart(int part) {
        return switch (part) {
            case PART_MIDDLE -> -1;
            case PART_BACK -> -2;
            default -> 0;
        };
    }
}
