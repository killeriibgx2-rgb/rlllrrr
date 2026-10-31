package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public class ReactorVesselData extends SavedData {
    private static final String DATA_NAME = "fly_drone_reactor_vessels";
    private static final double HYDRAULIC_STIFFNESS = 5000.0;
    private final List<Vessel> vessels = new ArrayList<>();
    private final Set<BlockPos> dirtyPositions = new LinkedHashSet<>();
    private final List<Burst> bursts = new ArrayList<>();

    public ReactorVesselData() {
    }

    public static ReactorVesselData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ReactorVesselData::load, ReactorVesselData::new, DATA_NAME);
    }

    public static ReactorVesselData load(CompoundTag tag) {
        ReactorVesselData data = new ReactorVesselData();
        ListTag list = tag.getList("Vessels", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.vessels.add(Vessel.fromTag(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Vessel vessel : this.vessels) {
            list.add(vessel.toTag());
        }
        tag.put("Vessels", list);
        return tag;
    }

    @Nullable
    public Vessel findOrScan(ServerLevel level, BlockPos anchor) {
        return findOrScan(level, anchor, List.of());
    }

    @Nullable
    public Vessel findOrScan(ServerLevel level, BlockPos anchor, Collection<BlockPos> preferredCoreBases) {
        ReactorVesselScanner.Result result = ReactorVesselScanner.scan(level, anchor, preferredCoreBases);
        if (!result.hullPresent) {
            return null;
        }
        Vessel matched = match(result);
        if (!result.complete) {
            return matched;
        }
        if (matched != null
                && matched.capacity > 0.0
                && result.capacity < matched.capacity * 0.35
                && result.shell.size() < Math.max(1, matched.shellPositions.size() / 2)) {
            return matched;
        }
        if (matched == null) {
            matched = new Vessel();
            this.vessels.add(matched);
        }
        updateGeometry(matched, result, level.getGameTime());
        Vessel updated = matched;
        this.dirtyPositions.removeIf(pos -> updated.shellPositions.contains(pos.asLong()));
        return updated;
    }

    @Nullable
    public Vessel refreshForCore(ServerLevel level, Collection<BlockPos> coreBases) {
        if (coreBases == null || coreBases.isEmpty()) {
            return null;
        }
        Vessel cached = findByCore(coreBases);
        long now = level.getGameTime();
        if (cached != null && now - cached.lastScanTick < 100L) {
            return cached;
        }
        BlockPos anchor = null;
        for (BlockPos base : coreBases) {
            if (base != null && level.isLoaded(base)) {
                anchor = base;
                break;
            }
        }
        if (anchor == null) {
            return cached;
        }
        Vessel scanned = findOrScan(level, anchor, coreBases);
        return scanned != null ? scanned : cached;
    }

    @Nullable
    public Vessel getVessel(UUID id) {
        if (id == null) {
            return null;
        }
        for (Vessel vessel : this.vessels) {
            if (vessel.id.equals(id)) {
                return vessel;
            }
        }
        return null;
    }

    public void markDirty(BlockPos pos) {
        if (pos != null) {
            this.dirtyPositions.add(pos.immutable());
        }
    }

    public void repairAt(BlockPos pos) {
        Vessel vessel = findByShell(pos);
        if (vessel == null) {
            for (Direction direction : Direction.values()) {
                vessel = findByShell(pos.relative(direction));
                if (vessel != null) {
                    break;
                }
            }
        }
        this.bursts.removeIf(burst -> burst.pos.equals(pos));
        if (vessel != null && vessel.pressure <= vessel.pressureLimit) {
            vessel.integrity = Math.min(100.0, vessel.integrity + 8.0);
            setDirty();
        }
    }

    public void onHullRemoved(BlockPos pos, BlockState oldState) {
        markDirty(pos);
        Vessel vessel = findByShell(pos);
        if (vessel == null) {
            return;
        }
        vessel.sealed = false;
        vessel.leakPositions.add(pos.asLong());
        Direction direction = Direction.UP;
        if (oldState.getBlock() instanceof HullNozzleBlock) {
            direction = oldState.getValue(HullNozzleBlock.FACING);
        } else {
            for (Direction candidate : Direction.values()) {
                BlockPos cavityNeighbor = pos.relative(candidate);
                if (vessel.cavityPositions.contains(cavityNeighbor.asLong()) || vessel.corePositions.contains(cavityNeighbor.asLong())) {
                    direction = candidate.getOpposite();
                    break;
                }
            }
        }
        vessel.leakDirections.put(pos.asLong(), direction.ordinal());
        vessel.lowestLeakY = Math.min(vessel.lowestLeakY, pos.getY());
        boolean belowLevel = pos.getY() + 1.0 <= vessel.liquidLevelY();
        if (vessel.pressure >= 3.0 || (belowLevel && vessel.coolant > vessel.retainedCapacity)) {
            startBurst(vessel, pos.immutable(), direction);
        }
        setDirty();
    }

    public double addCoolant(Vessel vessel, double delta) {
        if (vessel == null || vessel.capacity <= 0.0 || delta == 0.0) {
            return 0.0;
        }
        double changed = 0.0;
        if (delta > 0.0) {
            if (!vessel.sealed) {
                double before = vessel.coolant;
                vessel.coolant = Math.min(vessel.capacity, vessel.coolant + delta);
                changed = vessel.coolant - before;
            } else {
                double beforeGas = gasVolume(vessel);
                double beforePressure = vessel.pressure;
                double space = Math.max(0.0, vessel.capacity - vessel.coolant);
                double liquid = Math.min(delta, space);
                vessel.coolant += liquid;
                double extra = delta - liquid;
                if (extra > 0.0) {
                    vessel.compression += extra;
                }
                double afterGas = gasVolume(vessel);
                if (afterGas > 0.000001) {
                    if (vessel.gasAmount <= 0.0) {
                        vessel.gasAmount = beforeGas * Math.max(1.0, beforePressure);
                    }
                    vessel.pressure = clampPressure(vessel.gasAmount / afterGas + saturationPressureAtm(vessel.temperature));
                } else {
                    if (beforeGas > 0.000001) {
                        double squeeze = vessel.gasAmount / Math.max(0.05, beforeGas * 0.15);
                        vessel.fullPressure = Mth.clamp(squeeze, Math.max(1.0, beforePressure), 90.0);
                    }
                    vessel.pressure = clampPressure(Math.max(beforePressure, vessel.fullPressure + HYDRAULIC_STIFFNESS * vessel.compression / vessel.capacity));
                }
                changed = liquid + Math.max(0.0, extra);
            }
        } else {
            double remove = -delta;
            double beforeGas = gasVolume(vessel);
            double beforePressure = vessel.pressure;
            double removedCompression = Math.min(vessel.compression, remove);
            vessel.compression -= removedCompression;
            remove -= removedCompression;
            double removedLiquid = Math.min(vessel.coolant, remove);
            vessel.coolant -= removedLiquid;
            changed = -(removedCompression + removedLiquid);
            if (vessel.sealed) {
                double afterGas = gasVolume(vessel);
                if (afterGas > 0.000001) {
                    if (beforeGas <= 0.000001) {
                        vessel.gasAmount = Math.max(0.01, beforePressure * afterGas);
                        vessel.pressure = beforePressure;
                    } else {
                        if (vessel.gasAmount <= 0.0) {
                            vessel.gasAmount = beforeGas * Math.max(1.0, beforePressure);
                        }
                        vessel.pressure = clampPressure(vessel.gasAmount / afterGas + saturationPressureAtm(vessel.temperature));
                    }
                } else {
                    vessel.pressure = clampPressure(Math.max(1.0, vessel.fullPressure + HYDRAULIC_STIFFNESS * vessel.compression / vessel.capacity));
                }
            }
        }
        vessel.coolant = Mth.clamp(vessel.coolant, 0.0, vessel.capacity);
        vessel.compression = Math.max(0.0, vessel.compression);
        setDirty();
        return changed;
    }

    public void tick(ServerLevel level) {
        tickBursts(level);
        long gameTime = level.getGameTime();
        if (gameTime % 20L == 0L) {
            processDirty(level);
        }
        if (gameTime % 5L != 0L) {
            return;
        }
        for (Vessel vessel : new ArrayList<>(this.vessels)) {
            if (!level.isLoaded(BlockPos.of(vessel.anchor))) {
                continue;
            }
            simulate(level, vessel, 0.25);
        }
    }

    private void processDirty(ServerLevel level) {
        int budget = 2;
        while (budget > 0 && !this.dirtyPositions.isEmpty()) {
            Iterator<BlockPos> iterator = this.dirtyPositions.iterator();
            BlockPos pos = iterator.next();
            iterator.remove();
            budget--;
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockState(pos).getBlock() instanceof HullBlock) {
                findOrScan(level, pos);
                continue;
            }
            Vessel vessel = findByShell(pos);
            if (vessel == null) {
                continue;
            }
            BlockPos remaining = null;
            for (Direction direction : Direction.values()) {
                BlockPos candidate = pos.relative(direction);
                if (level.isLoaded(candidate) && level.getBlockState(candidate).getBlock() instanceof HullBlock) {
                    remaining = candidate;
                    break;
                }
            }
            if (remaining == null) {
                for (int dx = -1; dx <= 1 && remaining == null; dx++) {
                    for (int dy = -1; dy <= 1 && remaining == null; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            BlockPos candidate = pos.offset(dx, dy, dz);
                            if (level.isLoaded(candidate) && level.getBlockState(candidate).getBlock() instanceof HullBlock) {
                                remaining = candidate;
                                break;
                            }
                        }
                    }
                }
            }
            if (remaining == null) {
                for (long packed : vessel.shellPositions) {
                    BlockPos candidate = BlockPos.of(packed);
                    if (level.isLoaded(candidate) && level.getBlockState(candidate).getBlock() instanceof HullBlock) {
                        remaining = candidate;
                        break;
                    }
                }
            }
            if (remaining != null) {
                findOrScan(level, remaining);
            } else if (vessel.coolant <= 0.001 && vessel.pressure <= 1.05) {
                this.vessels.remove(vessel);
                setDirty();
            } else {
                vessel.sealed = false;
                setDirty();
            }
        }
    }

    private void simulate(ServerLevel level, Vessel vessel, double dt) {
        boolean changed = false;
        vessel.leaking = false;
        vessel.leakRate = 0.0;
        if (vessel.capacity <= 0.0) {
            return;
        }
        if (!vessel.sealed) {
            double area = Math.max(1.0, vessel.leakPositions.size());
            double tau = Mth.clamp(vessel.capacity / Math.max(1.0, area * 10.0), 0.25, 6.0);
            double factor = 1.0 - Math.exp(-dt / tau);
            if (Math.abs(vessel.pressure - 1.0) > 0.001) {
                vessel.pressure += (1.0 - vessel.pressure) * factor;
                changed = true;
            }
            if (vessel.compression > 0.0) {
                vessel.compression *= Math.exp(-dt / tau);
                if (vessel.compression < 0.0001) {
                    vessel.compression = 0.0;
                }
                changed = true;
            }
            if (vessel.coolant > vessel.retainedCapacity + 0.000001) {
                double excess = vessel.coolant - vessel.retainedCapacity;
                double head = Math.max(0.0, vessel.liquidLevelY() - vessel.lowestLeakY);
                double rate = area * (0.4 + 0.35 * head + 0.12 * Math.max(0.0, vessel.pressure - 1.0));
                double drained = Math.min(excess, rate * dt);
                vessel.coolant -= drained;
                vessel.leakRate = drained / dt;
                vessel.leaking = drained > 0.000001;
                changed = true;
            } else if (vessel.pressure > 2.0 && !vessel.leakPositions.isEmpty()) {
                vessel.leaking = true;
            }
            if (vessel.leaking && level.getGameTime() % 10L == 0L) {
                spawnLeakFx(level, vessel);
            }
        } else {
            recalculateSealedPressure(vessel);
            if (vessel.pressure > vessel.pressureLimit) {
                double damage = (vessel.pressure - vessel.pressureLimit) * 0.35 * dt;
                vessel.integrity = Math.max(0.0, vessel.integrity - damage);
                changed = true;
                if (vessel.integrity <= 0.0) {
                    rupture(level, vessel);
                }
            }
        }
        if (vessel.coolant < 0.0) {
            vessel.coolant = 0.0;
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    private void rupture(ServerLevel level, Vessel vessel) {
        BlockPos target = null;
        int bestScore = Integer.MAX_VALUE;
        for (long packed : vessel.shellPositions) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof HullBlock hull)) {
                continue;
            }
            int score = switch (hull.hullType()) {
                case LID -> 0;
                case NOZZLE -> 1;
                case WALL -> 2;
                case BOTTOM -> 3;
            };
            score = score * 1000 - pos.getY();
            if (target == null || score < bestScore) {
                target = pos;
                bestScore = score;
            }
        }
        if (target != null) {
            vessel.integrity = 35.0;
            setDirty();
            level.destroyBlock(target, false);
        }
    }

    private void startBurst(Vessel vessel, BlockPos pos, Direction direction) {
        double pressureFactor = Mth.clamp((vessel.pressure - 1.0) / 64.0, 0.0, 1.0);
        double head = Math.max(0.0, vessel.liquidLevelY() - (pos.getY() + 1.0));
        double headFactor = Mth.clamp(head / 8.0, 0.0, 0.35);
        double temperatureFactor = vessel.temperature >= 100.0 ? 1.0 : 0.55;
        double intensity = Mth.clamp(0.12 + pressureFactor * temperatureFactor + headFactor, 0.0, 1.0);
        int duration = (int) (45.0 + intensity * 140.0 + Math.min(60.0, vessel.capacity / 10.0));
        this.bursts.removeIf(burst -> burst.pos.equals(pos));
        this.bursts.add(new Burst(vessel.id, pos, direction, intensity, duration, vessel.temperature));
    }

    private void tickBursts(ServerLevel level) {
        Iterator<Burst> iterator = this.bursts.iterator();
        while (iterator.hasNext()) {
            Burst burst = iterator.next();
            if (!level.isLoaded(burst.pos)) {
                continue;
            }
            if (level.getBlockState(burst.pos).getBlock() instanceof HullBlock) {
                iterator.remove();
                continue;
            }
            burst.age++;
            if (burst.age == 1) {
                level.playSound(null, burst.pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.45f + (float) burst.intensity * 0.7f, 0.65f);
                level.playSound(null, burst.pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.55f);
            } else if (burst.age % 20 == 0) {
                level.playSound(null, burst.pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35f + (float) burst.intensity * 0.35f, 0.7f);
            }
            spawnBurstParticles(level, burst);
            if (burst.age % 10 == 0) {
                damageInJet(level, burst);
            }
            if (burst.age >= burst.duration) {
                iterator.remove();
            }
        }
    }

    private void spawnBurstParticles(ServerLevel level, Burst burst) {
        Vec3 center = Vec3.atCenterOf(burst.pos);
        Vec3 dir = new Vec3(burst.direction.getStepX(), burst.direction.getStepY(), burst.direction.getStepZ());
        double progress = Math.min(1.0, burst.age / 12.0);
        double fade = 1.0 - (double) burst.age / Math.max(1, burst.duration);
        double jetLength = (3.0 + burst.intensity * 11.0) * progress;
        for (int i = 0; i < 6; i++) {
            double distance = (i + level.random.nextDouble()) / 6.0 * jetLength;
            Vec3 p = center.add(dir.scale(distance));
            double rise = burst.direction.getAxis().isHorizontal() ? distance * distance * 0.018 * (0.5 + burst.intensity) : 0.0;
            double spread = 0.16 + distance * 0.09;
            int count = Math.max(1, (int) Math.round((2.0 + burst.intensity * 5.0) * (0.35 + fade)));
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + rise, p.z, count, spread, spread, spread, 0.015);
            if (i < 2) {
                level.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, count, spread, spread, spread, 0.04);
            }
        }
        double plumeHeight = (7.0 + burst.intensity * 30.0) * progress;
        Vec3 plumeBase = center.add(dir.scale(burst.direction.getAxis().isHorizontal() ? 1.2 : 0.0));
        for (int i = 0; i < 5; i++) {
            double y = level.random.nextDouble() * plumeHeight;
            double radius = 0.35 + y * 0.11;
            int count = Math.max(1, (int) Math.round((2.0 + burst.intensity * 4.0) * (0.3 + fade)));
            level.sendParticles(ParticleTypes.CLOUD, plumeBase.x, plumeBase.y + y, plumeBase.z, count, radius, 0.45, radius, 0.012);
        }
    }

    private void damageInJet(ServerLevel level, Burst burst) {
        Vec3 center = Vec3.atCenterOf(burst.pos);
        Vec3 dir = new Vec3(burst.direction.getStepX(), burst.direction.getStepY(), burst.direction.getStepZ());
        double length = 3.0 + burst.intensity * 11.0;
        AABB box = new AABB(burst.pos).inflate(length + 1.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            Vec3 relative = entity.position().subtract(center);
            double along = relative.dot(dir);
            if (along < 0.0 || along > length) {
                continue;
            }
            Vec3 lateral = relative.subtract(dir.scale(along));
            double allowed = 0.85 + along * 0.18;
            if (lateral.length() > allowed) {
                continue;
            }
            float amount = (float) ((burst.temperature >= 100.0 ? 2.0 : 0.75) + burst.intensity * (burst.temperature >= 100.0 ? 5.0 : 2.0));
            entity.hurt(burst.temperature >= 100.0 ? level.damageSources().hotFloor() : level.damageSources().generic(), amount);
            Vec3 velocity = entity.getDeltaMovement().add(dir.scale(0.25 + burst.intensity * 0.55)).add(0.0, 0.08 + burst.intensity * 0.12, 0.0);
            entity.setDeltaMovement(velocity);
            entity.hurtMarked = true;
        }
    }

    private void spawnLeakFx(ServerLevel level, Vessel vessel) {
        int emitted = 0;
        for (long packed : vessel.leakPositions) {
            if (emitted >= 4) {
                break;
            }
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos) || level.getBlockState(pos).getBlock() instanceof HullBlock) {
                continue;
            }
            Direction direction = Direction.from3DDataValue(vessel.leakDirections.getOrDefault(packed, Direction.UP.ordinal()));
            Vec3 center = Vec3.atCenterOf(pos).add(direction.getStepX() * 0.35, direction.getStepY() * 0.35, direction.getStepZ() * 0.35);
            boolean steam = vessel.pressure > 3.0 || vessel.temperature >= 100.0;
            int count = vessel.pressure > 15.0 ? 6 : 3;
            level.sendParticles(steam ? ParticleTypes.CLOUD : ParticleTypes.SPLASH, center.x, center.y, center.z, count, 0.22, 0.22, 0.22, steam ? 0.02 : 0.035);
            emitted++;
        }
    }

    private Vessel match(ReactorVesselScanner.Result result) {
        Vessel best = null;
        int bestScore = 0;
        for (Vessel vessel : this.vessels) {
            int score = 0;
            for (BlockPos pos : result.shell) {
                if (vessel.shellPositions.contains(pos.asLong())) {
                    score += 3;
                }
            }
            for (BlockPos pos : result.cavity) {
                if (vessel.cavityPositions.contains(pos.asLong())) {
                    score += 1;
                }
            }
            for (BlockPos pos : result.coreCells) {
                if (vessel.corePositions.contains(pos.asLong())) {
                    score += 2;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = vessel;
            }
        }
        return best;
    }

    private Vessel findByShell(BlockPos pos) {
        long packed = pos.asLong();
        for (Vessel vessel : this.vessels) {
            if (vessel.shellPositions.contains(packed)) {
                return vessel;
            }
        }
        return null;
    }

    private Vessel findByCore(Collection<BlockPos> coreBases) {
        for (Vessel vessel : this.vessels) {
            for (BlockPos base : coreBases) {
                if (base != null && vessel.coreBasePositions.contains(base.asLong())) {
                    return vessel;
                }
            }
        }
        return null;
    }

    private void updateGeometry(Vessel vessel, ReactorVesselScanner.Result result, long gameTime) {
        boolean wasSealed = vessel.sealed;
        vessel.anchor = result.anchor.asLong();
        vessel.shellPositions = pack(result.shell);
        vessel.cavityPositions = pack(result.cavity);
        vessel.corePositions = pack(result.coreCells);
        vessel.coreBasePositions = pack(result.coreBases);
        vessel.leakPositions = pack(result.leakPositions);
        vessel.leakDirections.clear();
        for (Map.Entry<BlockPos, Direction> entry : result.leakDirections.entrySet()) {
            vessel.leakDirections.put(entry.getKey().asLong(), entry.getValue().ordinal());
        }
        vessel.layerCapacity.clear();
        vessel.layerCapacity.putAll(result.layerCapacity);
        vessel.capacity = Math.max(0.0, result.capacity);
        vessel.retainedCapacity = Mth.clamp(result.retainedCapacity, 0.0, vessel.capacity);
        vessel.pressureLimit = result.pressureLimit;
        vessel.nozzlePerformance = Mth.clamp(result.nozzlePerformance, 0.0, 100.0);
        vessel.connectedNozzleCount = Math.max(0, result.connectedNozzleCount);
        vessel.sealed = result.sealed;
        vessel.roleComplete = result.roleComplete;
        vessel.coreTopY = result.coreTopY;
        vessel.lowestLeakY = result.lowestLeakY;
        vessel.minCoreClearance = result.minCoreClearance;
        vessel.headroom = result.headroom;
        vessel.lastScanTick = gameTime;
        if (vessel.capacity <= 0.0) {
            vessel.coolant = 0.0;
            vessel.compression = 0.0;
            vessel.pressure = 1.0;
        } else if (vessel.sealed) {
            if (vessel.coolant > vessel.capacity) {
                vessel.compression += vessel.coolant - vessel.capacity;
                vessel.coolant = vessel.capacity;
            }
            double gas = gasVolume(vessel);
            if (!wasSealed) {
                if (gas > 0.0) {
                    vessel.gasAmount = gas * Math.max(1.0, vessel.pressure);
                } else if (vessel.fullPressure <= 0.0) {
                    vessel.fullPressure = Math.max(1.0, vessel.pressure);
                }
            } else if (gas > 0.0 && vessel.gasAmount <= 0.0) {
                vessel.gasAmount = gas * Math.max(1.0, vessel.pressure);
            }
            recalculateSealedPressure(vessel);
        }
        setDirty();
    }

    private static Set<Long> pack(Collection<BlockPos> positions) {
        Set<Long> packed = new HashSet<>();
        for (BlockPos pos : positions) {
            packed.add(pos.asLong());
        }
        return packed;
    }

    private static double gasVolume(Vessel vessel) {
        return Math.max(0.0, vessel.capacity - vessel.coolant);
    }

    private static void recalculateSealedPressure(Vessel vessel) {
        double gas = gasVolume(vessel);
        if (gas > 0.000001) {
            if (vessel.gasAmount <= 0.0) {
                vessel.gasAmount = gas * Math.max(1.0, vessel.pressure);
            }
            vessel.pressure = clampPressure(vessel.gasAmount / gas + saturationPressureAtm(vessel.temperature));
        } else {
            if (vessel.fullPressure <= 0.0) {
                vessel.fullPressure = Math.max(1.0, vessel.pressure);
            }
            vessel.pressure = clampPressure(vessel.fullPressure + HYDRAULIC_STIFFNESS * vessel.compression / Math.max(1.0, vessel.capacity));
        }
    }

    private static double clampPressure(double pressure) {
        return Mth.clamp(pressure, 0.03, 250.0);
    }

    public static double saturationPressureAtm(double temperatureC) {
        double[] temperatures = new double[]{0.0, 20.0, 50.0, 100.0, 150.0, 200.0, 250.0, 275.5, 286.0, 300.0};
        double[] pressures = new double[]{0.006, 0.023, 0.122, 1.0, 4.7, 15.3, 39.2, 58.9, 70.0, 84.8};
        if (temperatureC <= temperatures[0]) {
            return pressures[0];
        }
        for (int i = 1; i < temperatures.length; i++) {
            if (temperatureC <= temperatures[i]) {
                double fraction = (temperatureC - temperatures[i - 1]) / (temperatures[i] - temperatures[i - 1]);
                return pressures[i - 1] + fraction * (pressures[i] - pressures[i - 1]);
            }
        }
        return pressures[pressures.length - 1];
    }

    public static final class Vessel {
        private UUID id = UUID.randomUUID();
        private long anchor;
        private Set<Long> shellPositions = new HashSet<>();
        private Set<Long> cavityPositions = new HashSet<>();
        private Set<Long> corePositions = new HashSet<>();
        private Set<Long> coreBasePositions = new HashSet<>();
        private Set<Long> leakPositions = new HashSet<>();
        private final Map<Long, Integer> leakDirections = new HashMap<>();
        private final TreeMap<Integer, Double> layerCapacity = new TreeMap<>();
        private boolean sealed;
        private boolean roleComplete;
        private boolean leaking;
        private double capacity;
        private double retainedCapacity;
        private double coolant;
        private double compression;
        private double gasAmount;
        private double fullPressure = 1.0;
        private double pressure = 1.0;
        private double temperature = VverThermal.AMBIENT_C;
        private double integrity = 100.0;
        private double pressureLimit = 70.0;
        private double leakRate;
        private double nozzlePerformance;
        private int connectedNozzleCount;
        private int coreTopY = Integer.MIN_VALUE;
        private int lowestLeakY = Integer.MAX_VALUE;
        private int minCoreClearance = -1;
        private int headroom;
        private long lastScanTick = -10000L;

        public UUID id() {
            return this.id;
        }

        public boolean sealed() {
            return this.sealed;
        }

        public boolean leaking() {
            return this.leaking;
        }

        public boolean roleComplete() {
            return this.roleComplete;
        }

        public double capacity() {
            return this.capacity;
        }

        public double coolant() {
            return this.coolant;
        }

        public double pressure() {
            return this.pressure;
        }

        public double temperature() {
            return this.temperature;
        }

        public double integrity() {
            return this.integrity;
        }

        public double pressureLimit() {
            return this.pressureLimit;
        }

        public double leakRate() {
            return this.leakRate;
        }

        public double nozzlePerformance() {
            return this.nozzlePerformance;
        }

        public int connectedNozzleCount() {
            return this.connectedNozzleCount;
        }

        public int minCoreClearance() {
            return this.minCoreClearance;
        }

        public int headroom() {
            return this.headroom;
        }

        public double gasPercent() {
            return this.capacity <= 0.0 ? 0.0 : Math.max(0.0, this.capacity - this.coolant) / this.capacity * 100.0;
        }

        public double coolantPercent() {
            return this.capacity <= 0.0 ? 0.0 : this.coolant / this.capacity * 100.0;
        }

        public boolean coreCovered() {
            return this.coreTopY == Integer.MIN_VALUE || liquidLevelY() >= this.coreTopY + 1.0 - 0.000001;
        }

        public double liquidLevelY() {
            if (this.layerCapacity.isEmpty()) {
                return BlockPos.of(this.anchor).getY();
            }
            double remaining = this.coolant;
            int lastY = this.layerCapacity.firstKey();
            for (Map.Entry<Integer, Double> entry : this.layerCapacity.entrySet()) {
                lastY = entry.getKey();
                double layer = Math.max(0.0, entry.getValue());
                if (remaining <= 0.0) {
                    return entry.getKey();
                }
                if (remaining < layer) {
                    return entry.getKey() + remaining / Math.max(0.000001, layer);
                }
                remaining -= layer;
            }
            return lastY + 1.0;
        }

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", this.id);
            tag.putLong("Anchor", this.anchor);
            tag.putLongArray("Shell", toLongArray(this.shellPositions));
            tag.putLongArray("Cavity", toLongArray(this.cavityPositions));
            tag.putLongArray("Core", toLongArray(this.corePositions));
            tag.putLongArray("CoreBases", toLongArray(this.coreBasePositions));
            List<Long> leaks = new ArrayList<>(this.leakPositions);
            long[] leakArray = new long[leaks.size()];
            int[] directionArray = new int[leaks.size()];
            for (int i = 0; i < leaks.size(); i++) {
                leakArray[i] = leaks.get(i);
                directionArray[i] = this.leakDirections.getOrDefault(leaks.get(i), Direction.UP.ordinal());
            }
            tag.putLongArray("Leaks", leakArray);
            tag.putIntArray("LeakDirections", directionArray);
            tag.putBoolean("Sealed", this.sealed);
            tag.putBoolean("RoleComplete", this.roleComplete);
            tag.putDouble("Capacity", this.capacity);
            tag.putDouble("Retained", this.retainedCapacity);
            tag.putDouble("Coolant", this.coolant);
            tag.putDouble("Compression", this.compression);
            tag.putDouble("GasAmount", this.gasAmount);
            tag.putDouble("FullPressure", this.fullPressure);
            tag.putDouble("Pressure", this.pressure);
            tag.putDouble("Temperature", this.temperature);
            tag.putDouble("Integrity", this.integrity);
            tag.putDouble("PressureLimit", this.pressureLimit);
            tag.putDouble("NozzlePerformance", this.nozzlePerformance);
            tag.putInt("ConnectedNozzles", this.connectedNozzleCount);
            tag.putInt("CoreTopY", this.coreTopY);
            tag.putInt("LowestLeakY", this.lowestLeakY);
            tag.putInt("MinClearance", this.minCoreClearance);
            tag.putInt("Headroom", this.headroom);
            tag.putLong("LastScan", this.lastScanTick);
            return tag;
        }

        private static Vessel fromTag(CompoundTag tag) {
            Vessel vessel = new Vessel();
            if (tag.hasUUID("Id")) {
                vessel.id = tag.getUUID("Id");
            }
            vessel.anchor = tag.getLong("Anchor");
            vessel.shellPositions = fromLongArray(tag.getLongArray("Shell"));
            vessel.cavityPositions = fromLongArray(tag.getLongArray("Cavity"));
            vessel.corePositions = fromLongArray(tag.getLongArray("Core"));
            vessel.coreBasePositions = fromLongArray(tag.getLongArray("CoreBases"));
            long[] leaks = tag.getLongArray("Leaks");
            int[] directions = tag.getIntArray("LeakDirections");
            for (int i = 0; i < leaks.length; i++) {
                vessel.leakPositions.add(leaks[i]);
                vessel.leakDirections.put(leaks[i], i < directions.length ? directions[i] : Direction.UP.ordinal());
            }
            vessel.sealed = tag.getBoolean("Sealed");
            vessel.roleComplete = tag.getBoolean("RoleComplete");
            vessel.capacity = Math.max(0.0, tag.getDouble("Capacity"));
            vessel.retainedCapacity = Math.max(0.0, tag.getDouble("Retained"));
            vessel.coolant = Math.max(0.0, tag.getDouble("Coolant"));
            vessel.compression = Math.max(0.0, tag.getDouble("Compression"));
            vessel.gasAmount = Math.max(0.0, tag.getDouble("GasAmount"));
            vessel.fullPressure = Math.max(1.0, tag.getDouble("FullPressure"));
            vessel.pressure = clampPressure(tag.getDouble("Pressure"));
            vessel.temperature = tag.contains("Temperature") ? tag.getDouble("Temperature") : VverThermal.AMBIENT_C;
            vessel.integrity = Mth.clamp(tag.getDouble("Integrity"), 0.0, 100.0);
            vessel.pressureLimit = tag.contains("PressureLimit") ? tag.getDouble("PressureLimit") : 70.0;
            vessel.nozzlePerformance = Mth.clamp(tag.getDouble("NozzlePerformance"), 0.0, 100.0);
            vessel.connectedNozzleCount = Math.max(0, tag.getInt("ConnectedNozzles"));
            vessel.coreTopY = tag.contains("CoreTopY") ? tag.getInt("CoreTopY") : Integer.MIN_VALUE;
            vessel.lowestLeakY = tag.contains("LowestLeakY") ? tag.getInt("LowestLeakY") : Integer.MAX_VALUE;
            vessel.minCoreClearance = tag.contains("MinClearance") ? tag.getInt("MinClearance") : -1;
            vessel.headroom = Math.max(0, tag.getInt("Headroom"));
            vessel.lastScanTick = tag.getLong("LastScan");
            for (long packed : vessel.cavityPositions) {
                vessel.layerCapacity.merge(BlockPos.of(packed).getY(), 1.0, Double::sum);
            }
            for (long packed : vessel.corePositions) {
                vessel.layerCapacity.merge(BlockPos.of(packed).getY(), 0.35, Double::sum);
            }
            return vessel;
        }

        private static long[] toLongArray(Set<Long> values) {
            long[] array = new long[values.size()];
            int index = 0;
            for (Long value : values) {
                array[index++] = value;
            }
            return array;
        }

        private static Set<Long> fromLongArray(long[] values) {
            Set<Long> set = new HashSet<>();
            for (long value : values) {
                set.add(value);
            }
            return set;
        }
    }

    private static final class Burst {
        private final UUID vesselId;
        private final BlockPos pos;
        private final Direction direction;
        private final double intensity;
        private final int duration;
        private final double temperature;
        private int age;

        private Burst(UUID vesselId, BlockPos pos, Direction direction, double intensity, int duration, double temperature) {
            this.vesselId = vesselId;
            this.pos = pos;
            this.direction = direction;
            this.intensity = intensity;
            this.duration = duration;
            this.temperature = temperature;
        }
    }
}
