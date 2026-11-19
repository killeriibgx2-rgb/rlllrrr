package com.zzynes.sarcophag.POWER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Раскладка готовой трассы по клеткам. Отрезки берутся как есть: клетка, вход и выход
// считаются по координатам, поэтому каждая клетка получает жилу и стыки не рвутся.
public final class CableLayout {

    private static final double EPS = 1.0e-4;

    private CableLayout() {
    }

    // План: одни и те же клетки считает и призрак на клиенте, и установка на сервере.
    public static List<Placement> plan(CableRoute route, CableTier tier, CableCore core) {
        List<Placement> out = new ArrayList<>();
        List<Vec3> points = route.points();
        if (points.size() < 2) {
            return out;
        }

        Map<Key, CableStrand> strands = new LinkedHashMap<>();
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            if (a.distanceToSqr(b) < 1.0e-8) {
                continue;
            }
            addSegment(strands, a, b, tier, core);
        }
        for (Map.Entry<Key, CableStrand> entry : strands.entrySet()) {
            out.add(new Placement(entry.getKey().pos, entry.getValue()));
        }
        return out;
    }

    public static boolean valid(Level level, List<Placement> plan) {
        if (plan.isEmpty()) {
            return false;
        }
        for (Placement placement : plan) {
            if (!free(level, placement)) {
                return false;
            }
        }
        return true;
    }

    public static boolean place(Level level, CableRoute route, CableTier tier, CableCore core) {
        List<Placement> placements = plan(route, tier, core);
        if (!valid(level, placements)) {
            return false;
        }
        for (Placement placement : placements) {
            commit(level, placement);
        }
        link(level, placements);
        return true;
    }

    private static void addSegment(Map<Key, CableStrand> strands, Vec3 a, Vec3 b, CableTier tier, CableCore core) {
        Direction.Axis axis = Math.abs(a.x - b.x) > EPS ? Direction.Axis.X
                : Math.abs(a.y - b.y) > EPS ? Direction.Axis.Y : Direction.Axis.Z;
        double from = coord(a, axis);
        double to = coord(b, axis);
        double lo = Math.min(from, to);
        double hi = Math.max(from, to);
        boolean forward = to > from;

        int first = (int) Math.floor(lo + EPS);
        int last = (int) Math.floor(hi - EPS);
        if (hi - lo < EPS) {
            first = last = (int) Math.floor(lo);
        }

        for (int cell = first; cell <= last; cell++) {
            double overlap = Math.min(hi, cell + 1.0) - Math.max(lo, cell);
            if (overlap < 1.0e-5) {
                continue;
            }
            boolean openStart = lo > cell + EPS;
            boolean openEnd = hi < cell + 1.0 - EPS;
            int conns;
            double along;
            if (openStart && openEnd) {
                continue;
            } else if (openStart) {
                along = lo - cell;
                conns = bit(forward ? positive(axis) : negative(axis));
            } else if (openEnd) {
                along = hi - cell;
                conns = bit(forward ? negative(axis) : positive(axis));
            } else {
                along = 0.5;
                conns = bit(positive(axis)) | bit(negative(axis));
            }

            Vec3 point = withCoord(a, axis, cell + along);
            BlockPos pos = BlockPos.containing(point);
            Vec3 local = clamp(point.subtract(Vec3.atLowerCornerOf(pos)));
            Key key = new Key(pos, quant(local.x), quant(local.y), quant(local.z), tier, core);
            CableStrand existing = strands.get(key);
            if (existing == null) {
                strands.put(key, new CableStrand((float) local.x, (float) local.y, (float) local.z, tier, core, conns));
            } else {
                existing.conns |= conns;
            }
        }
    }

    private static Direction positive(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }

    private static Direction negative(Direction.Axis axis) {
        return positive(axis).getOpposite();
    }

    private static int bit(Direction direction) {
        return 1 << direction.get3DDataValue();
    }

    private static double coord(Vec3 point, Direction.Axis axis) {
        return switch (axis) {
            case X -> point.x;
            case Y -> point.y;
            default -> point.z;
        };
    }

    private static Vec3 withCoord(Vec3 point, Direction.Axis axis, double value) {
        return switch (axis) {
            case X -> new Vec3(value, point.y, point.z);
            case Y -> new Vec3(point.x, value, point.z);
            default -> new Vec3(point.x, point.y, value);
        };
    }

    private static int quant(double value) {
        return (int) Math.round(value * 64.0);
    }

    private static Vec3 clamp(Vec3 point) {
        return new Vec3(
                Math.max(0.0, Math.min(1.0, point.x)),
                Math.max(0.0, Math.min(1.0, point.y)),
                Math.max(0.0, Math.min(1.0, point.z)));
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

    // Сшивка по факту: если у соседа жила на той же линии, стороны соединяются с обеих сторон.
    private static void link(Level level, List<Placement> placements) {
        Set<BlockPos> touched = new LinkedHashSet<>();
        for (Placement placement : placements) {
            for (Direction direction : Direction.values()) {
                BlockPos next = placement.pos.relative(direction);
                if (!(level.getBlockEntity(next) instanceof CableBlockEntity neighbour)) {
                    continue;
                }
                for (CableStrand strand : neighbour.strands()) {
                    if (strand.tier != placement.strand.tier || strand.core != placement.strand.core) {
                        continue;
                    }
                    if (aligned(placement.strand, strand, direction)) {
                        placement.strand.connect(direction);
                        strand.connect(direction.getOpposite());
                        touched.add(next);
                    }
                }
            }
            touched.add(placement.pos);
        }
        for (BlockPos pos : touched) {
            if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
                cable.refresh();
            }
        }
    }

    private static boolean aligned(CableStrand a, CableStrand b, Direction direction) {
        double limit = 1.0 / 64.0 + 1.0e-4;
        return switch (direction.getAxis()) {
            case X -> Math.abs(a.y - b.y) < limit && Math.abs(a.z - b.z) < limit;
            case Y -> Math.abs(a.x - b.x) < limit && Math.abs(a.z - b.z) < limit;
            case Z -> Math.abs(a.x - b.x) < limit && Math.abs(a.y - b.y) < limit;
        };
    }

    public record Placement(BlockPos pos, CableStrand strand) {
    }

    private record Key(BlockPos pos, int x, int y, int z, CableTier tier, CableCore core) {
    }
}
