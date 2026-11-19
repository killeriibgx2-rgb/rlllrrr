package com.zzynes.sarcophag.POWER;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

// Кабель как блок: твёрдый хитбокс, на него можно встать, ломается обычным ударом.
// Внутри клетки лежит набор жил, толщину и цвет каждая держит свои.
public class CableBlock extends Block implements EntityBlock {

    public CableBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(0.4f)
                .sound(SoundType.WOOL)
                .noOcclusion()
                .noLootTable()
                .dynamicShape());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
            return cable.shape();
        }
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    // Каждая жила выпадает своим предметом.
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
            for (CableStrand strand : cable.strands()) {
                if (player.isCreative()) {
                    continue;
                }
                ItemStack stack = new ItemStack(itemFor(strand.tier));
                stack.getOrCreateTag().putByte("Core", (byte) strand.core.ordinal());
                Block.popResource(level, pos, stack);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    private static Item itemFor(CableTier tier) {
        return switch (tier) {
            case CONTROL -> PowerRegistry.CABLE_CONTROL.get();
            case POWER -> PowerRegistry.CABLE_POWER.get();
            case MAIN -> PowerRegistry.CABLE_MAIN.get();
        };
    }
}
