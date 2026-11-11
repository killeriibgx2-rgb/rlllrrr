package com.zzynes.sarcophag.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

public class CornerPipeBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<Bend> BEND = EnumProperty.create("bend", Bend.class);

    private static final VoxelShape DOWN_ARM = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 10.0D, 14.0D);
    private static final VoxelShape UP_ARM = Block.box(2.0D, 6.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    private static final VoxelShape NORTH_ARM = Block.box(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 10.0D);
    private static final VoxelShape SOUTH_ARM = Block.box(2.0D, 2.0D, 6.0D, 14.0D, 14.0D, 16.0D);
    private static final VoxelShape WEST_ARM = Block.box(0.0D, 2.0D, 2.0D, 10.0D, 14.0D, 14.0D);
    private static final VoxelShape EAST_ARM = Block.box(6.0D, 2.0D, 2.0D, 16.0D, 14.0D, 14.0D);

    public CornerPipeBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 30.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.BLOCK)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.WEST)
                .setValue(BEND, Bend.FLAT));
    }

    public static Direction firstPort(BlockState state) {
        return state.getValue(FACING);
    }

    public static Direction secondPort(BlockState state) {
        return switch (state.getValue(BEND)) {
            case FLAT -> state.getValue(FACING).getClockWise();
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }

    public static boolean hasPort(BlockState state, Direction direction) {
        return state.getBlock() instanceof CornerPipeBlock
                && (firstPort(state) == direction || secondPort(state) == direction);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BEND);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction towardClickedBlock = context.getClickedFace().getOpposite();
        BlockState autoConnected = this.autoConnectedState(context, towardClickedBlock);
        if (autoConnected != null) {
            return autoConnected;
        }
        Direction towardPlayer = PipePorts.chooseTowardPlayer(context, towardClickedBlock);
        return this.stateForPorts(towardClickedBlock, towardPlayer);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        Direction source = state.getValue(BEND) == Bend.FLAT ? secondPort(state) : firstPort(state);
        Direction mirrored = mirror.getRotation(source).rotate(source);
        return state.setValue(FACING, mirrored);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.or(armShape(firstPort(state)), armShape(secondPort(state)));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Nullable
    private BlockState autoConnectedState(BlockPlaceContext context, Direction towardClickedBlock) {
        List<Direction> connections = PipePorts.connections(context.getLevel(), context.getClickedPos());
        if (connections.size() < 2) {
            return null;
        }
        if (connections.contains(towardClickedBlock)) {
            Direction second = this.chooseConnectedTowardPlayer(context, towardClickedBlock, connections);
            return second != null ? this.stateForPorts(towardClickedBlock, second) : null;
        }
        if (connections.size() == 2) {
            Direction first = connections.get(0);
            Direction second = connections.get(1);
            return first.getAxis() != second.getAxis() ? this.stateForPorts(first, second) : null;
        }
        return null;
    }

    @Nullable
    private Direction chooseConnectedTowardPlayer(BlockPlaceContext context, Direction first, List<Direction> connections) {
        Vec3 towardPlayer = PipePorts.towardPlayerVector(context);
        Direction best = null;
        double bestValue = -Double.MAX_VALUE;
        for (Direction direction : connections) {
            if (direction.getAxis() == first.getAxis()) {
                continue;
            }
            double value = PipePorts.score(towardPlayer, direction);
            if (value > bestValue) {
                bestValue = value;
                best = direction;
            }
        }
        return best;
    }

    private BlockState stateForPorts(Direction first, Direction second) {
        if (first.getAxis() != Direction.Axis.Y && second.getAxis() != Direction.Axis.Y) {
            Direction facing = second == first.getClockWise() ? first : second;
            return this.defaultBlockState()
                    .setValue(FACING, facing)
                    .setValue(BEND, Bend.FLAT);
        }
        Direction horizontal = first.getAxis() == Direction.Axis.Y ? second : first;
        Bend bend = first == Direction.UP || second == Direction.UP ? Bend.UP : Bend.DOWN;
        return this.defaultBlockState()
                .setValue(FACING, horizontal)
                .setValue(BEND, bend);
    }

    private static VoxelShape armShape(Direction direction) {
        return switch (direction) {
            case DOWN -> DOWN_ARM;
            case UP -> UP_ARM;
            case NORTH -> NORTH_ARM;
            case SOUTH -> SOUTH_ARM;
            case WEST -> WEST_ARM;
            case EAST -> EAST_ARM;
        };
    }

    public enum Bend implements StringRepresentable {
        FLAT,
        UP,
        DOWN;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
