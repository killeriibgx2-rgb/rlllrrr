package com.zzynes.flydrone.WATER;

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
        return new FiniteWaterData(memory);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("FiniteCells", this.memory.finiteCells.toLongArray());
        return tag;
    }

    Memory memory() {
        return this.memory;
    }

    static final class Memory {
        final LongOpenHashSet finiteCells = new LongOpenHashSet();
        final Long2LongOpenHashMap largeCells = new Long2LongOpenHashMap();

        Memory() {
            this.largeCells.defaultReturnValue(Long.MIN_VALUE);
        }
    }
}
