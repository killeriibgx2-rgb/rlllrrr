package com.zzynes.sarcophag.POWER;

public enum CableTier {
    CONTROL(0.0625, 20, 0.0070),
    POWER(0.09375, 90, 0.0011),
    MAIN(0.15625, 400, 0.00007);

    public final double radius;
    public final int maxAmps;
    public final double ohmPerBlock;

    CableTier(double radius, int maxAmps, double ohmPerBlock) {
        this.radius = radius;
        this.maxAmps = maxAmps;
        this.ohmPerBlock = ohmPerBlock;
    }

    public static CableTier byIndex(int index) {
        CableTier[] values = values();
        return index >= 0 && index < values.length ? values[index] : CONTROL;
    }
}
