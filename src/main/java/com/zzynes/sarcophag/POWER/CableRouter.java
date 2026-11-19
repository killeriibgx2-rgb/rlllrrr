package com.zzynes.sarcophag.POWER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Кабель идёт по плоскости одной грани, шаг за шагом по клеткам блока.
// Свободного полёта нет: каждая клетка должна опираться на твёрдую грань.
public final class CableRouter {

    public static final int MAX_CELLS = 48;
    private static final double BEND_PENALTY = 0.2;

    private CableRouter() {
    }

    public static Optional<CableRoute> route(Level level, Anchor from, Anchor to) {
        Direction face = from.face();
        if (face != to.face()) {
            return Optional.empty();
        }
        Direction.Axis planeAxis = face.getAxis();
        Vec3 first = from.point();
        Vec3 second = to.point();
        double plane = coord(first, planeAxis);
        if (Math.abs(plane - coord(second, planeAxis)) > 1.0e-6) {
            return Optional.empty();
        }
        Direction.Axis ta = Anchor.tangentA(planeAxis);
        Direction.Axis tb = Anchor.tangentB(planeAxis);
        int a0 = cellOf(coord(first, ta));
        int a1 = cellOf(coord(first, tb));
        int b0 = cellOf(coord(second, ta));
        int b1 = cellOf(coord(second, tb));
        if (Math.abs(a0 - b0) + Math.abs(a1 - b1) > MAX_CELLS) {
            return Optional.empty();
        }
        List<int[][]> candidates = candidates(a0, a1, b0, b1);
        candidates.sort(Comparator.comparingDouble(CableRouter::cost));
        for (int[][] path : candidates) {
            if (!clear(level, path, face, planeAxis, ta, tb, plane)) {
                continue;
            }
            CableRoute route = toRoute(path, planeAxis, ta, tb, plane);
            if (!route.isEmpty()) {
                return Optional.of(route);
            }
        }
        return Optional.empty();
    }

    private static List<int[][]> candidates(int a0, int a1, int b0, int b1) {
        List<int[][]> out = new ArrayList<>(14);
        for (int offset : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            out.add(new int[][]{{a0, a1}, {a0 + offset, a1}, {a0 + offset, b1}, {b0, b1}});
            out.add(new int[][]{{a0, a1}, {a0, a1 + offset}, {b0, a1 + offset}, {b0, b1}});
        }
        return out;
    }

    private static double cost(int[][] path) {
        int steps = 0;
        for (int i = 0; i + 1 < path.length; i++) {
            steps += Math.abs(path[i + 1][0] - path[i][0]) + Math.abs(path[i + 1][1] - path[i][1]);
        }
        return steps + (path.length - 2) * BEND_PENALTY;
    }

    private static boolean clear(Level level, int[][] path, Direction face, Direction.Axis planeAxis, Direction.Axis ta, Direction.Axis tb, double plane) {
        for (int i = 0; i + 1 < path.length; i++) {
            int[] from = path[i];
            int[] to = path[i + 1];
            int steps = Math.abs(to[0] - from[0]) + Math.abs(to[1] - from[1]);
            int stepA = Integer.signum(to[0] - from[0]);
            int stepB = Integer.signum(to[1] - from[1]);
            for (int s = 0; s <= steps; s++) {
                int cellA = from[0] + stepA * s;
                int cellB = from[1] + stepB * s;
                Vec3 point = pointOf(cellA, cellB, planeAxis, ta, tb, plane);
                BlockPos air = BlockPos.containing(point.x, point.y, point.z);
                if (!level.getBlockState(air).getCollisionShape(level, air).isEmpty()) {
                    return false;
                }
                BlockPos support = air.relative(face.getOpposite());
                if (!level.getBlockState(support).isFaceSturdy(level, support, face)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static CableRoute toRoute(int[][] path, Direction.Axis planeAxis, Direction.Axis ta, Direction.Axis tb, double plane) {
        List<Vec3> points = new ArrayList<>();
        Vec3 previousDirection = null;
        Vec3 previousPoint = null;
        for (int i = 0; i + 1 < path.length; i++) {
            int[] from = path[i];
            int[] to = path[i + 1];
            int steps = Math.abs(to[0] - from[0]) + Math.abs(to[1] - from[1]);
            int stepA = Integer.signum(to[0] - from[0]);
            int stepB = Integer.signum(to[1] - from[1]);
            for (int s = 0; s <= steps; s++) {
                Vec3 point = pointOf(from[0] + stepA * s, from[1] + stepB * s, planeAxis, ta, tb, plane);
                if (previousPoint == null) {
                    points.add(point);
                } else {
                    Vec3 direction = point.subtract(previousPoint);
                    if (direction.lengthSqr() > 1.0e-8) {
                        direction = direction.normalize();
                        if (previousDirection != null && !previousDirection.equals(direction)) {
                            points.add(previousPoint);
                        }
                        previousDirection = direction;
                    }
                }
                previousPoint = point;
            }
        }
        if (previousPoint != null) {
            points.add(previousPoint);
        }
        return CableRoute.of(points);
    }

    public static Vec3 pointOf(int cellA, int cellB, Direction.Axis planeAxis, Direction.Axis ta, Direction.Axis tb, double plane) {
        double a = cellA + 0.5;
        double b = cellB + 0.5;
        double x = 0;
        double y = 0;
        double z = 0;
        if (planeAxis == Direction.Axis.X) {
            x = plane;
        } else if (planeAxis == Direction.Axis.Y) {
            y = plane;
        } else {
            z = plane;
        }
        if (ta == Direction.Axis.X) {
            x = a;
        } else if (ta == Direction.Axis.Y) {
            y = a;
        } else {
            z = a;
        }
        if (tb == Direction.Axis.X) {
            x = b;
        } else if (tb == Direction.Axis.Y) {
            y = b;
        } else {
            z = b;
        }
        return new Vec3(x, y, z);
    }

    private static int cellOf(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private static double coord(Vec3 point, Direction.Axis axis) {
        return switch (axis) {
            case X -> point.x;
            case Y -> point.y;
            default -> point.z;
        };
    }
}
