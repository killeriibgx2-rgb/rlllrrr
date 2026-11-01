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
import java.util.ArrayList;
import java.util.List;

public class SteamGeneratorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 44);
    public static final int PART_COUNT = 45;
    public static final int MAIN_PART = 1;
    public static final int HEIGHT = 5;

    public SteamGeneratorBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(7.0f, 50.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, MAIN_PART));
    }

    public static BlockPos mainPos(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        Direction sideDirection = facing.getClockWise();
        return pos.relative(facing, -frontOffset(part)).relative(sideDirection, -sideOffset(part)).below(height(part));
    }

    public static BlockPos partPos(BlockPos mainPos, BlockState state, int part) {
        return partPos(mainPos, state.getValue(FACING), part);
    }

    public static BlockPos partPos(BlockPos mainPos, Direction facing, int part) {
        return mainPos.relative(facing, frontOffset(part)).relative(facing.getClockWise(), sideOffset(part)).above(height(part));
    }

    public static int partIndex(int frontOffset, int height, int sideOffset) {
        return height * 9 - frontOffset * 3 + sideOffset + 1;
    }

    public static int frontOffset(int part) {
        return -((part % 9) / 3);
    }

    public static int sideOffset(int part) {
        return part % 3 - 1;
    }

    public static int height(int part) {
        return part / 9;
    }

    public static boolean hasPort(BlockState state, Direction towardNeighbor) {
        PortSpec spec = portSpec(state);
        return spec != null && spec.outward() == towardNeighbor;
    }

    @Nullable
    public static Port portAt(BlockPos pos, BlockState state) {
        PortSpec spec = portSpec(state);
        return spec == null ? null : new Port(pos.immutable(), spec.outward(), spec.circuit());
    }

    public static List<Port> ports(BlockPos mainPos, BlockState state) {
        List<Port> ports = new ArrayList<>();
        for (int part = 0; part < PART_COUNT; part++) {
            BlockState partState = state.setValue(PART, part);
            PortSpec spec = portSpec(partState);
            if (spec != null) {
                ports.add(new Port(partPos(mainPos, state, part), spec.outward(), spec.circuit()));
            }
        }
        return ports;
    }

    public static List<Port> ports(BlockPos mainPos, BlockState state, Circuit circuit) {
        List<Port> ports = new ArrayList<>();
        for (Port port : ports(mainPos, state)) {
            if (port.circuit() == circuit) {
                ports.add(port);
            }
        }
        return ports;
    }

    @Nullable
    private static PortSpec portSpec(BlockState state) {
        if (!(state.getBlock() instanceof SteamGeneratorBlock)) {
            return null;
        }
        int part = state.getValue(PART);
        int front = frontOffset(part);
        int side = sideOffset(part);
        int height = height(part);
        Direction facing = state.getValue(FACING);
        if (height == 0 && front == 0 && side != 0) {
            return new PortSpec(facing, Circuit.PRIMARY);
        }
        if (height == 0 && front == -2 && side == 0) {
            return new PortSpec(facing.getOpposite(), Circuit.SECONDARY);
        }
        if (height == HEIGHT - 1 && front == -1 && side == 0) {
            return new PortSpec(Direction.UP, Circuit.SECONDARY);
        }
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos mainPos = context.getClickedPos();
        Level level = context.getLevel();
        for (int part = 0; part < PART_COUNT; part++) {
            BlockPos partPos = partPos(mainPos, facing, part);
            if (!level.isInWorldBounds(partPos) || !level.getBlockState(partPos).canBeReplaced(context)) {
                return null;
            }
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, MAIN_PART);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(PART) == MAIN_PART) {
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
        if (!level.isClientSide && state.getValue(PART) != MAIN_PART) {
            BlockPos mainPos = mainPos(pos, state);
            BlockState mainState = level.getBlockState(mainPos);
            if (mainState.is(this)
                    && mainState.getValue(PART) == MAIN_PART
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
            if (state.getValue(PART) == MAIN_PART) {
                for (int part = 0; part < PART_COUNT; part++) {
                    if (part == MAIN_PART) {
                        continue;
                    }
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
                        && mainState.getValue(PART) == MAIN_PART
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
        return state.getValue(PART) == MAIN_PART ? new SteamGeneratorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != MAIN_PART) {
            return null;
        }
        return createTickerHelper(type, VverRegistry.STEAM_GENERATOR_BLOCK_ENTITY.get(), SteamGeneratorBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == MAIN_PART ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
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
        if (!mainState.is(VverRegistry.STEAM_GENERATOR_BLOCK.get()) || mainState.getValue(PART) != MAIN_PART) {
            return true;
        }
        Direction facing = mainState.getValue(FACING);
        for (int part = 0; part < PART_COUNT; part++) {
            if (part == MAIN_PART) {
                continue;
            }
            BlockPos partPos = partPos(mainPos, facing, part);
            if (!level.isLoaded(partPos)) {
                return false;
            }
            BlockState partState = level.getBlockState(partPos);
            if (partState.is(VverRegistry.STEAM_GENERATOR_BLOCK.get())
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

    public enum Circuit {
        PRIMARY,
        SECONDARY
    }

    public record Port(BlockPos occupiedPos, Direction outward, Circuit circuit) {
    }

    private record PortSpec(Direction outward, Circuit circuit) {
    }
}
