package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class ValveBlockEntity extends BlockEntity {
    private static final float ROTATION_SPEED_DEGREES_PER_TICK = 18.0F;

    private float currentAngle;
    private float previousAngle;
    private float targetAngle;

    public ValveBlockEntity(BlockPos pos, BlockState state) {
        super(VverRegistry.VALVE_BLOCK_ENTITY.get(), pos, state);
        float angle = state.hasProperty(ValveBlock.OPEN) && !state.getValue(ValveBlock.OPEN) ? -180.0F : 0.0F;
        this.currentAngle = angle;
        this.previousAngle = angle;
        this.targetAngle = angle;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ValveBlockEntity entity) {
        entity.previousAngle = entity.currentAngle;
        if (entity.currentAngle < entity.targetAngle) {
            entity.currentAngle = Math.min(entity.targetAngle, entity.currentAngle + ROTATION_SPEED_DEGREES_PER_TICK);
        } else if (entity.currentAngle > entity.targetAngle) {
            entity.currentAngle = Math.max(entity.targetAngle, entity.currentAngle - ROTATION_SPEED_DEGREES_PER_TICK);
        }
        if (!level.isClientSide && entity.previousAngle != entity.currentAngle) {
            entity.setChanged();
        }
    }

    public void setOpen(boolean open) {
        this.targetAngle += open ? 180.0F : -180.0F;
        this.setChanged();
        sync();
    }

    public float getInterpolatedAngle(float partialTick) {
        return Mth.lerp(partialTick, this.previousAngle, this.currentAngle);
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("WheelCurrentAngle", this.currentAngle);
        tag.putFloat("WheelTargetAngle", this.targetAngle);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("WheelTargetAngle")) {
            this.targetAngle = tag.getFloat("WheelTargetAngle");
        }
        if (tag.contains("WheelCurrentAngle")) {
            this.currentAngle = tag.getFloat("WheelCurrentAngle");
        } else {
            this.currentAngle = this.targetAngle;
        }
        this.previousAngle = this.currentAngle;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putFloat("WheelCurrentAngle", this.currentAngle);
        tag.putFloat("WheelTargetAngle", this.targetAngle);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        if (tag.contains("WheelTargetAngle")) {
            this.targetAngle = tag.getFloat("WheelTargetAngle");
        }
        if (tag.contains("WheelCurrentAngle")) {
            this.currentAngle = tag.getFloat("WheelCurrentAngle");
        } else {
            this.currentAngle = this.targetAngle;
        }
        this.previousAngle = this.currentAngle;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null && tag.contains("WheelTargetAngle")) {
            this.targetAngle = tag.getFloat("WheelTargetAngle");
        }
    }
}
