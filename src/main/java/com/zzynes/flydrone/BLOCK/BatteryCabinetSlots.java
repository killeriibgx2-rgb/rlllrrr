package com.zzynes.flydrone.BLOCK;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class BatteryCabinetSlots {
    public static final int SLOT_COUNT = 60;

    public record Slot(String boneName, float u0, float v0, float w0, float u1, float v1, float w1) {
    }

    public static final Slot[] SLOTS = {
            new Slot("bone31", 0.0750f, 0.1375f, 0.0625f, 0.2000f, 0.6250f, 0.8938f),
            new Slot("bone32", 0.2062f, 0.1375f, 0.0625f, 0.3312f, 0.6250f, 0.8938f),
            new Slot("bone33", 0.3375f, 0.1375f, 0.0625f, 0.4625f, 0.6250f, 0.8938f),
            new Slot("bone34", 0.4688f, 0.1375f, 0.0625f, 0.5938f, 0.6250f, 0.8938f),
            new Slot("bone35", 0.6000f, 0.1375f, 0.0625f, 0.7250f, 0.6250f, 0.8938f),
            new Slot("bone36", 0.7312f, 0.1375f, 0.0625f, 0.8562f, 0.6250f, 0.8938f),
            new Slot("bone37", 1.1375f, 0.1375f, 0.0625f, 1.2625f, 0.6250f, 0.8938f),
            new Slot("bone38", 1.2688f, 0.1375f, 0.0625f, 1.3938f, 0.6250f, 0.8938f),
            new Slot("bone39", 1.4000f, 0.1375f, 0.0625f, 1.5250f, 0.6250f, 0.8938f),
            new Slot("bone40", 1.5312f, 0.1375f, 0.0625f, 1.6562f, 0.6250f, 0.8938f),
            new Slot("bone41", 1.6625f, 0.1375f, 0.0625f, 1.7875f, 0.6250f, 0.8938f),
            new Slot("bone42", 1.7937f, 0.1375f, 0.0625f, 1.9187f, 0.6250f, 0.8938f),
            new Slot("bone19", 0.0750f, 0.7000f, 0.0625f, 0.2000f, 1.1875f, 0.8938f),
            new Slot("bone20", 0.2062f, 0.7000f, 0.0625f, 0.3312f, 1.1875f, 0.8938f),
            new Slot("bone21", 0.3375f, 0.7000f, 0.0625f, 0.4625f, 1.1875f, 0.8938f),
            new Slot("bone22", 0.4688f, 0.7000f, 0.0625f, 0.5938f, 1.1875f, 0.8938f),
            new Slot("bone23", 0.6000f, 0.7000f, 0.0625f, 0.7250f, 1.1875f, 0.8938f),
            new Slot("bone24", 0.7312f, 0.7000f, 0.0625f, 0.8562f, 1.1875f, 0.8938f),
            new Slot("bone25", 1.1375f, 0.7000f, 0.0625f, 1.2625f, 1.1875f, 0.8938f),
            new Slot("bone26", 1.2688f, 0.7000f, 0.0625f, 1.3938f, 1.1875f, 0.8938f),
            new Slot("bone27", 1.4000f, 0.7000f, 0.0625f, 1.5250f, 1.1875f, 0.8938f),
            new Slot("bone28", 1.5312f, 0.7000f, 0.0625f, 1.6562f, 1.1875f, 0.8938f),
            new Slot("bone29", 1.6625f, 0.7000f, 0.0625f, 1.7875f, 1.1875f, 0.8938f),
            new Slot("bone30", 1.7937f, 0.7000f, 0.0625f, 1.9187f, 1.1875f, 0.8938f),
            new Slot("bone7", 0.0750f, 1.2625f, 0.0625f, 0.2000f, 1.7500f, 0.8938f),
            new Slot("bone8", 0.2062f, 1.2625f, 0.0625f, 0.3312f, 1.7500f, 0.8938f),
            new Slot("bone9", 0.3375f, 1.2625f, 0.0625f, 0.4625f, 1.7500f, 0.8938f),
            new Slot("bone10", 0.4688f, 1.2625f, 0.0625f, 0.5938f, 1.7500f, 0.8938f),
            new Slot("bone11", 0.6000f, 1.2625f, 0.0625f, 0.7250f, 1.7500f, 0.8938f),
            new Slot("bone12", 0.7312f, 1.2625f, 0.0625f, 0.8562f, 1.7500f, 0.8938f),
            new Slot("bone13", 1.1375f, 1.2625f, 0.0625f, 1.2625f, 1.7500f, 0.8938f),
            new Slot("bone14", 1.2688f, 1.2625f, 0.0625f, 1.3938f, 1.7500f, 0.8938f),
            new Slot("bone15", 1.4000f, 1.2625f, 0.0625f, 1.5250f, 1.7500f, 0.8938f),
            new Slot("bone16", 1.5312f, 1.2625f, 0.0625f, 1.6562f, 1.7500f, 0.8938f),
            new Slot("bone17", 1.6625f, 1.2625f, 0.0625f, 1.7875f, 1.7500f, 0.8938f),
            new Slot("bone18", 1.7937f, 1.2625f, 0.0625f, 1.9187f, 1.7500f, 0.8938f),
            new Slot("13", 0.0750f, 1.8250f, 0.0625f, 0.2000f, 2.3125f, 0.8938f),
            new Slot("14", 0.2062f, 1.8250f, 0.0625f, 0.3312f, 2.3125f, 0.8938f),
            new Slot("15", 0.3375f, 1.8250f, 0.0625f, 0.4625f, 2.3125f, 0.8938f),
            new Slot("16", 0.4688f, 1.8250f, 0.0625f, 0.5938f, 2.3125f, 0.8938f),
            new Slot("17", 0.6000f, 1.8250f, 0.0625f, 0.7250f, 2.3125f, 0.8938f),
            new Slot("18", 0.7312f, 1.8250f, 0.0625f, 0.8562f, 2.3125f, 0.8938f),
            new Slot("19", 1.1375f, 1.8250f, 0.0625f, 1.2625f, 2.3125f, 0.8938f),
            new Slot("bone2", 1.2688f, 1.8250f, 0.0625f, 1.3938f, 2.3125f, 0.8938f),
            new Slot("bone3", 1.4000f, 1.8250f, 0.0625f, 1.5250f, 2.3125f, 0.8938f),
            new Slot("bone4", 1.5312f, 1.8250f, 0.0625f, 1.6562f, 2.3125f, 0.8938f),
            new Slot("bone5", 1.6625f, 1.8250f, 0.0625f, 1.7875f, 2.3125f, 0.8938f),
            new Slot("bone6", 1.7937f, 1.8250f, 0.0625f, 1.9187f, 2.3125f, 0.8938f),
            new Slot("1", 0.0750f, 2.3875f, 0.0625f, 0.2000f, 2.8750f, 0.8938f),
            new Slot("2", 0.2062f, 2.3875f, 0.0625f, 0.3312f, 2.8750f, 0.8938f),
            new Slot("3", 0.3375f, 2.3875f, 0.0625f, 0.4625f, 2.8750f, 0.8938f),
            new Slot("4", 0.4688f, 2.3875f, 0.0625f, 0.5938f, 2.8750f, 0.8938f),
            new Slot("5", 0.6000f, 2.3875f, 0.0625f, 0.7250f, 2.8750f, 0.8938f),
            new Slot("6", 0.7312f, 2.3875f, 0.0625f, 0.8562f, 2.8750f, 0.8938f),
            new Slot("7", 1.1375f, 2.3875f, 0.0625f, 1.2625f, 2.8750f, 0.8938f),
            new Slot("8", 1.2688f, 2.3875f, 0.0625f, 1.3938f, 2.8750f, 0.8938f),
            new Slot("9", 1.4000f, 2.3875f, 0.0625f, 1.5250f, 2.8750f, 0.8938f),
            new Slot("10", 1.5312f, 2.3875f, 0.0625f, 1.6562f, 2.8750f, 0.8938f),
            new Slot("11", 1.6625f, 2.3875f, 0.0625f, 1.7875f, 2.8750f, 0.8938f),
            new Slot("12", 1.7937f, 2.3875f, 0.0625f, 1.9187f, 2.8750f, 0.8938f)
    };

    private BatteryCabinetSlots() {
    }

    public static Direction sideDirection(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static Vec3 toWorld(BlockPos mainPos, Direction facing, double u, double v, double w) {
        double x;
        double z;
        switch (facing) {
            case NORTH -> {
                x = mainPos.getX() + 1.0 - u;
                z = mainPos.getZ() + 1.0 - w;
            }
            case SOUTH -> {
                x = mainPos.getX() + u;
                z = mainPos.getZ() + w;
            }
            case WEST -> {
                x = mainPos.getX() + 1.0 - w;
                z = mainPos.getZ() + u;
            }
            case EAST -> {
                x = mainPos.getX() + w;
                z = mainPos.getZ() + 1.0 - u;
            }
            default -> throw new IllegalArgumentException("Battery cabinet requires horizontal facing");
        }
        return new Vec3(x, mainPos.getY() + v, z);
    }

    public static double[] toLocal(BlockPos mainPos, Direction facing, Vec3 point) {
        double relX = point.x - mainPos.getX();
        double relY = point.y - mainPos.getY();
        double relZ = point.z - mainPos.getZ();
        return switch (facing) {
            case NORTH -> new double[]{1.0 - relX, relY, 1.0 - relZ};
            case SOUTH -> new double[]{relX, relY, relZ};
            case WEST -> new double[]{relZ, relY, 1.0 - relX};
            case EAST -> new double[]{1.0 - relZ, relY, relX};
            default -> throw new IllegalArgumentException("Battery cabinet requires horizontal facing");
        };
    }

    public static AABB slotWorldBox(BlockPos mainPos, Direction facing, int slot) {
        Slot s = SLOTS[slot];
        Vec3 a = toWorld(mainPos, facing, s.u0(), s.v0(), s.w0());
        Vec3 b = toWorld(mainPos, facing, s.u1(), s.v1(), s.w1());
        return new AABB(
                Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z),
                Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z)
        );
    }

    public static int pickSlot(BlockPos mainPos, Direction facing, long batteriesMask, boolean wantInstalled, Vec3 eye, Vec3 end) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < SLOT_COUNT; i++) {
            boolean installed = (batteriesMask & (1L << i)) != 0L;
            if (installed != wantInstalled) {
                continue;
            }
            AABB box = slotWorldBox(mainPos, facing, i);
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isPresent()) {
                double distance = hit.get().distanceToSqr(eye);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
        }
        return best;
    }
}
