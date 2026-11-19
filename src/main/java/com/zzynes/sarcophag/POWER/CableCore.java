package com.zzynes.sarcophag.POWER;

public enum CableCore {
    PE(0x9CCB3B),
    N(0x2B4FC7),
    L1(0x7A4B24),
    L2(0x9AA0A6),
    L3(0x1B1B1B),
    HV(0xB3362B);

    public final int color;

    CableCore(int color) {
        this.color = color;
    }

    public float red() {
        return ((color >> 16) & 255) / 255f;
    }

    public float green() {
        return ((color >> 8) & 255) / 255f;
    }

    public float blue() {
        return (color & 255) / 255f;
    }

    public CableCore next() {
        CableCore[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static CableCore byIndex(int index) {
        CableCore[] values = values();
        return index >= 0 && index < values.length ? values[index] : PE;
    }

    public String key() {
        return "cable.core." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
