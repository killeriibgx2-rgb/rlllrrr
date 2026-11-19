package com.zzynes.sarcophag.POWER;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Ломаная трассы. Точки идут по порядку, соседние всегда различаются одной осью.
public final class CableRoute {

    public static final CableRoute EMPTY = new CableRoute(List.of());
    private static final double EPS = 1.0e-4;

    private final List<Vec3> points;
    private final double length;

    private CableRoute(List<Vec3> points) {
        this.points = points;
        double total = 0.0;
        for (int i = 0; i + 1 < points.size(); i++) {
            total += points.get(i).distanceTo(points.get(i + 1));
        }
        this.length = total;
    }

    public static CableRoute of(List<Vec3> raw) {
        List<Vec3> clean = new ArrayList<>(raw.size());
        for (Vec3 point : raw) {
            if (clean.isEmpty() || clean.get(clean.size() - 1).distanceTo(point) > EPS) {
                clean.add(point);
            }
        }
        if (clean.size() < 2) {
            return EMPTY;
        }
        return new CableRoute(List.copyOf(clean));
    }

    public boolean isEmpty() {
        return points.size() < 2;
    }

    public int size() {
        return points.size();
    }

    public Vec3 pointAt(int index) {
        return points.get(index);
    }

    public List<Vec3> points() {
        return points;
    }

    public double length() {
        return length;
    }

    public int bends() {
        return Math.max(0, points.size() - 2);
    }

    public AABB bounds() {
        if (isEmpty()) {
            return new AABB(0, 0, 0, 0, 0, 0);
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Vec3 point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.35);
    }

    // Режет трассу в ближайшей к точке точке. Возвращает до двух частей.
    public List<CableRoute> cut(Vec3 point) {
        if (isEmpty()) {
            return List.of();
        }
        int bestSegment = 0;
        double bestDist = Double.MAX_VALUE;
        Vec3 bestPoint = pointAt(0);
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            Vec3 dir = b.subtract(a);
            double lenSqr = dir.lengthSqr();
            double t = lenSqr < EPS ? 0.0 : Math.max(0.0, Math.min(1.0, point.subtract(a).dot(dir) / lenSqr));
            Vec3 probe = a.add(dir.scale(t));
            double dist = probe.distanceToSqr(point);
            if (dist < bestDist) {
                bestDist = dist;
                bestSegment = i;
                bestPoint = probe;
            }
        }
        List<Vec3> first = new ArrayList<>(points.subList(0, bestSegment + 1));
        first.add(bestPoint);
        List<Vec3> second = new ArrayList<>();
        second.add(bestPoint);
        second.addAll(points.subList(bestSegment + 1, points.size()));
        List<CableRoute> parts = new ArrayList<>(2);
        CableRoute left = of(first);
        CableRoute right = of(second);
        if (!left.isEmpty()) {
            parts.add(left);
        }
        if (!right.isEmpty()) {
            parts.add(right);
        }
        return parts;
    }

    public CompoundTag save() {
        ListTag list = new ListTag();
        for (Vec3 point : points) {
            list.add(DoubleTag.valueOf(point.x));
            list.add(DoubleTag.valueOf(point.y));
            list.add(DoubleTag.valueOf(point.z));
        }
        CompoundTag tag = new CompoundTag();
        tag.put("P", list);
        return tag;
    }

    public static CableRoute load(CompoundTag tag) {
        ListTag list = tag.getList("P", Tag.TAG_DOUBLE);
        List<Vec3> raw = new ArrayList<>(list.size() / 3);
        for (int i = 0; i + 2 < list.size(); i += 3) {
            raw.add(new Vec3(list.getDouble(i), list.getDouble(i + 1), list.getDouble(i + 2)));
        }
        return of(raw);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(points.size());
        for (Vec3 point : points) {
            buf.writeDouble(point.x);
            buf.writeDouble(point.y);
            buf.writeDouble(point.z);
        }
    }

    public static CableRoute read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Vec3> raw = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            raw.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        return of(raw);
    }
}
