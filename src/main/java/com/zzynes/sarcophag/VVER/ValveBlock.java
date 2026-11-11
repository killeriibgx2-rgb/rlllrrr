package com.zzynes.sarcophag.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class ValveBlock extends PipeBlock implements EntityBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final VoxelShape X_SHAPE = Shapes.or(
            Block.box(0.0D, 2.0D, 2.0D, 16.0D, 14.0D, 14.0D),
            Block.box(2.5D, 14.0D, 4.5D, 11.5D, 16.0D, 11.5D)
    );
    private static final VoxelShape Y_SHAPE = Shapes.or(
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D),
            Block.box(0.0D, 2.5D, 4.5D, 2.0D, 11.5D, 11.5D)
    );
    private static final VoxelShape Z_SHAPE = Shapes.or(
            Block.box(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 16.0D),
            Block.box(4.5D, 14.0D, 2.5D, 11.5D, 16.0D, 11.5D)
    );

    public ValveBlock() {
        super();
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(OPEN, true));
    }

    public static boolean hasPort(BlockState state, Direction direction) {
        return state.getBlock() instanceof ValveBlock
                && state.getValue(OPEN)
                && state.getValue(AXIS) == direction.getAxis();
    }

    public static boolean hasPhysicalPort(BlockState state, Direction direction) {
        return state.getBlock() instanceof ValveBlock
                && state.getValue(AXIS) == direction.getAxis();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, OPEN);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof PressureStickItem) {
            return InteractionResult.PASS;
        }
        boolean open = !state.getValue(OPEN);
        if (!level.isClientSide) {
            BlockState nextState = state.setValue(OPEN, open);
            level.setBlock(pos, nextState, 3);
            if (level.getBlockEntity(pos) instanceof ValveBlockEntity valve) {
                valve.setOpen(open);
            }
            level.playSound(
                    null,
                    pos,
                    VverRegistry.VALVE_TURN_SOUND.get(),
                    SoundSource.BLOCKS,
                    0.8F,
                    open ? 1.0F : 0.92F
            );
            level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            level.updateNeighborsAt(pos, this);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ValveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != VverRegistry.VALVE_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickerLevel, tickerPos, tickerState, blockEntity) ->
                ValveBlockEntity.tick(tickerLevel, tickerPos, tickerState, (ValveBlockEntity) blockEntity);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }
}
