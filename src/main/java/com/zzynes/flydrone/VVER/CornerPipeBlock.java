package com.zzynes.flydrone.VVER;

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
        BlockState clickedState = context.getLevel().getBlockState(context.getClickedPos());
        Direction clickedFace = context.getClickedFace();
        if (isConnectorFace(clickedState, clickedFace)) {
            Direction back = clickedFace.getOpposite();
            return this.stateForPorts(back, this.chooseExit(context, back));
        }
        if (clickedFace.getAxis() == Direction.Axis.Y) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(BEND, Bend.FLAT);
        }
        Bend bend = context.getClickLocation().y >= context.getClickedPos().getY() + 0.5D ? Bend.UP : Bend.DOWN;
        return this.defaultBlockState()
                .setValue(FACING, clickedFace)
                .setValue(BEND, bend);
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

    private static boolean isConnectorFace(BlockState state, Direction clickedFace) {
        if (PipeBlock.hasPort(state, clickedFace) || CornerPipeBlock.hasPort(state, clickedFace)) {
            return true;
        }
        return state.getBlock() instanceof HullNozzleBlock
                && state.getValue(HullNozzleBlock.FACING) == clickedFace;
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

    private Direction chooseExit(BlockPlaceContext context, Direction back) {
        Direction look = context.getNearestLookingDirection();
        if (look.getAxis() != back.getAxis()) {
            return look;
        }
        Vec3 relative = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
        Direction best = null;
        double bestValue = 0.1D;
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == back.getAxis()) {
                continue;
            }
            double value = relative.x * direction.getStepX()
                    + relative.y * direction.getStepY()
                    + relative.z * direction.getStepZ();
            if (value > bestValue) {
                bestValue = value;
                best = direction;
            }
        }
        if (best != null) {
            return best;
        }
        Direction horizontal = context.getHorizontalDirection();
        return horizontal.getAxis() != back.getAxis() ? horizontal : Direction.UP;
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
