package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
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

public class SteamGeneratorBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final double MAX_PRESSURE_ATM = 250.0;
    public static final double PRIMARY_GAUGE_MAX_ATM = 200.0;
    public static final double SECONDARY_GAUGE_MAX_ATM = 130.0;
    public static final double PRIMARY_VOLUME = 18.0;
    public static final double SECONDARY_VOLUME = 36.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean partsChecked;
    private boolean displayInitialized;
    private double primaryPressureAtm = 1.0;
    private double secondaryPressureAtm = 1.0;
    private double displayPrimaryPressureAtm = 1.0;
    private double displaySecondaryPressureAtm = 1.0;
    private int lastSyncedPrimaryTenths = Integer.MIN_VALUE;
    private int lastSyncedSecondaryTenths = Integer.MIN_VALUE;

    public SteamGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(VverRegistry.STEAM_GENERATOR_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SteamGeneratorBlockEntity blockEntity) {
        if (level.isClientSide) {
            blockEntity.updateDisplayPressures();
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!blockEntity.partsChecked) {
            blockEntity.partsChecked = SteamGeneratorBlock.placeParts(level, pos, state, false);
        }
        long offset = Math.floorMod(pos.hashCode(), 10);
        if ((level.getGameTime() + offset) % 10L == 0L) {
            PipePressureManager.reconcileSteamGenerator(serverLevel, blockEntity);
        }
    }

    public double getCircuitPressureAtm(SteamGeneratorBlock.Circuit circuit) {
        return circuit == SteamGeneratorBlock.Circuit.PRIMARY ? this.primaryPressureAtm : this.secondaryPressureAtm;
    }

    public double getDisplayPressureAtm(SteamGeneratorBlock.Circuit circuit) {
        if (this.level != null && !this.level.isClientSide) {
            return getCircuitPressureAtm(circuit);
        }
        return circuit == SteamGeneratorBlock.Circuit.PRIMARY ? this.displayPrimaryPressureAtm : this.displaySecondaryPressureAtm;
    }

    public void setCircuitPressureAtm(SteamGeneratorBlock.Circuit circuit, double pressureAtm) {
        double clamped = Mth.clamp(pressureAtm, 0.0, MAX_PRESSURE_ATM);
        if (circuit == SteamGeneratorBlock.Circuit.PRIMARY) {
            if (Math.abs(this.primaryPressureAtm - clamped) < 0.000001) {
                return;
            }
            this.primaryPressureAtm = clamped;
        } else {
            if (Math.abs(this.secondaryPressureAtm - clamped) < 0.000001) {
                return;
            }
            this.secondaryPressureAtm = clamped;
        }
        setChanged();
        syncIfNeeded();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "steam_generator", 0, state -> PlayState.STOP));
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
                this.worldPosition.getY() + 6.0,
                this.worldPosition.getZ() + 4.0
        );
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("PrimaryPressureAtm", this.primaryPressureAtm);
        tag.putDouble("SecondaryPressureAtm", this.secondaryPressureAtm);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.primaryPressureAtm = Mth.clamp(tag.contains("PrimaryPressureAtm") ? tag.getDouble("PrimaryPressureAtm") : 1.0, 0.0, MAX_PRESSURE_ATM);
        this.secondaryPressureAtm = Mth.clamp(tag.contains("SecondaryPressureAtm") ? tag.getDouble("SecondaryPressureAtm") : 1.0, 0.0, MAX_PRESSURE_ATM);
        if (!this.displayInitialized) {
            this.displayPrimaryPressureAtm = this.primaryPressureAtm;
            this.displaySecondaryPressureAtm = this.secondaryPressureAtm;
            this.displayInitialized = true;
        }
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

    private void updateDisplayPressures() {
        if (!this.displayInitialized) {
            this.displayPrimaryPressureAtm = this.primaryPressureAtm;
            this.displaySecondaryPressureAtm = this.secondaryPressureAtm;
            this.displayInitialized = true;
            return;
        }
        this.displayPrimaryPressureAtm = approach(this.displayPrimaryPressureAtm, this.primaryPressureAtm);
        this.displaySecondaryPressureAtm = approach(this.displaySecondaryPressureAtm, this.secondaryPressureAtm);
    }

    private static double approach(double current, double target) {
        double next = current + (target - current) * 0.25;
        return Math.abs(target - next) < 0.01 ? target : next;
    }

    private void syncIfNeeded() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        int primaryTenths = (int) Math.round(this.primaryPressureAtm * 10.0);
        int secondaryTenths = (int) Math.round(this.secondaryPressureAtm * 10.0);
        if (primaryTenths == this.lastSyncedPrimaryTenths && secondaryTenths == this.lastSyncedSecondaryTenths) {
            return;
        }
        this.lastSyncedPrimaryTenths = primaryTenths;
        this.lastSyncedSecondaryTenths = secondaryTenths;
        this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
    }
}
