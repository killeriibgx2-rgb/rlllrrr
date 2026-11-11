package com.zzynes.sarcophag.VVER;

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
    private int group = 0;
    private float temperatureC = VverThermal.AMBIENT_C;
    private float integrityPercent = 100.0f;
    private int stuckTicks;
    private int thermalSyncCooldown;

    public SuzBlockEntity(BlockPos pos, BlockState state) {
        super(VverRegistry.SUZ_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SuzBlockEntity entity) {
        entity.tickThermal();
        entity.prevPercent = entity.currentPercent;
        float beforeMove = entity.currentPercent;
        float diff = entity.targetPercent - entity.currentPercent;
        float dist = Math.abs(diff);
        if (dist <= 0.001f) {
            entity.currentPercent = entity.targetPercent;
            entity.velocityPercent = 0.0f;
            entity.updateStuck(beforeMove);
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
        entity.updateStuck(beforeMove);
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

    public int getGroup() {
        return this.group;
    }

    public void setGroup(int group) {
        int clamped = Mth.clamp(group, 0, 8);
        if (this.group == clamped) {
            return;
        }
        this.group = clamped;
        this.setChanged();
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

    public boolean isMelted() {
        return this.temperatureC >= VverThermal.SUZ_MELT_C;
    }

    public boolean isStuck() {
        return this.stuckTicks >= 100;
    }

    private void tickThermal() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        float next = VverThermal.applyDamagePerTick(this.integrityPercent, this.temperatureC, VverThermal.SUZ_DAMAGE_C, VverThermal.SUZ_MELT_C);
        if (Math.abs(next - this.integrityPercent) < 0.001f) {
            return;
        }
        this.integrityPercent = next;
        this.setChanged();
        if (this.thermalSyncCooldown-- <= 0) {
            this.thermalSyncCooldown = 20;
            sync();
        }
    }

    private void updateStuck(float beforeMove) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (Math.abs(this.targetPercent - this.currentPercent) > 0.5f && Math.abs(this.currentPercent - beforeMove) <= 0.0001f) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
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
        tag.putInt("Group", this.group);
        tag.putFloat("TemperatureC", this.temperatureC);
        tag.putFloat("IntegrityPercent", this.integrityPercent);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boolean wasEmpty = this.level == null;
        this.targetPercent = Mth.clamp(tag.getFloat("TargetPercent"), 0.0f, 100.0f);
        this.group = Mth.clamp(tag.getInt("Group"), 0, 8);
        this.temperatureC = tag.contains("TemperatureC") ? VverThermal.clampTemperature(tag.getFloat("TemperatureC")) : VverThermal.AMBIENT_C;
        this.integrityPercent = tag.contains("IntegrityPercent") ? VverThermal.clampIntegrity(tag.getFloat("IntegrityPercent")) : 100.0f;
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
        tag.putInt("Group", this.group);
        tag.putFloat("TemperatureC", this.temperatureC);
        tag.putFloat("IntegrityPercent", this.integrityPercent);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        this.targetPercent = Mth.clamp(tag.getFloat("TargetPercent"), 0.0f, 100.0f);
        this.currentPercent = Mth.clamp(tag.getFloat("CurrentPercent"), 0.0f, 100.0f);
        this.prevPercent = this.currentPercent;
        this.velocityPercent = 0.0f;
        this.group = Mth.clamp(tag.getInt("Group"), 0, 8);
        this.temperatureC = tag.contains("TemperatureC") ? VverThermal.clampTemperature(tag.getFloat("TemperatureC")) : VverThermal.AMBIENT_C;
        this.integrityPercent = tag.contains("IntegrityPercent") ? VverThermal.clampIntegrity(tag.getFloat("IntegrityPercent")) : 100.0f;
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
            this.group = Mth.clamp(tag.getInt("Group"), 0, 8);
            this.temperatureC = tag.contains("TemperatureC") ? VverThermal.clampTemperature(tag.getFloat("TemperatureC")) : VverThermal.AMBIENT_C;
            this.integrityPercent = tag.contains("IntegrityPercent") ? VverThermal.clampIntegrity(tag.getFloat("IntegrityPercent")) : 100.0f;
        }
    }
}
