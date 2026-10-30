package com.zzynes.flydrone.VVER;

import net.minecraft.util.Mth;

public final class VverThermal {
    public static final float AMBIENT_C = 20.0f;
    public static final float SCALE_MAX_C = 2000.0f;
    public static final float TVS_NORMAL_C = 300.0f;
    public static final float TVS_DAMAGE_C = 750.0f;
    public static final float TVS_FAST_DAMAGE_C = 1000.0f;
    public static final float TVS_ACCIDENT_C = 1200.0f;
    public static final float TVS_CRITICAL_C = 1600.0f;
    public static final float TVS_SHELL_MELT_C = 1850.0f;
    public static final float TVS_FUEL_MELT_C = 2850.0f;
    public static final float SUZ_NORMAL_C = 290.0f;
    public static final float SUZ_DAMAGE_C = 700.0f;
    public static final float SUZ_FAST_DAMAGE_C = 1000.0f;
    public static final float SUZ_CRITICAL_C = 1100.0f;
    public static final float SUZ_MELT_C = 1250.0f;
    public static final float CRITICAL_INTEGRITY_PERCENT = 25.0f;
    public static final float GCN_WARNING_INTEGRITY_PERCENT = 70.0f;
    public static final float GCN_CRITICAL_INTEGRITY_PERCENT = 30.0f;

    private VverThermal() {
    }

    public static float clampTemperature(float temperatureC) {
        return Mth.clamp(temperatureC, -50.0f, 4000.0f);
    }

    public static float clampIntegrity(float integrityPercent) {
        return Mth.clamp(integrityPercent, 0.0f, 100.0f);
    }

    public static float damagePercentPerSecond(float temperatureC, float damageStartC, float meltC) {
        if (temperatureC >= meltC) {
            return 100.0f;
        }
        if (temperatureC <= damageStartC) {
            return 0.0f;
        }
        float ratio = (temperatureC - damageStartC) / Math.max(1.0f, meltC - damageStartC);
        return 9.0f * ratio * ratio * ratio;
    }

    public static float applyDamagePerTick(float integrityPercent, float temperatureC, float damageStartC, float meltC) {
        if (temperatureC >= meltC) {
            return 0.0f;
        }
        float damage = damagePercentPerSecond(temperatureC, damageStartC, meltC) / 20.0f;
        return clampIntegrity(integrityPercent - damage);
    }

    public static boolean isCriticalIntegrity(float integrityPercent) {
        return integrityPercent <= CRITICAL_INTEGRITY_PERCENT;
    }
}
