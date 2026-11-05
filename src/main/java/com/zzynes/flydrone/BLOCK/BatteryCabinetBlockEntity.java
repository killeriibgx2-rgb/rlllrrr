package com.zzynes.flydrone.BLOCK;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class BatteryCabinetBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final float DOOR_STEP = 0.1f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean open;
    private long batteriesMask;
    private float doorProgress;
    private boolean doorProgressInitialized;
    private boolean partsChecked;

    public BatteryCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.BATTERY_CABINET_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BatteryCabinetBlockEntity blockEntity) {
        if (level.isClientSide) {
            blockEntity.updateDoorProgress();
        } else if (!blockEntity.partsChecked) {
            blockEntity.partsChecked = BatteryCabinetBlock.placeParts(level, pos, state, false);
        }
    }

    public boolean isOpen() {
        return this.open;
    }

    public void setOpen(boolean open) {
        if (this.open == open) {
            return;
        }
        this.open = open;
        setChanged();
        sync();
    }

    public long getBatteriesMask() {
        return this.batteriesMask;
    }

    public boolean isInstalled(int slot) {
        return slot >= 0 && slot < BatteryCabinetSlots.SLOT_COUNT && (this.batteriesMask & (1L << slot)) != 0L;
    }

    public int getInstalledCount() {
        return Long.bitCount(this.batteriesMask);
    }

    public void setInstalled(int slot, boolean installed) {
        if (slot < 0 || slot >= BatteryCabinetSlots.SLOT_COUNT || isInstalled(slot) == installed) {
            return;
        }
        if (installed) {
            this.batteriesMask |= (1L << slot);
        } else {
            this.batteriesMask &= ~(1L << slot);
        }
        setChanged();
        sync();
    }

    public float getDoorProgress() {
        if (!this.doorProgressInitialized) {
            this.doorProgress = this.open ? 1.0f : 0.0f;
            this.doorProgressInitialized = true;
        }
        return this.doorProgress;
    }

    private void updateDoorProgress() {
        float target = this.open ? 1.0f : 0.0f;
        if (!this.doorProgressInitialized) {
            this.doorProgress = target;
            this.doorProgressInitialized = true;
            return;
        }
        this.doorProgress = Mth.clamp(this.doorProgress + Mth.clamp(target - this.doorProgress, -DOOR_STEP, DOOR_STEP), 0.0f, 1.0f);
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "battery_cabinet", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(
                this.worldPosition.getX() - 3.0,
                this.worldPosition.getY(),
                this.worldPosition.getZ() - 3.0,
                this.worldPosition.getX() + 4.0,
                this.worldPosition.getY() + 4.0,
                this.worldPosition.getZ() + 4.0
        );
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Open", this.open);
        tag.putLong("Batteries", this.batteriesMask);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.open = tag.getBoolean("Open");
        this.batteriesMask = tag.getLong("Batteries");
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

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
