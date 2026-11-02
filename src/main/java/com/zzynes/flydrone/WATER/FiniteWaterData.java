package com.zzynes.flydrone.WATER;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class FiniteWaterData extends SavedData {
    private static final String DATA_NAME = "fly_drone_finite_water";

    private final Memory memory;

    public FiniteWaterData() {
        this(new Memory());
    }

    private FiniteWaterData(Memory memory) {
        this.memory = memory;
    }

    public static FiniteWaterData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FiniteWaterData::load, FiniteWaterData::new, DATA_NAME);
    }

    public static FiniteWaterData load(CompoundTag tag) {
        Memory memory = new Memory();
        for (long cell : tag.getLongArray("FiniteCells")) {
            memory.finiteCells.add(cell);
        }
        long[] volumeCells = tag.getLongArray("VolumeCells");
        int[] volumes = tag.getIntArray("Volumes");
        int count = Math.min(volumeCells.length, volumes.length);
        boolean millibuckets = tag.getInt("VolumeVersion") >= 2;
        for (int i = 0; i < count; i++) {
            if (volumes[i] > 0) {
                int volume = millibuckets || volumes[i] > FiniteWaterManager.BUCKET_UNITS
                        ? volumes[i]
                        : volumes[i] * FiniteWaterManager.MILLIBUCKETS_PER_UNIT;
                memory.volumes.put(volumeCells[i], volume);
            }
        }
        return new FiniteWaterData(memory);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("FiniteCells", this.memory.finiteCells.toLongArray());
        long[] volumeCells = new long[this.memory.volumes.size()];
        int[] volumes = new int[this.memory.volumes.size()];
        int index = 0;
        for (var entry : this.memory.volumes.long2IntEntrySet()) {
            volumeCells[index] = entry.getLongKey();
            volumes[index] = entry.getIntValue();
            index++;
        }
        tag.putLongArray("VolumeCells", volumeCells);
        tag.putIntArray("Volumes", volumes);
        tag.putInt("VolumeVersion", 2);
        return tag;
    }

    Memory memory() {
        return this.memory;
    }

    static final class Memory {
        final LongOpenHashSet finiteCells = new LongOpenHashSet();
        final Long2LongOpenHashMap largeCells = new Long2LongOpenHashMap();
        final Long2IntOpenHashMap volumes = new Long2IntOpenHashMap();
        final Long2LongOpenHashMap equalizedAt = new Long2LongOpenHashMap();
        final Long2LongOpenHashMap flowInAt = new Long2LongOpenHashMap();
        final Long2LongOpenHashMap flowOutAt = new Long2LongOpenHashMap();
        final Long2LongOpenHashMap equalizationCooldown = new Long2LongOpenHashMap();
        long equalizationTaskTick = Long.MIN_VALUE;
        int equalizationTasksThisTick;

        Memory() {
            this.largeCells.defaultReturnValue(Long.MIN_VALUE);
            this.volumes.defaultReturnValue(0);
            this.equalizedAt.defaultReturnValue(Long.MIN_VALUE);
            this.flowInAt.defaultReturnValue(Long.MIN_VALUE);
            this.flowOutAt.defaultReturnValue(Long.MIN_VALUE);
            this.equalizationCooldown.defaultReturnValue(Long.MIN_VALUE);
        }
    }
}
