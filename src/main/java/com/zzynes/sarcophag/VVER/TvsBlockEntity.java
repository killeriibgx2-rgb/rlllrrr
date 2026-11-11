package com.zzynes.sarcophag.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

    private float temperatureC = VverThermal.AMBIENT_C;
    private float integrityPercent = 100.0f;

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
        sync();
    }

    public float getTemperatureC() {
        return this.temperatureC;
    }

    public void setTemperatureC(float temperatureC) {
        float clamped = VverThermal.clampTemperature(temperatureC);
        if (Math.abs(this.temperatureC - clamped) < 0.01f) {
            return;
        }
        this.temperatureC = clamped;
        this.setChanged();
        sync();
    }

    public float getIntegrityPercent() {
        return this.integrityPercent;
    }

    public void damageIntegrity(float amountPercent) {
        if (amountPercent <= 0.0f) {
            return;
        }
        float next = VverThermal.clampIntegrity(this.integrityPercent - amountPercent);
        if (Math.abs(next - this.integrityPercent) < 0.001f) {
            return;
        }
        this.integrityPercent = next;
        this.setChanged();
        sync();
    }

    public boolean isShellMelted() {
        return this.temperatureC >= VverThermal.TVS_SHELL_MELT_C;
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", this.itemHandler.serializeNBT());
        tag.putFloat("TemperatureC", this.temperatureC);
        tag.putFloat("IntegrityPercent", this.integrityPercent);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.itemHandler.deserializeNBT(tag.getCompound("inventory"));
        this.temperatureC = tag.contains("TemperatureC") ? VverThermal.clampTemperature(tag.getFloat("TemperatureC")) : VverThermal.AMBIENT_C;
        this.integrityPercent = tag.contains("IntegrityPercent") ? VverThermal.clampIntegrity(tag.getFloat("IntegrityPercent")) : 100.0f;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.sarcophag.vver_tvs");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TvsMenu(containerId, playerInventory, this);
    }
}
