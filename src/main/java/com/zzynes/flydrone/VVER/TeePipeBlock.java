package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
import java.util.Locale;

public class TeePipeBlock extends Block {
    public static final EnumProperty<Orientation> ORIENTATION = EnumProperty.create("orientation", Orientation.class);

    private static final VoxelShape DOWN_ARM = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 10.0D, 14.0D);
    private static final VoxelShape UP_ARM = Block.box(2.0D, 6.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    private static final VoxelShape NORTH_ARM = Block.box(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 10.0D);
    private static final VoxelShape SOUTH_ARM = Block.box(2.0D, 2.0D, 6.0D, 14.0D, 14.0D, 16.0D);
    private static final VoxelShape WEST_ARM = Block.box(0.0D, 2.0D, 2.0D, 10.0D, 14.0D, 14.0D);
    private static final VoxelShape EAST_ARM = Block.box(6.0D, 2.0D, 2.0D, 16.0D, 14.0D, 14.0D);

    public TeePipeBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 30.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.BLOCK)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(ORIENTATION, Orientation.SOUTH_X));
    }

    public static boolean hasPort(BlockState state, Direction direction) {
        return state.getBlock() instanceof TeePipeBlock
                && state.getValue(ORIENTATION).hasPort(direction);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction towardClickedBlock = context.getClickedFace().getOpposite();
        Direction towardPlayer = PipePorts.chooseTowardPlayer(context, towardClickedBlock);
        Orientation fallback = Orientation.of(towardPlayer, towardClickedBlock.getAxis());
        Orientation chosen = this.chooseOrientation(context, towardClickedBlock, towardPlayer, fallback);
        return this.defaultBlockState().setValue(ORIENTATION, chosen);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Orientation current = state.getValue(ORIENTATION);
        Direction branch = rotation.rotate(current.branch());
        Direction.Axis mainAxis = rotateAxis(current.mainAxis(), rotation);
        return state.setValue(ORIENTATION, Orientation.of(branch, mainAxis));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        Orientation current = state.getValue(ORIENTATION);
        Direction branch = current.branch();
        Direction mirrored = mirror.getRotation(branch).rotate(branch);
        return state.setValue(ORIENTATION, Orientation.of(mirrored, current.mainAxis()));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Orientation orientation = state.getValue(ORIENTATION);
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, orientation.mainAxis());
        Direction negative = Direction.get(Direction.AxisDirection.NEGATIVE, orientation.mainAxis());
        return Shapes.or(armShape(orientation.branch()), Shapes.or(armShape(positive), armShape(negative)));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    private Orientation chooseOrientation(BlockPlaceContext context, Direction towardClickedBlock, Direction towardPlayer, Orientation fallback) {
        List<Direction> connections = PipePorts.connections(context.getLevel(), context.getClickedPos());
        Vec3 towardPlayerVector = PipePorts.towardPlayerVector(context);
        Orientation best = fallback;
        int bestMatches = -1;
        double bestScore = -Double.MAX_VALUE;
        for (Orientation orientation : Orientation.values()) {
            int matches = orientation.matches(connections);
            double score = this.orientationScore(orientation, fallback, towardClickedBlock, towardPlayer, towardPlayerVector);
            if (matches > bestMatches || matches == bestMatches && score > bestScore) {
                best = orientation;
                bestMatches = matches;
                bestScore = score;
            }
        }
        return bestMatches >= 2 ? best : fallback;
    }

    private double orientationScore(Orientation orientation, Orientation fallback, Direction towardClickedBlock, Direction towardPlayer, Vec3 towardPlayerVector) {
        double score = PipePorts.score(towardPlayerVector, orientation.branch());
        if (orientation == fallback) {
            score += 1000.0D;
        }
        if (orientation.hasPort(towardClickedBlock)) {
            score += 100.0D;
        }
        if (orientation.branch() == towardPlayer) {
            score += 10.0D;
        }
        return score;
    }

    private static Direction.Axis rotateAxis(Direction.Axis axis, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return switch (axis) {
                case X -> Direction.Axis.Z;
                case Y -> Direction.Axis.Y;
                case Z -> Direction.Axis.X;
            };
        }
        return axis;
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

    public enum Orientation implements StringRepresentable {
        SOUTH_X(Direction.SOUTH, Direction.Axis.X),
        WEST_Z(Direction.WEST, Direction.Axis.Z),
        NORTH_X(Direction.NORTH, Direction.Axis.X),
        EAST_Z(Direction.EAST, Direction.Axis.Z),
        SOUTH_Y(Direction.SOUTH, Direction.Axis.Y),
        WEST_Y(Direction.WEST, Direction.Axis.Y),
        NORTH_Y(Direction.NORTH, Direction.Axis.Y),
        EAST_Y(Direction.EAST, Direction.Axis.Y),
        UP_X(Direction.UP, Direction.Axis.X),
        UP_Z(Direction.UP, Direction.Axis.Z),
        DOWN_X(Direction.DOWN, Direction.Axis.X),
        DOWN_Z(Direction.DOWN, Direction.Axis.Z);

        private final Direction branch;
        private final Direction.Axis mainAxis;

        Orientation(Direction branch, Direction.Axis mainAxis) {
            this.branch = branch;
            this.mainAxis = mainAxis;
        }

        static Orientation of(Direction branch, Direction.Axis mainAxis) {
            for (Orientation orientation : values()) {
                if (orientation.branch == branch && orientation.mainAxis == mainAxis) {
                    return orientation;
                }
            }
            throw new IllegalArgumentException("Invalid tee pipe orientation");
        }

        Direction branch() {
            return this.branch;
        }

        Direction.Axis mainAxis() {
            return this.mainAxis;
        }

        boolean hasPort(Direction direction) {
            return direction == this.branch || direction.getAxis() == this.mainAxis;
        }

        int matches(List<Direction> connections) {
            int matches = 0;
            for (Direction direction : connections) {
                if (this.hasPort(direction)) {
                    matches++;
                }
            }
            return matches;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
