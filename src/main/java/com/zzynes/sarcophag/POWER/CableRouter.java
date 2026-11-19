package com.zzynes.sarcophag.POWER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Трасса между двумя точками: перебор готовых форм (прямая, Г, Z, обход)
// вместо поиска пути. Все отрезки по осям, изломов не больше четырёх.
public final class CableRouter {

    public static final int MAX_LENGTH_BLOCKS = 48;
    private static final double STEP = 0.2;
    private static final double BEND_PENALTY = 0.15;

    private static final Direction.Axis[][] ORDERS = {
            {Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z},
            {Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y},
            {Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z},
            {Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X},
            {Direction.Axis.Z, Direction.Axis.X, Direction.Axis.Y},
            {Direction.Axis.Z, Direction.Axis.Y, Direction.Axis.X}
    };

    private CableRouter() {
    }

    public static Optional<CableRoute> route(Level level, Anchor from, Anchor to, CableTier tier) {
        // Ось кабеля держим на расстоянии половины толщины от поверхности,
        // иначе он наполовину утопает в блоке.
        double lift = tier.radius;
        Vec3 a = from.point().add(normal(from.face()).scale(lift));
        Vec3 b = to.point().add(normal(to.face()).scale(lift));
        if (a.distanceTo(b) > MAX_LENGTH_BLOCKS) {
            return Optional.empty();
        }
        List<CableRoute> candidates = candidates(a, b);
        candidates.sort(Comparator.comparingDouble(CableRouter::score));
        for (CableRoute candidate : candidates) {
            if (candidate.length() > MAX_LENGTH_BLOCKS || candidate.bends() > 4) {
                continue;
            }
            if (!clear(level, candidate)) {
                continue;
            }
            if (!hugging(level, candidate, tier)) {
                continue;
            }
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private static double score(CableRoute route) {
        return route.length() + route.bends() * BEND_PENALTY;
    }

    private static List<CableRoute> candidates(Vec3 a, Vec3 b) {
        List<CableRoute> out = new ArrayList<>(24);
        for (Direction.Axis[] order : ORDERS) {
            out.add(stairs(a, b, order));
            out.add(detour(a, b, order, 1.0));
            out.add(detour(a, b, order, -1.0));
        }
        out.removeIf(CableRoute::isEmpty);
        return out;
    }

    private static CableRoute stairs(Vec3 a, Vec3 b, Direction.Axis[] order) {
        List<Vec3> points = new ArrayList<>(4);
        points.add(a);
        Vec3 current = a;
        for (Direction.Axis axis : order) {
            double target = coord(b, axis);
            if (Math.abs(coord(current, axis) - target) < 1.0e-4) {
                continue;
            }
            current = withCoord(current, axis, target);
            points.add(current);
        }
        points.add(b);
        return CableRoute.of(points);
    }

    private static CableRoute detour(Vec3 a, Vec3 b, Direction.Axis[] order, double offset) {
        Direction.Axis axis = order[order.length - 1];
        Vec3 a2 = withCoord(a, axis, coord(a, axis) + offset);
        Vec3 b2 = withCoord(b, axis, coord(b, axis) + offset);
        List<Vec3> points = new ArrayList<>(6);
        points.add(a);
        points.add(a2);
        Vec3 current = a2;
        for (Direction.Axis step : order) {
            double target = coord(b2, step);
            if (Math.abs(coord(current, step) - target) < 1.0e-4) {
                continue;
            }
            current = withCoord(current, step, target);
            points.add(current);
        }
        points.add(b);
        return CableRoute.of(points);
    }

    private static boolean clear(Level level, CableRoute route) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int i = 0; i + 1 < route.size(); i++) {
            Vec3 a = route.pointAt(i);
            Vec3 b = route.pointAt(i + 1);
            int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / STEP));
            for (int s = 0; s <= steps; s++) {
                Vec3 point = a.lerp(b, s / (double) steps);
                probe.set((int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z));
                BlockState state = level.getBlockState(probe);
                if (state.is(PowerRegistry.CABLE.get())) {
                    // своя же клетка не помеха: жил в ней может быть сколько угодно,
                    // наложение проверит раскладка по толщине
                    continue;
                }
                if (!state.getCollisionShape(level, probe).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    // Кабель живёт только у поверхности: каждая точка трассы должна быть рядом с блоком.
    private static boolean hugging(Level level, CableRoute route, CableTier tier) {
        double reach = tier.radius + 0.12;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int i = 0; i + 1 < route.size(); i++) {
            Vec3 a = route.pointAt(i);
            Vec3 b = route.pointAt(i + 1);
            int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.25));
            for (int s = 0; s <= steps; s++) {
                Vec3 point = a.lerp(b, s / (double) steps);
                probe.set((int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z));
                boolean near = false;
                for (Direction direction : Direction.values()) {
                    BlockPos neighbour = probe.relative(direction);
                    if (!level.getBlockState(neighbour).getCollisionShape(level, neighbour).isEmpty()) {
                        near = true;
                        break;
                    }
                }
                if (!near) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Vec3 normal(Direction face) {
        return new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
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
}
