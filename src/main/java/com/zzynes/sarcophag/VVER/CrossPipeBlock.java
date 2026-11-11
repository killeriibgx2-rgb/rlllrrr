package com.zzynes.sarcophag.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
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

public class CrossPipeBlock extends Block {
    public static final EnumProperty<Direction.Axis> MISSING_AXIS = EnumProperty.create("missing_axis", Direction.Axis.class);

    private static final VoxelShape DOWN_ARM = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 10.0D, 14.0D);
    private static final VoxelShape UP_ARM = Block.box(2.0D, 6.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    private static final VoxelShape NORTH_ARM = Block.box(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 10.0D);
    private static final VoxelShape SOUTH_ARM = Block.box(2.0D, 2.0D, 6.0D, 14.0D, 14.0D, 16.0D);
    private static final VoxelShape WEST_ARM = Block.box(0.0D, 2.0D, 2.0D, 10.0D, 14.0D, 14.0D);
    private static final VoxelShape EAST_ARM = Block.box(6.0D, 2.0D, 2.0D, 16.0D, 14.0D, 14.0D);

    public CrossPipeBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 30.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.BLOCK)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(MISSING_AXIS, Direction.Axis.Y));
    }

    public static boolean hasPort(BlockState state, Direction direction) {
        return state.getBlock() instanceof CrossPipeBlock
                && direction.getAxis() != state.getValue(MISSING_AXIS);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MISSING_AXIS);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction towardClickedBlock = context.getClickedFace().getOpposite();
        Direction towardPlayer = PipePorts.chooseTowardPlayer(context, towardClickedBlock);
        Direction.Axis fallback = thirdAxis(towardClickedBlock.getAxis(), towardPlayer.getAxis());
        Direction.Axis chosen = this.chooseMissingAxis(context, towardClickedBlock, fallback);
        return this.defaultBlockState().setValue(MISSING_AXIS, chosen);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(MISSING_AXIS);
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            axis = switch (axis) {
                case X -> Direction.Axis.Z;
                case Y -> Direction.Axis.Y;
                case Z -> Direction.Axis.X;
            };
        }
        return state.setValue(MISSING_AXIS, axis);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = Shapes.empty();
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != state.getValue(MISSING_AXIS)) {
                shape = Shapes.or(shape, armShape(direction));
            }
        }
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    private Direction.Axis chooseMissingAxis(BlockPlaceContext context, Direction towardClickedBlock, Direction.Axis fallback) {
        List<Direction> connections = PipePorts.connections(context.getLevel(), context.getClickedPos());
        Vec3 towardPlayerVector = PipePorts.towardPlayerVector(context);
        Direction.Axis best = fallback;
        int bestMatches = -1;
        double bestScore = -Double.MAX_VALUE;
        for (Direction.Axis axis : Direction.Axis.values()) {
            int matches = matches(connections, axis);
            double score = planeScore(axis, fallback, towardClickedBlock, towardPlayerVector);
            if (matches > bestMatches || matches == bestMatches && score > bestScore) {
                best = axis;
                bestMatches = matches;
                bestScore = score;
            }
        }
        return bestMatches >= 2 ? best : fallback;
    }

    private static int matches(List<Direction> connections, Direction.Axis missingAxis) {
        int matches = 0;
        for (Direction direction : connections) {
            if (direction.getAxis() != missingAxis) {
                matches++;
            }
        }
        return matches;
    }

    private static double planeScore(Direction.Axis missingAxis, Direction.Axis fallback, Direction towardClickedBlock, Vec3 towardPlayerVector) {
        double score = Math.abs(towardPlayerVector.x)
                + Math.abs(towardPlayerVector.y)
                + Math.abs(towardPlayerVector.z)
                - Math.abs(component(towardPlayerVector, missingAxis));
        if (missingAxis == fallback) {
            score += 1000.0D;
        }
        if (towardClickedBlock.getAxis() != missingAxis) {
            score += 100.0D;
        }
        return score;
    }

    private static double component(Vec3 vector, Direction.Axis axis) {
        return switch (axis) {
            case X -> vector.x;
            case Y -> vector.y;
            case Z -> vector.z;
        };
    }

    private static Direction.Axis thirdAxis(Direction.Axis first, Direction.Axis second) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != first && axis != second) {
                return axis;
            }
        }
        throw new IllegalArgumentException("Invalid cross pipe plane");
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
}
