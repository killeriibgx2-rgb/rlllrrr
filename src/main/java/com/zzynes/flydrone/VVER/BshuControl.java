package com.zzynes.flydrone.VVER;

public enum BshuControl {
    STOP_NASOS("stop_nasos", 1, Kind.MOMENTARY, null, 0),
    NASOS_1("nasos_1", 1, Kind.TOGGLE, BshuToggle.NASOS_1, 1),
    NASOS_2("nasos_2", 1, Kind.TOGGLE, BshuToggle.NASOS_2, 2),
    NASOS_3("nasos_3", 1, Kind.TOGGLE, BshuToggle.NASOS_3, 3),
    A1("a1", 1, Kind.PAIR, BshuToggle.A1, 0),
    A2("a2", 1, Kind.PAIR, BshuToggle.A2, 0),
    A3("a3", 1, Kind.PAIR, BshuToggle.A3, 0),
    A4("a4", 1, Kind.PAIR, BshuToggle.A4, 0),
    A5("a5", 1, Kind.PAIR, BshuToggle.A5, 0),
    A6("a6", 1, Kind.PAIR, BshuToggle.A6, 0),
    B1("b1", 1, Kind.PAIR, BshuToggle.B1, 0),
    B2("b2", 1, Kind.PAIR, BshuToggle.B2, 0),
    B3("b3", 1, Kind.PAIR, BshuToggle.B3, 0),
    B4("b4", 1, Kind.PAIR, BshuToggle.B4, 0),
    B5("b5", 1, Kind.PAIR, BshuToggle.B5, 0),
    B6("b6", 1, Kind.PAIR, BshuToggle.B6, 0),
    C1("c1", 1, Kind.PAIR, BshuToggle.C1, 0),
    C2("c2", 1, Kind.PAIR, BshuToggle.C2, 0),
    C3("c3", 1, Kind.PAIR, BshuToggle.C3, 0),
    C4("c4", 1, Kind.PAIR, BshuToggle.C4, 0),
    C5("c5", 1, Kind.PAIR, BshuToggle.C5, 0),
    C6("c6", 1, Kind.PAIR, BshuToggle.C6, 0),
    TURBINE_START("turbine1", 1, Kind.MOMENTARY, null, 0),
    TURBINE_STOP("turbine0", 1, Kind.MOMENTARY, null, 0),
    POWER_1("power_1", 1, Kind.POWER, null, 1),
    LAMP_TEST("lamp_test", 2, Kind.MOMENTARY, null, 0),
    KVITIR("kvitir", 2, Kind.MOMENTARY, null, 0),
    ALARM("alarm", 2, Kind.MOMENTARY, null, 0),
    ALARM_STOP("stop", 2, Kind.MOMENTARY, null, 0),
    GCN_1("gcn_1", 2, Kind.TOGGLE, BshuToggle.GCN_1, 1),
    GCN_2("gcn_2", 2, Kind.TOGGLE, BshuToggle.GCN_2, 2),
    GCN_3("gcn_3", 2, Kind.TOGGLE, BshuToggle.GCN_3, 3),
    PRESSURE0("pressure0", 2, Kind.TOGGLE, BshuToggle.PRESSURE, 0),
    ROD_BANK_0("rod_bank_0", 2, Kind.ROD_BANK, null, 0),
    ROD_BANK_1("rod_bank_1", 2, Kind.ROD_BANK, null, 1),
    ROD_BANK_2("rod_bank_2", 2, Kind.ROD_BANK, null, 2),
    ROD_BANK_3("rod_bank_3", 2, Kind.ROD_BANK, null, 3),
    ROD_BANK_4("rod_bank_4", 2, Kind.ROD_BANK, null, 4),
    ROD_BANK_5("rod_bank_5", 2, Kind.ROD_BANK, null, 5),
    ROD_BANK_6("rod_bank_6", 2, Kind.ROD_BANK, null, 6),
    ROD_BANK_7("rod_bank_7", 2, Kind.ROD_BANK, null, 7),
    ROD_BANK_8("rod_bank_8", 2, Kind.ROD_BANK, null, 8),
    ZACHITA("zachita", 2, Kind.TOGGLE, BshuToggle.ZACHITA, 0),
    ROD_HOLD("rod_hold", 2, Kind.TOGGLE, BshuToggle.ROD_HOLD, 0),
    BOR("bor", 2, Kind.TOGGLE, BshuToggle.BOR, 0),
    WATER("water", 2, Kind.TOGGLE, BshuToggle.WATER, 0),
    SAOZ("saoz", 2, Kind.TOGGLE, BshuToggle.SAOZ, 0),
    AZ5("az5", 2, Kind.MOMENTARY, null, 0),
    RESET("reset", 2, Kind.MOMENTARY, null, 0),
    READY("ready", 2, Kind.TOGGLE, BshuToggle.READY, 0),
    DIESEL_START("diesel_start", 2, Kind.MOMENTARY, null, 0),
    DIESEL_STOP("diesel_stop", 2, Kind.MOMENTARY, null, 0),
    POWER_2("power_2", 2, Kind.POWER, null, 2),
    BP_1("bp_1", 3, Kind.PAIR, BshuToggle.BP_1, 0),
    BP_2("bp_2", 3, Kind.PAIR, BshuToggle.BP_2, 0),
    BP_3("bp_3", 3, Kind.PAIR, BshuToggle.BP_3, 0),
    CL_1("cl_1", 3, Kind.PAIR, BshuToggle.CL_1, 0),
    CL_2("cl_2", 3, Kind.PAIR, BshuToggle.CL_2, 0),
    CL_3("cl_3", 3, Kind.PAIR, BshuToggle.CL_3, 0),
    CL_4("cl_4", 3, Kind.PAIR, BshuToggle.CL_4, 0),
    VENT_1("vent_1", 3, Kind.PAIR, BshuToggle.VENT_1, 0),
    VENT_2("vent_2", 3, Kind.PAIR, BshuToggle.VENT_2, 0),
    VENT_3("vent_3", 3, Kind.PAIR, BshuToggle.VENT_3, 0),
    VENT_4("vent_4", 3, Kind.PAIR, BshuToggle.VENT_4, 0),
    SUMP_PUMP("sump_pump", 3, Kind.TOGGLE, BshuToggle.SUMP_PUMP, 0),
    ISOLATE("isolate", 3, Kind.TOGGLE, BshuToggle.ISOLATE, 0),
    VENT_ALL_ON("vent1", 3, Kind.MOMENTARY, null, 0),
    VENT_ALL_OFF("vent0", 3, Kind.MOMENTARY, null, 0),
    POWER_3("power_3", 3, Kind.POWER, null, 3);

    public enum Kind {
        MOMENTARY,
        TOGGLE,
        PAIR,
        POWER,
        ROD_BANK
    }

    private final String boneName;
    private final int department;
    private final Kind kind;
    private final BshuToggle toggle;
    private final int number;

    BshuControl(String boneName, int department, Kind kind, BshuToggle toggle, int number) {
        this.boneName = boneName;
        this.department = department;
        this.kind = kind;
        this.toggle = toggle;
        this.number = number;
    }

    public String boneName() {
        return this.boneName;
    }

    public int department() {
        return this.department;
    }

    public Kind kind() {
        return this.kind;
    }

    public BshuToggle toggle() {
        return this.toggle;
    }

    public int number() {
        return this.number;
    }

    public boolean pressable() {
        return this.kind != Kind.PAIR;
    }

    public static BshuControl byBone(String boneName) {
        for (BshuControl control : values()) {
            if (control.boneName.equals(boneName)) {
                return control;
            }
        }
        return null;
    }
}
