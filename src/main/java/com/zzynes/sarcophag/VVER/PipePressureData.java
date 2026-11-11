package com.zzynes.sarcophag.VVER;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Iterator;

public class PipePressureData extends SavedData {
    private static final String DATA_NAME = "sarcophag_pipe_pressure";
    private static final double ATMOSPHERIC_PRESSURE_ATM = 1.0;

    private final Long2DoubleOpenHashMap pipePressures = new Long2DoubleOpenHashMap();
    private final Long2DoubleOpenHashMap endpointPressures = new Long2DoubleOpenHashMap();

    public PipePressureData() {
        this.pipePressures.defaultReturnValue(ATMOSPHERIC_PRESSURE_ATM);
        this.endpointPressures.defaultReturnValue(ATMOSPHERIC_PRESSURE_ATM);
    }

    public static PipePressureData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PipePressureData::load, PipePressureData::new, DATA_NAME);
    }

    public static PipePressureData load(CompoundTag tag) {
        PipePressureData data = new PipePressureData();
        loadMap(tag.getList("PipePressures", Tag.TAG_COMPOUND), data.pipePressures);
        loadMap(tag.getList("EndpointPressures", Tag.TAG_COMPOUND), data.endpointPressures);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("PipePressures", saveMap(this.pipePressures));
        tag.put("EndpointPressures", saveMap(this.endpointPressures));
        return tag;
    }

    public double getPipePressureAtm(BlockPos pos) {
        return this.pipePressures.get(pos.asLong());
    }

    public void setPipePressureAtm(BlockPos pos, double pressureAtm) {
        if (setPressure(this.pipePressures, pos.asLong(), pressureAtm)) {
            setDirty();
        }
    }

    public void removePipePressure(BlockPos pos) {
        if (this.pipePressures.remove(pos.asLong()) != ATMOSPHERIC_PRESSURE_ATM || this.pipePressures.containsKey(pos.asLong())) {
            setDirty();
        }
    }

    public double getEndpointPressureAtm(BlockPos pos) {
        return this.endpointPressures.get(pos.asLong());
    }

    public void setEndpointPressureAtm(BlockPos pos, double pressureAtm) {
        if (setPressure(this.endpointPressures, pos.asLong(), pressureAtm)) {
            setDirty();
        }
    }

    public void removeEndpointPressure(BlockPos pos) {
        boolean contained = this.endpointPressures.containsKey(pos.asLong());
        this.endpointPressures.remove(pos.asLong());
        if (contained) {
            setDirty();
        }
    }

    public void cleanup(ServerLevel level) {
        boolean changed = false;
        Iterator<Long2DoubleMap.Entry> pipeIterator = this.pipePressures.long2DoubleEntrySet().iterator();
        while (pipeIterator.hasNext()) {
            BlockPos pos = BlockPos.of(pipeIterator.next().getLongKey());
            if (level.isLoaded(pos) && !PipePorts.isPipe(level.getBlockState(pos))) {
                pipeIterator.remove();
                changed = true;
            }
        }
        Iterator<Long2DoubleMap.Entry> endpointIterator = this.endpointPressures.long2DoubleEntrySet().iterator();
        while (endpointIterator.hasNext()) {
            BlockPos pos = BlockPos.of(endpointIterator.next().getLongKey());
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof GcnBlock)) {
                endpointIterator.remove();
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private static boolean setPressure(Long2DoubleOpenHashMap map, long key, double pressureAtm) {
        double clamped = Mth.clamp(pressureAtm, 0.0, SteamGeneratorBlockEntity.MAX_PRESSURE_ATM);
        if (Math.abs(clamped - ATMOSPHERIC_PRESSURE_ATM) < 0.000001) {
            boolean contained = map.containsKey(key);
            map.remove(key);
            return contained;
        }
        double previous = map.put(key, clamped);
        return Math.abs(previous - clamped) >= 0.000001;
    }

    private static void loadMap(ListTag list, Long2DoubleOpenHashMap map) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            double pressure = Mth.clamp(entry.getDouble("Pressure"), 0.0, SteamGeneratorBlockEntity.MAX_PRESSURE_ATM);
            if (Math.abs(pressure - ATMOSPHERIC_PRESSURE_ATM) >= 0.000001) {
                map.put(entry.getLong("Pos"), pressure);
            }
        }
    }

    private static ListTag saveMap(Long2DoubleOpenHashMap map) {
        ListTag list = new ListTag();
        for (Long2DoubleMap.Entry entry : map.long2DoubleEntrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", entry.getLongKey());
            tag.putDouble("Pressure", entry.getDoubleValue());
            list.add(tag);
        }
        return list;
    }
}
