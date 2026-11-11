package com.zzynes.sarcophag.VVER;

import java.util.Locale;

public enum BshuToggle {
    NASOS_1,
    NASOS_2,
    NASOS_3,
    TURBINE,
    A1,
    A2,
    A3,
    A4,
    A5,
    A6,
    B1,
    B2,
    B3,
    B4,
    B5,
    B6,
    C1,
    C2,
    C3,
    C4,
    C5,
    C6,
    GCN_1,
    GCN_2,
    GCN_3,
    PRESSURE,
    ZACHITA,
    ROD_HOLD,
    BOR,
    WATER,
    SAOZ,
    READY,
    BP_1,
    BP_2,
    BP_3,
    CL_1,
    CL_2,
    CL_3,
    CL_4,
    VENT_1,
    VENT_2,
    VENT_3,
    VENT_4,
    SUMP_PUMP,
    ISOLATE;

    public String boneName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BshuToggle byBone(String boneName) {
        for (BshuToggle toggle : values()) {
            if (toggle.boneName().equals(boneName)) {
                return toggle;
            }
        }
        return null;
    }
}
