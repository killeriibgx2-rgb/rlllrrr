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
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

public class SuzBlockEntity extends BlockEntity {

    public static final float MAX_LIFT_BLOCKS = 2.92f;
    private static final float MAX_SPEED_PERCENT_PER_TICK = 0.668f;
    private static final float ACCEL_PERCENT_PER_TICK = 0.08f;
    private static final float BRAKE_FACTOR = 0.08f;
    private static final float MIN_CRAWL_SPEED = 0.05f;

    private float targetPercent = 0.0f;
    private float currentPercent = 0.0f;
    private float prevPercent = 0.0f;
    private float velocityPercent = 0.0f;

    public SuzBlockEntity(BlockPos pos, BlockState state) {
        super(VverRegistry.SUZ_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SuzBlockEntity entity) {
        entity.prevPercent = entity.currentPercent;
        float diff = entity.targetPercent - entity.currentPercent;
        float dist = Math.abs(diff);
        if (dist <= 0.001f) {
            entity.currentPercent = entity.targetPercent;
            entity.velocityPercent = 0.0f;
            return;
        }
        float sign = diff > 0.0f ? 1.0f : -1.0f;
        float stopSpeed = Math.min(MAX_SPEED_PERCENT_PER_TICK, Math.max(MIN_CRAWL_SPEED, Mth.sqrt(BRAKE_FACTOR * dist)));
        float desiredVel = sign * stopSpeed;
        float deltaVel = desiredVel - entity.velocityPercent;
        if (Math.abs(deltaVel) <= ACCEL_PERCENT_PER_TICK) {
            entity.velocityPercent = desiredVel;
        } else {
            entity.velocityPercent += Math.copySign(ACCEL_PERCENT_PER_TICK, deltaVel);
        }
        if (Math.abs(entity.velocityPercent) >= dist) {
            entity.currentPercent = entity.targetPercent;
            entity.velocityPercent = 0.0f;
        } else {
            entity.currentPercent = Mth.clamp(entity.currentPercent + entity.velocityPercent, 0.0f, 100.0f);
        }
    }

    public void setTargetPercent(float percent) {
        float clamped = Mth.clamp(percent, 0.0f, 100.0f);
        if (Math.abs(this.targetPercent - clamped) < 0.001f) {
            return;
        }
        this.targetPercent = clamped;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public float getTargetPercent() {
        return this.targetPercent;
    }

    public float getCurrentPercent() {
        return this.currentPercent;
    }

    public float getInterpolatedPercent(float partialTick) {
        return Mth.clamp(Mth.lerp(partialTick, this.prevPercent, this.currentPercent), 0.0f, 100.0f);
    }

    public float getInterpolatedLiftBlocks(float partialTick) {
        return (getInterpolatedPercent(partialTick) / 100.0f) * MAX_LIFT_BLOCKS;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(
                this.worldPosition.getX() - 1.0,
                this.worldPosition.getY(),
                this.worldPosition.getZ() - 1.0,
                this.worldPosition.getX() + 2.0,
                this.worldPosition.getY() + 6.5,
                this.worldPosition.getZ() + 2.0
        );
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("TargetPercent", this.targetPercent);
        tag.putFloat("CurrentPercent", this.currentPercent);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boolean wasEmpty = this.level == null;
        this.targetPercent = Mth.clamp(tag.getFloat("TargetPercent"), 0.0f, 100.0f);
        if (wasEmpty) {
            float savedCurrent = tag.contains("CurrentPercent") ? tag.getFloat("CurrentPercent") : this.targetPercent;
            this.currentPercent = Mth.clamp(savedCurrent, 0.0f, 100.0f);
            this.prevPercent = this.currentPercent;
            this.velocityPercent = 0.0f;
        } else if (!this.level.isClientSide && tag.contains("CurrentPercent")) {
            this.currentPercent = Mth.clamp(tag.getFloat("CurrentPercent"), 0.0f, 100.0f);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putFloat("TargetPercent", this.targetPercent);
        tag.putFloat("CurrentPercent", this.currentPercent);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        this.targetPercent = Mth.clamp(tag.getFloat("TargetPercent"), 0.0f, 100.0f);
        this.currentPercent = Mth.clamp(tag.getFloat("CurrentPercent"), 0.0f, 100.0f);
        this.prevPercent = this.currentPercent;
        this.velocityPercent = 0.0f;
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
            this.targetPercent = Mth.clamp(tag.getFloat("TargetPercent"), 0.0f, 100.0f);
        }
    }
}
