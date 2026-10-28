package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

public class TvsBlockEntity extends BlockEntity implements MenuProvider {

    public final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            updateRodsState();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public TvsBlockEntity(BlockPos pos, BlockState state) {
        super(VverRegistry.TVS_BLOCK_ENTITY.get(), pos, state);
    }

    private void updateRodsState() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        boolean filled = !this.itemHandler.getStackInSlot(0).isEmpty();
        for (int i = 0; i < TvsBlock.STRUCTURE_HEIGHT; i++) {
            BlockPos targetPos = this.worldPosition.above(i);
            BlockState state = this.level.getBlockState(targetPos);
            if (state.is(VverRegistry.TVS_BLOCK.get())
                    && state.getValue(TvsBlock.PART) == i
                    && state.getValue(TvsBlock.HAS_RODS) != filled) {
                this.level.setBlock(targetPos, state.setValue(TvsBlock.HAS_RODS, filled), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", this.itemHandler.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.itemHandler.deserializeNBT(tag.getCompound("inventory"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.fly_drone.vver_tvs");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TvsMenu(containerId, playerInventory, this);
    }
}
