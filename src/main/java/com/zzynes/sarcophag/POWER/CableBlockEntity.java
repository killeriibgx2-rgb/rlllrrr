package com.zzynes.sarcophag.POWER;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

// В одной клетке лежит сколько угодно жил: раскладка, цвет и толщина у каждой свои.
public class CableBlockEntity extends BlockEntity {

    private final List<CableStrand> strands = new ArrayList<>();
    private VoxelShape shape;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.CABLE_BE.get(), pos, state);
    }

    public List<CableStrand> strands() {
        return strands;
    }

    // Жила на той же линии сливается с существующей, иначе добавляется новая.
    public void addStrand(CableStrand strand) {
        for (CableStrand existing : strands) {
            if (existing.sameLane(strand)) {
                existing.conns |= strand.conns;
                dropShape();
                return;
            }
        }
        strands.add(strand);
        dropShape();
    }

    public boolean isEmpty() {
        return strands.isEmpty();
    }

    public VoxelShape shape() {
        if (shape == null) {
            if (strands.isEmpty()) {
                shape = Shapes.empty();
            } else {
                VoxelShape combined = Shapes.empty();
                for (CableStrand strand : strands) {
                    for (net.minecraft.world.phys.AABB box : strand.boxes()) {
                        combined = Shapes.or(combined, Shapes.create(box));
                    }
                }
                shape = combined.optimize();
            }
        }
        return shape;
    }

    private void dropShape() {
        shape = null;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (CableStrand strand : strands) {
            CompoundTag entry = new CompoundTag();
            entry.putFloat("X", strand.x);
            entry.putFloat("Y", strand.y);
            entry.putFloat("Z", strand.z);
            entry.putByte("Tier", (byte) strand.tier.ordinal());
            entry.putByte("Core", (byte) strand.core.ordinal());
            entry.putByte("Conns", (byte) strand.conns);
            list.add(entry);
        }
        tag.put("Strands", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        strands.clear();
        ListTag list = tag.getList("Strands", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            strands.add(new CableStrand(
                    entry.getFloat("X"),
                    entry.getFloat("Y"),
                    entry.getFloat("Z"),
                    CableTier.byIndex(entry.getByte("Tier")),
                    CableCore.byIndex(entry.getByte("Core")),
                    entry.getByte("Conns")));
        }
        shape = null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        load(packet.getTag());
    }
}
