package com.zzynes.sarcophag.POWER;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Одна жила внутри клетки: центр сечения в локальных координатах блока и битовая маска соединений.
public final class CableStrand {

    public final float x;
    public final float y;
    public final float z;
    public final CableTier tier;
    public final CableCore core;
    public int conns;

    public CableStrand(float x, float y, float z, CableTier tier, CableCore core, int conns) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.tier = tier;
        this.core = core;
        this.conns = conns;
    }

    public boolean connects(Direction direction) {
        return (conns & (1 << direction.get3DDataValue())) != 0;
    }

    public void connect(Direction direction) {
        conns |= 1 << direction.get3DDataValue();
    }

    public Vec3 centre() {
        return new Vec3(x, y, z);
    }

    public boolean sameLane(CableStrand other) {
        return tier == other.tier && core == other.core
                && Math.abs(x - other.x) < 0.004f
                && Math.abs(y - other.y) < 0.004f
                && Math.abs(z - other.z) < 0.004f;
    }

    // Кубик в точке излома плюс полоски до граней. Из этих же коробок складывается хитбокс.
    public List<AABB> boxes() {
        List<AABB> out = new ArrayList<>(7);
        double half = tier.radius;
        out.add(new AABB(x - half, y - half, z - half, x + half, y + half, z + half));
        for (Direction direction : Direction.values()) {
            if (!connects(direction)) {
                continue;
            }
            double ex = direction.getStepX() > 0 ? 1.0 : direction.getStepX() < 0 ? 0.0 : x;
            double ey = direction.getStepY() > 0 ? 1.0 : direction.getStepY() < 0 ? 0.0 : y;
            double ez = direction.getStepZ() > 0 ? 1.0 : direction.getStepZ() < 0 ? 0.0 : z;
            double minX = Math.min(x, ex) - (direction.getAxis() == Direction.Axis.X ? 0.0 : half);
            double maxX = Math.max(x, ex) + (direction.getAxis() == Direction.Axis.X ? 0.0 : half);
            double minY = Math.min(y, ey) - (direction.getAxis() == Direction.Axis.Y ? 0.0 : half);
            double maxY = Math.max(y, ey) + (direction.getAxis() == Direction.Axis.Y ? 0.0 : half);
            double minZ = Math.min(z, ez) - (direction.getAxis() == Direction.Axis.Z ? 0.0 : half);
            double maxZ = Math.max(z, ez) + (direction.getAxis() == Direction.Axis.Z ? 0.0 : half);
            out.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return out;
    }
}
