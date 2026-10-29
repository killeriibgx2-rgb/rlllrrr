package com.zzynes.flydrone.VVER;

public record BshuLampState(boolean lit, float red, float green, float blue) {
    public static final BshuLampState OFF = new BshuLampState(false, 0.34f, 0.36f, 0.36f);
    public static final BshuLampState GREEN = new BshuLampState(true, 0.15f, 1.0f, 0.32f);
    public static final BshuLampState RED = new BshuLampState(true, 1.0f, 0.12f, 0.12f);
    public static final BshuLampState YELLOW = new BshuLampState(true, 1.0f, 0.78f, 0.16f);
    public static final BshuLampState WHITE = new BshuLampState(true, 0.95f, 0.98f, 0.92f);
}
