package com.zzynes.sarcophag.POWER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Раскладка готовой трассы по клеткам: в каждой клетке остаётся жила со своим сечением
// и маской сторон. Так кабель становится твёрдым блоком, а не набором неосязаемых коробок.
public final class CableLayout {

    private CableLayout() {
    }

    public static boolean place(Level level, CableRoute route, CableTier tier, CableCore core) {
        List<Vec3> points = route.points();
        if (points.size() < 2) {
            return false;
        }
        Vec3 first = points.get(0);
        Vec3 last = points.get(points.size() - 1);

        Map<BlockPos, Vec3> bends = new LinkedHashMap<>();
        for (int i = 1; i + 1 < points.size(); i++) {
            Vec3 vertex = points.get(i);
            bends.put(BlockPos.containing(vertex), vertex);
        }

        Map<BlockPos, Cell> cells = new LinkedHashMap<>();
        BlockPos prev = BlockPos.containing(first);
        cells.computeIfAbsent(prev, key -> new Cell());
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.05));
            for (int s = 1; s <= steps; s++) {
                Vec3 sample = a.lerp(b, s / (double) steps);
                BlockPos pos = BlockPos.containing(sample);
                if (!pos.equals(prev)) {
                    Direction face = faceBetween(prev, pos);
                    if (face != null) {
                        cells.computeIfAbsent(prev, key -> new Cell()).ports.add(face);
                        cells.computeIfAbsent(pos, key -> new Cell()).ports.add(face.getOpposite());
                    }
                    prev = pos;
                }
                cells.computeIfAbsent(pos, key -> new Cell()).points.add(sample);
            }
        }

        BlockPos startCell = BlockPos.containing(first);
        BlockPos endCell = BlockPos.containing(last);

        List<Placement> placements = new ArrayList<>(cells.size());
        for (Map.Entry<BlockPos, Cell> entry : cells.entrySet()) {
            BlockPos pos = entry.getKey();
            Cell cell = entry.getValue();
            if (cell.ports.isEmpty()) {
                continue;
            }
            Vec3 centre;
            Vec3 bend = bends.get(pos);
            if (bend != null) {
                centre = bend;
            } else if (pos.equals(startCell) && cell.ports.size() == 1) {
                centre = first;
            } else if (pos.equals(endCell) && cell.ports.size() == 1) {
                centre = last;
            } else {
                centre = straightCentre(pos, cell);
            }
            Vec3 local = snap(centre.subtract(Vec3.atLowerCornerOf(pos)));
            placements.add(new Placement(pos, new CableStrand(
                    (float) local.x, (float) local.y, (float) local.z, tier, core, cell.mask())));
        }

        for (Placement placement : placements) {
            if (!free(level, placement)) {
                return false;
            }
        }
        for (Placement placement : placements) {
            commit(level, placement);
        }
        return true;
    }

    private static Vec3 straightCentre(BlockPos pos, Cell cell) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Vec3 sample : cell.points) {
            Vec3 local = sample.subtract(Vec3.atLowerCornerOf(pos));
            x += local.x;
            y += local.y;
            z += local.z;
        }
        int count = cell.points.size();
        x /= count;
        y /= count;
        z /= count;
        Direction.Axis axis = spread(cell.points);
        if (axis == Direction.Axis.X) {
            x = 0.5;
        } else if (axis == Direction.Axis.Y) {
            y = 0.5;
        } else if (axis == Direction.Axis.Z) {
            z = 0.5;
        }
        return new Vec3(x, y, z);
    }

    // Ось, вдоль которой точки клетки разъехались сильнее всего.
    private static Direction.Axis spread(List<Vec3> points) {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Vec3 point : points) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
        }
        double dx = maxX - minX;
        double dy = maxY - minY;
        double dz = maxZ - minZ;
        if (dx >= dy && dx >= dz) {
            return Direction.Axis.X;
        }
        return dy >= dz ? Direction.Axis.Y : Direction.Axis.Z;
    }

    private static Vec3 snap(Vec3 point) {
        double step = 1.0 / 64.0;
        return new Vec3(
                Math.round(point.x / step) * step,
                Math.round(point.y / step) * step,
                Math.round(point.z / step) * step);
    }

    private static Direction faceBetween(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) == 1 && dy == 0 && dz == 0) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        if (Math.abs(dy) == 1 && dx == 0 && dz == 0) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }
        if (Math.abs(dz) == 1 && dx == 0 && dy == 0) {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return null;
    }

    private static boolean free(Level level, Placement placement) {
        BlockState state = level.getBlockState(placement.pos);
        boolean ours = state.is(PowerRegistry.CABLE.get());
        if (!ours && !state.canBeReplaced()) {
            return false;
        }
        if (!(level.getBlockEntity(placement.pos) instanceof CableBlockEntity cable)) {
            return true;
        }
        for (CableStrand existing : cable.strands()) {
            if (existing.sameLane(placement.strand)) {
                continue;
            }
            double limit = existing.tier.radius + placement.strand.tier.radius;
            if (existing.centre().distanceTo(placement.strand.centre()) < limit - 0.002) {
                return false;
            }
        }
        return true;
    }

    private static void commit(Level level, Placement placement) {
        if (!level.getBlockState(placement.pos).is(PowerRegistry.CABLE.get())) {
            level.setBlock(placement.pos, PowerRegistry.CABLE.get().defaultBlockState(), 3);
        }
        if (level.getBlockEntity(placement.pos) instanceof CableBlockEntity cable) {
            cable.addStrand(placement.strand);
        }
    }

    private record Placement(BlockPos pos, CableStrand strand) {
    }

    private static final class Cell {
        final List<Vec3> points = new ArrayList<>();
        final EnumSet<Direction> ports = EnumSet.noneOf(Direction.class);

        int mask() {
            int mask = 0;
            for (Direction direction : ports) {
                mask |= 1 << direction.get3DDataValue();
            }
            return mask;
        }
    }
}
