package com.zzynes.flydrone.VVER;

public enum BshuScreenType {
    CORE("screen_core", 512, 512, 2, false),
    SECONDARY("screen_xzz", 512, 185, 1, false),
    TERTIARY("screen_xzzz", 512, 185, 1, true),
    SCHEMATIC("screen_sxematica", 512, 246, 3, false),
    FUEL("screen_xz", 512, 209, 1, false),
    PARAMETERS("screen_parametr", 512, 217, 2, false),
    ERRORS("screen_error", 512, 206, 3, false),
    CONTROL("screen_control", 384, 120, 2, true),
    GCN("screen_gcn", 160, 512, 2, true);

    private final String boneName;
    private final int width;
    private final int height;
    private final int department;
    private final boolean input;

    BshuScreenType(String boneName, int width, int height, int department, boolean input) {
        this.boneName = boneName;
        this.width = width;
        this.height = height;
        this.department = department;
        this.input = input;
    }

    public String boneName() {
        return this.boneName;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int department() {
        return this.department;
    }

    public boolean isInput() {
        return this.input;
    }

    public static BshuScreenType byBone(String boneName) {
        for (BshuScreenType type : values()) {
            if (type.boneName.equals(boneName)) {
                return type;
            }
        }
        return null;
    }
}
