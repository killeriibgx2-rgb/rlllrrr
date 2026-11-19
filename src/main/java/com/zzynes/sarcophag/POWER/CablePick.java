package com.zzynes.sarcophag.POWER;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

// Хитбокс кабеля это набор коробок по клеткам. Куда попал, там и режется.
public final class CablePick {

    public record Hit(CableRunEntity run, Vec3 point, double distance) {
    }

    private CablePick() {
    }

    public static Hit pick(Player player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(reach));
        AABB area = player.getBoundingBox().inflate(reach + 2.0);
        List<CableRunEntity> runs = player.level().getEntitiesOfClass(CableRunEntity.class, area);
        Hit best = null;
        for (CableRunEntity run : runs) {
            CableRoute route = run.route();
            if (route.isEmpty()) {
                continue;
            }
            Vec3 shift = run.renderShift();
            double half = run.tier().radius;
            for (int i = 0; i + 1 < route.size(); i++) {
                Vec3 a = route.pointAt(i).add(shift);
                Vec3 b = route.pointAt(i + 1).add(shift);
                int pieces = Math.max(1, (int) Math.round(a.distanceTo(b)));
                for (int p = 0; p < pieces; p++) {
                    Vec3 from = a.lerp(b, p / (double) pieces);
                    Vec3 to = a.lerp(b, (p + 1) / (double) pieces);
                    AABB box = new AABB(from, to).inflate(half);
                    Optional<Vec3> hit = box.clip(eye, end);
                    if (hit.isEmpty()) {
                        continue;
                    }
                    double distance = hit.get().distanceTo(eye);
                    if (best == null || distance < best.distance()) {
                        best = new Hit(run, hit.get(), distance);
                    }
                }
            }
        }
        return best;
    }
}
