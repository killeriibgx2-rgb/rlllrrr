package com.zzynes.sarcophag.POWER;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// Попадание по кабелю считаем сами: хитбокс сущности это весь прогон,
// а нужен точный контакт с тонкой жилой.
public final class CablePick {

    public record Hit(CableRunEntity run, Vec3 point, double distance) {
    }

    private CablePick() {
    }

    public static Hit pick(Player player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0f);
        AABB area = player.getBoundingBox().inflate(reach + 1.0);
        List<CableRunEntity> runs = player.level().getEntitiesOfClass(CableRunEntity.class, area);
        Hit best = null;
        for (CableRunEntity run : runs) {
            CableRoute route = run.route();
            if (route.isEmpty()) {
                continue;
            }
            double radius = run.tier().radius + 0.05;
            for (int i = 0; i + 1 < route.size(); i++) {
                Vec3 a = route.pointAt(i);
                Vec3 b = route.pointAt(i + 1);
                double[] result = closest(eye, dir, a, b, reach);
                if (result == null) {
                    continue;
                }
                if (result[1] > radius * radius) {
                    continue;
                }
                if (best == null || result[0] < best.distance()) {
                    best = new Hit(run, a.add(b.subtract(a).scale(result[2])), result[0]);
                }
            }
        }
        return best;
    }

    // Возвращает {длина по лучу, квадрат расстояния, параметр на отрезке} или null.
    private static double[] closest(Vec3 eye, Vec3 dir, Vec3 a, Vec3 b, double reach) {
        Vec3 v = b.subtract(a);
        Vec3 w0 = eye.subtract(a);
        double aa = dir.dot(dir);
        double bb = dir.dot(v);
        double cc = v.dot(v);
        double dd = dir.dot(w0);
        double ee = v.dot(w0);
        double denom = aa * cc - bb * bb;
        double sc;
        double tc;
        if (Math.abs(denom) < 1.0e-8) {
            sc = 0.0;
            tc = cc < 1.0e-8 ? 0.0 : ee / cc;
        } else {
            sc = (bb * ee - cc * dd) / denom;
            tc = (aa * ee - bb * dd) / denom;
        }
        sc = Math.max(0.0, Math.min(reach, sc));
        tc = Math.max(0.0, Math.min(1.0, tc));
        Vec3 onRay = eye.add(dir.scale(sc));
        Vec3 onSegment = a.add(v.scale(tc));
        return new double[]{sc, onRay.distanceToSqr(onSegment), tc};
    }
}
