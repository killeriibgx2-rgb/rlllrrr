package com.zzynes.flydrone.VVER;

public enum HullType {
    WALL(6.0f, 14.0f, 1.0),
    BOTTOM(8.0f, 20.0f, 1.25),
    LID(5.0f, 12.0f, 0.9),
    NOZZLE(6.0f, 14.0f, 0.85);

    private final float hardness;
    private final float resistance;
    private final double strength;

    HullType(float hardness, float resistance, double strength) {
        this.hardness = hardness;
        this.resistance = resistance;
        this.strength = strength;
    }

    public float hardness() {
        return this.hardness;
    }

    public float resistance() {
        return this.resistance;
    }

    public double strength() {
        return this.strength;
    }
}
