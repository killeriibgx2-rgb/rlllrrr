package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
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

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class BshuBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final int SCAN_INTERVAL = 100;
    private static final int MAX_COMPONENTS = 2048;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final boolean[] power = new boolean[4];
    private final boolean[] toggles = new boolean[BshuToggle.values().length];
    private final long[] toggleChangedTick = new long[BshuToggle.values().length];
    private final boolean[] rodBanks = new boolean[9];
    private final int[] gcnSpeed = new int[3];
    private final float[] gcnIntegrity = new float[]{100.0f, 100.0f, 100.0f};
    private final boolean[] gcnPresent = new boolean[3];
    private final List<BlockPos> suzPositions = new ArrayList<>();
    private final List<BlockPos> tvsPositions = new ArrayList<>();
    private int lastRodInput;
    private int parameterListScroll;
    private boolean alarmActive;
    private boolean az5Active;
    private int dieselState;
    private long dieselStateTick = -10000L;
    private long lampTestTick = -10000L;
    private long acknowledgedErrors;
    private boolean linked;
    @Nullable
    private BlockPos seedPos;
    private int rescanCooldown;
    private int errorCheckCooldown;
    private boolean vesselPresent;
    private boolean vesselSealed;
    private boolean vesselLeaking;
    private boolean vesselCoreCovered = true;
    private int vesselPressureTenths = 10;
    private int vesselTemperatureC = 20;
    private int vesselCoolantM3;
    private int vesselCapacityM3;
    private int vesselGasPercent;
    private int vesselIntegrityPercent = 100;
    private int vesselPressureLimitTenths = 700;

    public BshuBlockEntity(BlockPos pos, BlockState state) {
        super(VverRegistry.BSHU_BLOCK_ENTITY.get(), pos, state);
        Arrays.fill(this.toggleChangedTick, -10000L);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BshuBlockEntity entity) {
        if (level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        if (entity.linked) {
            entity.rescanCooldown--;
            if (entity.rescanCooldown <= 0) {
                entity.rescanCooldown = SCAN_INTERVAL;
                entity.scanCore();
            }
        }
        if (entity.dieselState == 1 && now - entity.dieselStateTick >= 40L) {
            entity.dieselState = 3;
            entity.dieselStateTick = now;
            entity.sync();
        }
        entity.errorCheckCooldown--;
        if (entity.errorCheckCooldown <= 0) {
            entity.errorCheckCooldown = 10;
            if (entity.linked && entity.updateVesselState(entity.suzPositions, entity.tvsPositions)) {
                entity.sync();
            }
            long pruned = entity.acknowledgedErrors & entity.computeActiveErrorMask();
            if (pruned != entity.acknowledgedErrors) {
                entity.acknowledgedErrors = pruned;
                entity.sync();
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "bshu", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public boolean isPowered(int department) {
        return department >= 1 && department <= 3 && this.power[department];
    }

    public boolean isScreenPowered(BshuScreenType screen) {
        return screen != null && isPowered(screen.department());
    }

    public boolean getToggle(BshuToggle toggle) {
        return toggle != null && this.toggles[toggle.ordinal()];
    }

    public long getToggleChangedTick(BshuToggle toggle) {
        return toggle == null ? -10000L : this.toggleChangedTick[toggle.ordinal()];
    }

    public boolean isRodBankSelected(int bank) {
        return bank >= 1 && bank <= 8 && this.rodBanks[bank];
    }

    public boolean allRodBanksSelected() {
        for (int i = 1; i <= 8; i++) {
            if (!this.rodBanks[i]) {
                return false;
            }
        }
        return true;
    }

    public int getGcnSpeed(int index) {
        return index >= 0 && index < this.gcnSpeed.length ? this.gcnSpeed[index] : 0;
    }

    public float getGcnIntegrity(int index) {
        return index >= 0 && index < this.gcnIntegrity.length ? this.gcnIntegrity[index] : 100.0f;
    }

    public boolean isGcnPresent(int index) {
        return index >= 0 && index < this.gcnPresent.length && this.gcnPresent[index];
    }

    public int getGcnStrengthSeverity() {
        boolean anyPresent = false;
        float minimum = 100.0f;
        for (int i = 0; i < this.gcnPresent.length; i++) {
            if (this.gcnPresent[i]) {
                anyPresent = true;
                minimum = Math.min(minimum, this.gcnIntegrity[i]);
            }
        }
        if (!anyPresent) {
            return 2;
        }
        if (minimum <= VverThermal.GCN_CRITICAL_INTEGRITY_PERCENT) {
            return 2;
        }
        return minimum <= VverThermal.GCN_WARNING_INTEGRITY_PERCENT ? 1 : 0;
    }

    public boolean isErrorCritical(BshuError error) {
        if (error == BshuError.GCN_STRENGTH) {
            return getGcnStrengthSeverity() != 1;
        }
        return error != null && error.critical();
    }

    public int getLastRodInput() {
        return this.lastRodInput;
    }

    public int getParameterListScroll() {
        return this.parameterListScroll;
    }

    public void setParameterListScroll(int scroll) {
        int clamped = Mth.clamp(scroll, 0, 100000);
        if (this.parameterListScroll == clamped) {
            return;
        }
        this.parameterListScroll = clamped;
        this.setChanged();
        if (this.level instanceof ServerLevel) {
            sync();
        }
    }

    public boolean isAlarmActive() {
        return this.alarmActive;
    }

    public boolean isAz5Active() {
        return this.az5Active;
    }

    public int getDieselState() {
        return this.dieselState;
    }

    public boolean isLinked() {
        return this.linked;
    }

    public boolean isVesselPresent() {
        return this.vesselPresent;
    }

    public boolean isVesselSealed() {
        return this.vesselSealed;
    }

    public boolean isVesselLeaking() {
        return this.vesselLeaking;
    }

    public boolean isVesselCoreCovered() {
        return this.vesselCoreCovered;
    }

    public double getVesselPressureAtm() {
        return this.vesselPressureTenths / 10.0;
    }

    public double getVesselPressureLimitAtm() {
        return this.vesselPressureLimitTenths / 10.0;
    }

    public int getVesselTemperatureC() {
        return this.vesselTemperatureC;
    }

    public int getVesselCoolantM3() {
        return this.vesselCoolantM3;
    }

    public int getVesselCapacityM3() {
        return this.vesselCapacityM3;
    }

    public int getVesselGasPercent() {
        return this.vesselGasPercent;
    }

    public int getVesselIntegrityPercent() {
        return this.vesselIntegrityPercent;
    }

    @Nullable
    public BlockPos getSeedPos() {
        return this.seedPos;
    }

    public List<BlockPos> getSuzPositions() {
        return List.copyOf(this.suzPositions);
    }

    public List<BlockPos> getTvsPositions() {
        return List.copyOf(this.tvsPositions);
    }

    public boolean linkTo(BlockPos target) {
        if (this.level == null || this.level.isClientSide || this.linked) {
            return false;
        }
        BlockPos base = componentBase(this.level, target);
        if (base == null) {
            return false;
        }
        this.linked = true;
        this.seedPos = base;
        this.rescanCooldown = SCAN_INTERVAL;
        scanCore();
        sync();
        return true;
    }

    public void scanCore() {
        if (this.level == null || this.level.isClientSide || !this.linked || this.seedPos == null) {
            return;
        }
        if (!this.level.isLoaded(this.seedPos)) {
            return;
        }
        BlockPos seed = componentBase(this.level, this.seedPos);
        if (seed == null) {
            boolean vesselChanged = clearVesselState();
            if (!this.suzPositions.isEmpty() || !this.tvsPositions.isEmpty() || vesselChanged) {
                this.suzPositions.clear();
                this.tvsPositions.clear();
                sync();
            }
            return;
        }
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        visited.add(seed);
        queue.add(seed);
        while (!queue.isEmpty() && visited.size() < MAX_COMPONENTS) {
            BlockPos current = queue.remove();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos candidate = current.offset(dx, dy, dz);
                        if (!this.level.isLoaded(candidate)) {
                            continue;
                        }
                        BlockPos base = componentBase(this.level, candidate);
                        if (base != null && visited.add(base)) {
                            queue.add(base);
                            if (visited.size() >= MAX_COMPONENTS) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        List<BlockPos> nextSuz = new ArrayList<>();
        List<BlockPos> nextTvs = new ArrayList<>();
        for (BlockPos pos : visited) {
            BlockState state = this.level.getBlockState(pos);
            if (state.is(VverRegistry.SUZ_BLOCK.get())) {
                nextSuz.add(pos);
            } else if (state.is(VverRegistry.TVS_BLOCK.get())) {
                nextTvs.add(pos);
            }
        }
        Comparator<BlockPos> order = Comparator.comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt((BlockPos pos) -> pos.getZ())
                .thenComparingInt((BlockPos pos) -> pos.getX());
        nextSuz.sort(order);
        nextTvs.sort(order);
        boolean vesselChanged = updateVesselState(nextSuz, nextTvs);
        if (!nextSuz.equals(this.suzPositions) || !nextTvs.equals(this.tvsPositions) || vesselChanged) {
            this.suzPositions.clear();
            this.suzPositions.addAll(nextSuz);
            this.tvsPositions.clear();
            this.tvsPositions.addAll(nextTvs);
            sync();
        }
    }

    private boolean updateVesselState(List<BlockPos> suz, List<BlockPos> tvs) {
        if (!(this.level instanceof ServerLevel serverLevel) || (suz.isEmpty() && tvs.isEmpty())) {
            return clearVesselState();
        }
        List<BlockPos> bases = new ArrayList<>(suz.size() + tvs.size());
        bases.addAll(suz);
        bases.addAll(tvs);
        ReactorVesselData.Vessel vessel = ReactorVesselData.get(serverLevel).refreshForCore(serverLevel, bases);
        if (vessel == null) {
            return clearVesselState();
        }
        boolean present = true;
        boolean sealed = vessel.sealed();
        boolean leaking = vessel.leaking();
        boolean covered = vessel.coreCovered();
        int pressure = (int) Math.round(vessel.pressure() * 10.0);
        int temperature = (int) Math.round(vessel.temperature());
        int coolant = (int) Math.round(vessel.coolant());
        int capacity = (int) Math.round(vessel.capacity());
        int gas = (int) Math.round(vessel.gasPercent());
        int integrity = (int) Math.round(vessel.integrity());
        int limit = (int) Math.round(vessel.pressureLimit() * 10.0);
        boolean changed = this.vesselPresent != present
                || this.vesselSealed != sealed
                || this.vesselLeaking != leaking
                || this.vesselCoreCovered != covered
                || this.vesselPressureTenths != pressure
                || this.vesselTemperatureC != temperature
                || this.vesselCoolantM3 != coolant
                || this.vesselCapacityM3 != capacity
                || this.vesselGasPercent != gas
                || this.vesselIntegrityPercent != integrity
                || this.vesselPressureLimitTenths != limit;
        this.vesselPresent = present;
        this.vesselSealed = sealed;
        this.vesselLeaking = leaking;
        this.vesselCoreCovered = covered;
        this.vesselPressureTenths = pressure;
        this.vesselTemperatureC = temperature;
        this.vesselCoolantM3 = coolant;
        this.vesselCapacityM3 = capacity;
        this.vesselGasPercent = gas;
        this.vesselIntegrityPercent = integrity;
        this.vesselPressureLimitTenths = limit;
        return changed;
    }

    private boolean clearVesselState() {
        boolean changed = this.vesselPresent
                || this.vesselSealed
                || this.vesselLeaking
                || !this.vesselCoreCovered
                || this.vesselPressureTenths != 10
                || this.vesselTemperatureC != 20
                || this.vesselCoolantM3 != 0
                || this.vesselCapacityM3 != 0
                || this.vesselGasPercent != 0
                || this.vesselIntegrityPercent != 100
                || this.vesselPressureLimitTenths != 700;
        this.vesselPresent = false;
        this.vesselSealed = false;
        this.vesselLeaking = false;
        this.vesselCoreCovered = true;
        this.vesselPressureTenths = 10;
        this.vesselTemperatureC = 20;
        this.vesselCoolantM3 = 0;
        this.vesselCapacityM3 = 0;
        this.vesselGasPercent = 0;
        this.vesselIntegrityPercent = 100;
        this.vesselPressureLimitTenths = 700;
        return changed;
    }

    @Nullable
    public static BlockPos componentBase(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(VverRegistry.SUZ_BLOCK.get())) {
            BlockPos base = pos.below(state.getValue(SuzBlock.PART));
            BlockState baseState = level.getBlockState(base);
            return baseState.is(VverRegistry.SUZ_BLOCK.get()) && baseState.getValue(SuzBlock.PART) == 0 ? base : null;
        }
        if (state.is(VverRegistry.TVS_BLOCK.get())) {
            BlockPos base = pos.below(state.getValue(TvsBlock.PART));
            BlockState baseState = level.getBlockState(base);
            return baseState.is(VverRegistry.TVS_BLOCK.get()) && baseState.getValue(TvsBlock.PART) == 0 ? base : null;
        }
        return null;
    }

    public boolean press(BshuControl control, boolean pairOn) {
        if (this.level == null || this.level.isClientSide || control == null) {
            return false;
        }
        long now = this.level.getGameTime();
        if (control.pressable()) {
            VverNetwork.broadcastPress(this, control);
        }
        if (control.kind() == BshuControl.Kind.POWER) {
            int department = control.number();
            this.power[department] = !this.power[department];
            sync();
            return true;
        }
        if (!isPowered(control.department())) {
            return false;
        }
        switch (control.kind()) {
            case TOGGLE -> {
                if (control.toggle() != null) {
                    setToggle(control.toggle(), !getToggle(control.toggle()), now);
                }
            }
            case PAIR -> {
                if (control.toggle() != null) {
                    setToggle(control.toggle(), pairOn, now);
                }
            }
            case ROD_BANK -> pressRodBank(control.number());
            case MOMENTARY -> pressMomentary(control, now);
            case POWER -> {
            }
        }
        sync();
        return true;
    }

    private void pressRodBank(int bank) {
        if (bank == 0) {
            boolean target = !allRodBanksSelected();
            for (int i = 1; i <= 8; i++) {
                this.rodBanks[i] = target;
            }
        } else if (bank >= 1 && bank <= 8) {
            this.rodBanks[bank] = !this.rodBanks[bank];
        }
    }

    private void pressMomentary(BshuControl control, long now) {
        switch (control) {
            case STOP_NASOS -> {
                setToggle(BshuToggle.NASOS_1, false, now);
                setToggle(BshuToggle.NASOS_2, false, now);
                setToggle(BshuToggle.NASOS_3, false, now);
            }
            case TURBINE_START -> setToggle(BshuToggle.TURBINE, true, now);
            case TURBINE_STOP -> setToggle(BshuToggle.TURBINE, false, now);
            case LAMP_TEST -> this.lampTestTick = now;
            case KVITIR -> this.acknowledgedErrors = computeActiveErrorMask();
            case ALARM -> this.alarmActive = true;
            case ALARM_STOP -> this.alarmActive = false;
            case AZ5 -> activateAz5();
            case RESET -> resetAz5();
            case DIESEL_START -> {
                this.dieselState = 1;
                this.dieselStateTick = now;
            }
            case DIESEL_STOP -> {
                this.dieselState = 0;
                this.dieselStateTick = now;
            }
            case VENT_ALL_ON -> setAllVents(true, now);
            case VENT_ALL_OFF -> setAllVents(false, now);
            default -> {
            }
        }
    }

    private void setAllVents(boolean value, long now) {
        setToggle(BshuToggle.VENT_1, value, now);
        setToggle(BshuToggle.VENT_2, value, now);
        setToggle(BshuToggle.VENT_3, value, now);
        setToggle(BshuToggle.VENT_4, value, now);
    }

    private void setToggle(BshuToggle toggle, boolean value, long now) {
        int index = toggle.ordinal();
        if (this.toggles[index] != value) {
            this.toggles[index] = value;
            this.toggleChangedTick[index] = now;
        }
    }

    private void activateAz5() {
        this.az5Active = true;
        scanCore();
        if (this.level == null) {
            return;
        }
        for (BlockPos pos : this.suzPositions) {
            if (this.level.isLoaded(pos) && this.level.getBlockEntity(pos) instanceof SuzBlockEntity suz) {
                suz.setTargetPercent(0.0f);
            }
        }
    }

    private void resetAz5() {
        this.az5Active = false;
        if (this.level == null) {
            return;
        }
        for (BlockPos pos : this.suzPositions) {
            if (this.level.isLoaded(pos) && this.level.getBlockEntity(pos) instanceof SuzBlockEntity suz) {
                suz.setTargetPercent(suz.getCurrentPercent());
            }
        }
    }

    public boolean applyGcnInput(int field, int value) {
        if (this.level == null || this.level.isClientSide || !isScreenPowered(BshuScreenType.GCN) || field < 0 || field >= this.gcnSpeed.length) {
            return false;
        }
        this.gcnSpeed[field] = Mth.clamp(value, 0, 100);
        sync();
        return true;
    }

    public int applyRodInput(int value) {
        if (this.level == null || this.level.isClientSide || !isScreenPowered(BshuScreenType.CONTROL) || !this.linked || this.az5Active || getToggle(BshuToggle.ROD_HOLD)) {
            return -1;
        }
        scanCore();
        int clamped = Mth.clamp(value, 0, 100);
        int affected = 0;
        boolean allSelected = allRodBanksSelected();
        for (BlockPos pos : this.suzPositions) {
            if (!this.level.isLoaded(pos)) {
                continue;
            }
            if (this.level.getBlockEntity(pos) instanceof SuzBlockEntity suz
                    && (allSelected || (suz.getGroup() >= 1 && this.rodBanks[suz.getGroup()]))) {
                suz.setTargetPercent(clamped);
                affected++;
            }
        }
        if (affected > 0) {
            this.lastRodInput = clamped;
            sync();
        }
        return affected;
    }

    public long computeActiveErrorMask() {
        long mask = 0L;
        if (!isPowered(1)) {
            mask |= BshuError.POWER_1.bit();
        }
        if (!isPowered(2)) {
            mask |= BshuError.POWER_2.bit();
        }
        if (!isPowered(3)) {
            mask |= BshuError.POWER_3.bit();
        }
        if (this.az5Active) {
            mask |= BshuError.AZ5.bit();
        }
        if (this.alarmActive) {
            mask |= BshuError.ALARM.bit();
        }
        if (this.dieselState == 3) {
            mask |= BshuError.DG_FAILED.bit();
        }
        if (getGcnStrengthSeverity() > 0) {
            mask |= BshuError.GCN_STRENGTH.bit();
        }
        long gameTime = this.level == null ? 0L : this.level.getGameTime();
        if (hasDelayedFailure(gameTime, BshuToggle.VENT_1, BshuToggle.VENT_2, BshuToggle.VENT_3, BshuToggle.VENT_4)) {
            mask |= BshuError.VENT_FAILURE.bit();
        }
        if (hasDelayedFailure(gameTime, BshuToggle.CL_1, BshuToggle.CL_2, BshuToggle.CL_3, BshuToggle.CL_4, BshuToggle.SUMP_PUMP)) {
            mask |= BshuError.DRAIN_FAILURE.bit();
        }
        if (!this.linked || this.seedPos == null) {
            mask |= BshuError.NO_LINK.bit();
            return mask;
        }
        if (this.level != null && this.level.isLoaded(this.seedPos) && componentBase(this.level, this.seedPos) == null) {
            mask |= BshuError.LINK_LOST.bit();
        }
        if (this.suzPositions.isEmpty() && this.tvsPositions.isEmpty()) {
            mask |= BshuError.LINK_LOST.bit();
        }
        boolean allSelected = allRodBanksSelected();
        boolean selectedHasRod = false;
        if (this.level != null) {
            for (BlockPos pos : this.suzPositions) {
                if (!this.level.isLoaded(pos)) {
                    continue;
                }
                if (this.level.getBlockEntity(pos) instanceof SuzBlockEntity suz) {
                    if (allSelected || (suz.getGroup() >= 1 && this.rodBanks[suz.getGroup()])) {
                        selectedHasRod = true;
                    }
                    if (suz.getTemperatureC() >= VverThermal.SUZ_DAMAGE_C) {
                        mask |= BshuError.SUZ_OVERHEAT.bit();
                    }
                    if (suz.getTemperatureC() >= VverThermal.SUZ_CRITICAL_C || VverThermal.isCriticalIntegrity(suz.getIntegrityPercent())) {
                        mask |= BshuError.SUZ_STRENGTH_CRITICAL.bit();
                    }
                    if (suz.isStuck()) {
                        mask |= BshuError.SUZ_STUCK.bit();
                    }
                }
            }
            for (BlockPos pos : this.tvsPositions) {
                if (!this.level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = this.level.getBlockState(pos);
                if (state.is(VverRegistry.TVS_BLOCK.get()) && !state.getValue(TvsBlock.HAS_RODS)) {
                    mask |= BshuError.TVS_EMPTY.bit();
                }
                if (this.level.getBlockEntity(pos) instanceof TvsBlockEntity tvs) {
                    if (tvs.getTemperatureC() >= VverThermal.TVS_DAMAGE_C) {
                        mask |= BshuError.ROD_OVERHEAT.bit();
                    }
                    if (tvs.getTemperatureC() >= VverThermal.TVS_CRITICAL_C || VverThermal.isCriticalIntegrity(tvs.getIntegrityPercent())) {
                        mask |= BshuError.ROD_STRENGTH_CRITICAL.bit();
                    }
                    if (tvs.isShellMelted() && tvs.getIntegrityPercent() <= 0.0f) {
                        mask |= BshuError.MELTDOWN.bit();
                    }
                    if (state.is(VverRegistry.TVS_BLOCK.get()) && state.getValue(TvsBlock.HAS_RODS) && tvs.getTemperatureC() >= VverThermal.TVS_FAST_DAMAGE_C) {
                        mask |= BshuError.HYDROGEN.bit();
                    }
                    if (tvs.getIntegrityPercent() <= 50.0f) {
                        mask |= BshuError.ACTIVITY_RELEASE.bit();
                    }
                }
            }
        }
        if (this.vesselPresent && !this.vesselSealed) {
            mask |= BshuError.HULL_NOT_SEALED.bit();
        }
        if (this.vesselPresent && (this.vesselLeaking || (this.vesselCoolantM3 > 0 && !this.vesselCoreCovered))) {
            mask |= BshuError.COOLANT_LOSS.bit();
        }
        if (this.vesselPresent && this.vesselPressureTenths >= Math.max(650, this.vesselPressureLimitTenths - 50)) {
            mask |= BshuError.HIGH_PRESSURE.bit();
        }
        if (this.vesselPresent && this.vesselIntegrityPercent <= 75) {
            mask |= BshuError.VESSEL_STRENGTH.bit();
        }
        if (!this.suzPositions.isEmpty() && !selectedHasRod) {
            mask |= BshuError.NO_BANK.bit();
        }
        if (!getToggle(BshuToggle.GCN_1) && !getToggle(BshuToggle.GCN_2) && !getToggle(BshuToggle.GCN_3)) {
            mask |= BshuError.GCN_OFF.bit();
        }
        return mask;
    }

    public boolean hasDelayedFailure(long gameTime, BshuToggle... toggles) {
        for (BshuToggle toggle : toggles) {
            if (getDelayedFailureState(toggle, gameTime) == BshuLampState.RED) {
                return true;
            }
        }
        return false;
    }

    public long getAcknowledgedErrorMask() {
        return this.acknowledgedErrors;
    }

    public boolean hasUnacknowledgedErrors() {
        return (computeActiveErrorMask() & ~this.acknowledgedErrors) != 0L;
    }

    public BshuLampState getLampState(String lampName, long gameTime) {
        int department = lampDepartment(lampName);
        if (department == 0 || !isPowered(department)) {
            return BshuLampState.OFF;
        }
        if (isLampTestActive(gameTime)) {
            long elapsed = gameTime - this.lampTestTick;
            return (elapsed / 5L) % 2L == 0L ? nominalLampColor(lampName) : BshuLampState.OFF;
        }
        if (lampName.equals("power_1l") || lampName.equals("power_2l") || lampName.equals("power_3l")) {
            return BshuLampState.GREEN;
        }
        if (lampName.equals("turbin_l")) {
            return getToggle(BshuToggle.TURBINE) ? BshuLampState.GREEN : BshuLampState.RED;
        }
        if (lampName.equals("t")) {
            return this.alarmActive ? blink(BshuLampState.RED, gameTime) : BshuLampState.OFF;
        }
        if (lampName.startsWith("PUMP_")) {
            int index = lampName.charAt(5) - '1';
            BshuToggle toggle = index == 0 ? BshuToggle.NASOS_1 : index == 1 ? BshuToggle.NASOS_2 : BshuToggle.NASOS_3;
            return getToggle(toggle) ? BshuLampState.GREEN : BshuLampState.OFF;
        }
        if (lampName.startsWith("gcn_")) {
            int index = lampName.charAt(4) - '1';
            BshuToggle toggle = index == 0 ? BshuToggle.GCN_1 : index == 1 ? BshuToggle.GCN_2 : BshuToggle.GCN_3;
            if (!getToggle(toggle)) {
                return BshuLampState.OFF;
            }
            return getGcnSpeed(index) > 0 ? BshuLampState.GREEN : blink(BshuLampState.RED, gameTime);
        }
        if (lampName.equals("kv1") || lampName.equals("kv2") || lampName.equals("kv3")) {
            return BshuLampState.OFF;
        }
        if (lampName.equals("sbros_pressure")) {
            return getToggle(BshuToggle.PRESSURE) ? BshuLampState.GREEN : BshuLampState.OFF;
        }
        if (lampName.length() == 2 && lampName.charAt(0) == 'r' && Character.isDigit(lampName.charAt(1))) {
            int bank = lampName.charAt(1) - '0';
            boolean on = bank == 0 ? allRodBanksSelected() : isRodBankSelected(bank);
            return on ? BshuLampState.GREEN : BshuLampState.OFF;
        }
        if (lampName.equals("bor_l")) {
            return getToggle(BshuToggle.BOR) ? BshuLampState.RED : BshuLampState.OFF;
        }
        if (lampName.equals("water_l")) {
            return getToggle(BshuToggle.WATER) ? BshuLampState.RED : BshuLampState.OFF;
        }
        if (lampName.equals("saoz_l")) {
            return getToggle(BshuToggle.SAOZ) ? BshuLampState.RED : BshuLampState.OFF;
        }
        if (lampName.equals("dg_l")) {
            return switch (this.dieselState) {
                case 1 -> BshuLampState.YELLOW;
                case 2 -> BshuLampState.GREEN;
                case 3 -> BshuLampState.RED;
                default -> BshuLampState.OFF;
            };
        }
        if (lampName.equals("VENT_l")) {
            BshuToggle latest = latestVentToggle();
            return latest == null ? BshuLampState.OFF : delayedFailureState(latest, gameTime);
        }
        if (lampName.equals("SUMP_PUMP_l")) {
            return delayedFailureState(BshuToggle.SUMP_PUMP, gameTime);
        }
        if (lampName.equals("isolete")) {
            return delayedFailureState(BshuToggle.ISOLATE, gameTime);
        }
        String toggleName = lampName.equals("cl4_l") ? "cl_4" : lampName.endsWith("l") ? lampName.substring(0, lampName.length() - 1) : lampName;
        BshuToggle toggle = BshuToggle.byBone(toggleName);
        if (toggle != null) {
            if (!getToggle(toggle)) {
                return BshuLampState.OFF;
            }
            if (department == 3) {
                return delayedFailureState(toggle, gameTime);
            }
            return BshuLampState.GREEN;
        }
        return BshuLampState.OFF;
    }

    public boolean isKvitirLit(long gameTime) {
        return isPowered(2) && hasUnacknowledgedErrors() && (gameTime / 10L) % 2L == 0L;
    }

    public BshuLampState getDelayedFailureState(BshuToggle toggle, long gameTime) {
        return delayedFailureState(toggle, gameTime);
    }

    private BshuLampState delayedFailureState(BshuToggle toggle, long gameTime) {
        if (!getToggle(toggle)) {
            return BshuLampState.OFF;
        }
        return gameTime - getToggleChangedTick(toggle) < 40L ? BshuLampState.YELLOW : BshuLampState.RED;
    }

    @Nullable
    private BshuToggle latestVentToggle() {
        BshuToggle latest = null;
        long latestTick = Long.MIN_VALUE;
        for (BshuToggle toggle : new BshuToggle[]{BshuToggle.VENT_1, BshuToggle.VENT_2, BshuToggle.VENT_3, BshuToggle.VENT_4}) {
            if (getToggle(toggle) && getToggleChangedTick(toggle) > latestTick) {
                latest = toggle;
                latestTick = getToggleChangedTick(toggle);
            }
        }
        return latest;
    }

    private boolean isLampTestActive(long gameTime) {
        long elapsed = gameTime - this.lampTestTick;
        return elapsed >= 0L && elapsed < 50L;
    }

    private BshuLampState nominalLampColor(String lampName) {
        if (lampName.equals("t") || lampName.equals("turbin_l")) {
            return lampName.equals("t") ? BshuLampState.RED : BshuLampState.GREEN;
        }
        if (lampName.equals("dg_l") || lampName.startsWith("kv")) {
            return BshuLampState.YELLOW;
        }
        return BshuLampState.GREEN;
    }

    private BshuLampState blink(BshuLampState state, long gameTime) {
        return (gameTime / 10L) % 2L == 0L ? state : BshuLampState.OFF;
    }

    private int lampDepartment(String lampName) {
        if (lampName.equals("power_1l") || lampName.equals("turbin_l") || lampName.startsWith("PUMP_")) {
            return 1;
        }
        if (lampName.length() == 3 && (lampName.charAt(0) == 'a' || lampName.charAt(0) == 'b' || lampName.charAt(0) == 'c') && lampName.charAt(2) == 'l') {
            return 1;
        }
        if (lampName.equals("power_3l") || lampName.startsWith("bp_") || lampName.startsWith("cl_") || lampName.equals("cl4_l") || lampName.startsWith("vent_")
                || lampName.equals("VENT_l") || lampName.equals("SUMP_PUMP_l") || lampName.equals("isolete")) {
            return 3;
        }
        return 2;
    }

    private void sync() {
        setChanged();
        if (this.level instanceof ServerLevel) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(
                this.worldPosition.getX() - 9.0,
                this.worldPosition.getY() - 1.0,
                this.worldPosition.getZ() - 9.0,
                this.worldPosition.getX() + 10.0,
                this.worldPosition.getY() + 8.0,
                this.worldPosition.getZ() + 10.0
        );
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeState(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readState(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeState(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readState(tag);
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
            readState(tag);
        }
    }

    private void writeState(CompoundTag tag) {
        int powerMask = 0;
        for (int i = 1; i <= 3; i++) {
            if (this.power[i]) {
                powerMask |= 1 << i;
            }
        }
        long toggleMask = 0L;
        for (BshuToggle toggle : BshuToggle.values()) {
            if (this.toggles[toggle.ordinal()]) {
                toggleMask |= 1L << toggle.ordinal();
            }
        }
        int rodMask = 0;
        for (int i = 1; i <= 8; i++) {
            if (this.rodBanks[i]) {
                rodMask |= 1 << i;
            }
        }
        tag.putInt("PowerMask", powerMask);
        tag.putLong("ToggleMask", toggleMask);
        tag.putLongArray("ToggleTicks", Arrays.copyOf(this.toggleChangedTick, this.toggleChangedTick.length));
        tag.putInt("RodMask", rodMask);
        tag.putIntArray("GcnSpeed", Arrays.copyOf(this.gcnSpeed, this.gcnSpeed.length));
        int[] gcnIntegrityValues = new int[this.gcnIntegrity.length];
        int gcnPresentMask = 0;
        for (int i = 0; i < this.gcnIntegrity.length; i++) {
            gcnIntegrityValues[i] = Math.round(this.gcnIntegrity[i]);
            if (this.gcnPresent[i]) {
                gcnPresentMask |= 1 << i;
            }
        }
        tag.putIntArray("GcnIntegrity", gcnIntegrityValues);
        tag.putInt("GcnPresentMask", gcnPresentMask);
        tag.putInt("LastRodInput", this.lastRodInput);
        tag.putInt("ParameterListScroll", this.parameterListScroll);
        tag.putBoolean("Alarm", this.alarmActive);
        tag.putBoolean("Az5", this.az5Active);
        tag.putInt("DieselState", this.dieselState);
        tag.putLong("DieselTick", this.dieselStateTick);
        tag.putLong("LampTestTick", this.lampTestTick);
        tag.putLong("AcknowledgedErrors", this.acknowledgedErrors);
        tag.putBoolean("Linked", this.linked);
        if (this.seedPos != null) {
            tag.putLong("SeedPos", this.seedPos.asLong());
        }
        tag.putLongArray("SuzPositions", this.suzPositions.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("TvsPositions", this.tvsPositions.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putBoolean("VesselPresent", this.vesselPresent);
        tag.putBoolean("VesselSealed", this.vesselSealed);
        tag.putBoolean("VesselLeaking", this.vesselLeaking);
        tag.putBoolean("VesselCoreCovered", this.vesselCoreCovered);
        tag.putInt("VesselPressureTenths", this.vesselPressureTenths);
        tag.putInt("VesselTemperatureC", this.vesselTemperatureC);
        tag.putInt("VesselCoolantM3", this.vesselCoolantM3);
        tag.putInt("VesselCapacityM3", this.vesselCapacityM3);
        tag.putInt("VesselGasPercent", this.vesselGasPercent);
        tag.putInt("VesselIntegrityPercent", this.vesselIntegrityPercent);
        tag.putInt("VesselPressureLimitTenths", this.vesselPressureLimitTenths);
    }

    private void readState(CompoundTag tag) {
        int powerMask = tag.getInt("PowerMask");
        for (int i = 1; i <= 3; i++) {
            this.power[i] = (powerMask & (1 << i)) != 0;
        }
        long toggleMask = tag.getLong("ToggleMask");
        for (BshuToggle toggle : BshuToggle.values()) {
            this.toggles[toggle.ordinal()] = (toggleMask & (1L << toggle.ordinal())) != 0L;
        }
        long[] toggleTicks = tag.getLongArray("ToggleTicks");
        Arrays.fill(this.toggleChangedTick, -10000L);
        System.arraycopy(toggleTicks, 0, this.toggleChangedTick, 0, Math.min(toggleTicks.length, this.toggleChangedTick.length));
        int rodMask = tag.getInt("RodMask");
        for (int i = 1; i <= 8; i++) {
            this.rodBanks[i] = (rodMask & (1 << i)) != 0;
        }
        int[] speeds = tag.getIntArray("GcnSpeed");
        Arrays.fill(this.gcnSpeed, 0);
        System.arraycopy(speeds, 0, this.gcnSpeed, 0, Math.min(speeds.length, this.gcnSpeed.length));
        for (int i = 0; i < this.gcnSpeed.length; i++) {
            this.gcnSpeed[i] = Mth.clamp(this.gcnSpeed[i], 0, 100);
        }
        int[] gcnIntegrityValues = tag.getIntArray("GcnIntegrity");
        Arrays.fill(this.gcnIntegrity, 100.0f);
        for (int i = 0; i < Math.min(gcnIntegrityValues.length, this.gcnIntegrity.length); i++) {
            this.gcnIntegrity[i] = VverThermal.clampIntegrity(gcnIntegrityValues[i]);
        }
        int gcnPresentMask = tag.getInt("GcnPresentMask");
        for (int i = 0; i < this.gcnPresent.length; i++) {
            this.gcnPresent[i] = (gcnPresentMask & (1 << i)) != 0;
        }
        this.lastRodInput = Mth.clamp(tag.getInt("LastRodInput"), 0, 100);
        this.parameterListScroll = Mth.clamp(tag.getInt("ParameterListScroll"), 0, 100000);
        this.alarmActive = tag.getBoolean("Alarm");
        this.az5Active = tag.getBoolean("Az5");
        this.dieselState = Mth.clamp(tag.getInt("DieselState"), 0, 3);
        this.dieselStateTick = tag.getLong("DieselTick");
        this.lampTestTick = tag.contains("LampTestTick") ? tag.getLong("LampTestTick") : -10000L;
        this.acknowledgedErrors = tag.getLong("AcknowledgedErrors");
        this.linked = tag.getBoolean("Linked");
        this.seedPos = tag.contains("SeedPos") ? BlockPos.of(tag.getLong("SeedPos")) : null;
        this.suzPositions.clear();
        for (long value : tag.getLongArray("SuzPositions")) {
            this.suzPositions.add(BlockPos.of(value));
        }
        this.tvsPositions.clear();
        for (long value : tag.getLongArray("TvsPositions")) {
            this.tvsPositions.add(BlockPos.of(value));
        }
        this.vesselPresent = tag.getBoolean("VesselPresent");
        this.vesselSealed = tag.getBoolean("VesselSealed");
        this.vesselLeaking = tag.getBoolean("VesselLeaking");
        this.vesselCoreCovered = !tag.contains("VesselCoreCovered") || tag.getBoolean("VesselCoreCovered");
        this.vesselPressureTenths = Mth.clamp(tag.contains("VesselPressureTenths") ? tag.getInt("VesselPressureTenths") : 10, 0, 2500);
        this.vesselTemperatureC = Mth.clamp(tag.contains("VesselTemperatureC") ? tag.getInt("VesselTemperatureC") : 20, -50, 4000);
        this.vesselCoolantM3 = Math.max(0, tag.getInt("VesselCoolantM3"));
        this.vesselCapacityM3 = Math.max(0, tag.getInt("VesselCapacityM3"));
        this.vesselGasPercent = Mth.clamp(tag.getInt("VesselGasPercent"), 0, 100);
        this.vesselIntegrityPercent = Mth.clamp(tag.contains("VesselIntegrityPercent") ? tag.getInt("VesselIntegrityPercent") : 100, 0, 100);
        this.vesselPressureLimitTenths = Mth.clamp(tag.contains("VesselPressureLimitTenths") ? tag.getInt("VesselPressureLimitTenths") : 700, 0, 2500);
    }
}
