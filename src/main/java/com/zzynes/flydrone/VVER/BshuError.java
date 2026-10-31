package com.zzynes.flydrone.VVER;

public enum BshuError {
    NO_LINK("НЕТ СВЯЗИ", true),
    LINK_LOST("СВЯЗЬ ПОТЕРЯНА", true),
    POWER_1("ПИТАНИЕ ОТД. 1", false),
    POWER_2("ПИТАНИЕ ОТД. 2", true),
    POWER_3("ПИТАНИЕ ОТД. 3", false),
    TVS_EMPTY("ПУСТАЯ ТВС", false),
    NO_BANK("СУЗ НЕ ВЫБРАНЫ", false),
    AZ5("АЗ-5", true),
    ALARM("ТРЕВОГА", true),
    GCN_OFF("ГЦН ОСТАНОВЛЕНЫ", false),
    DG_FAILED("ДГ НЕ ЗАПУСТИЛСЯ", true),
    GCN_STRENGTH("КРИТ. ПРОЧН. ГЦН", true),
    MELTDOWN("МЕЛТДАУН", true),
    ROD_STRENGTH_CRITICAL("КРИТ. ПРОЧН. СТЕРЖНЕЙ", true),
    SUZ_STRENGTH_CRITICAL("КРИТ. ПРОЧН. СУЗ", true),
    ROD_OVERHEAT("ПЕРЕГРЕВ СТЕРЖНЕЙ", false),
    SUZ_OVERHEAT("ПЕРЕГРЕВ СУЗ", false),
    SUZ_STUCK("ЗАКЛИНИВАНИЕ СУЗ", false),
    POWER_RISE("РОСТ МОЩНОСТИ", true),
    NO_CIRCULATION("НЕТ ЦИРКУЛЯЦИИ", true),
    GCN_CAVITATION("КАВИТАЦИЯ ГЦН", false),
    COOLANT_LOSS("ПОТЕРЯ ТЕПЛОНОСИТЕЛЯ", true),
    HIGH_PRESSURE("ВЫСОКОЕ ДАВЛЕНИЕ 1К", true),
    HYDROGEN("ВОДОРОД", true),
    ACTIVITY_RELEASE("ВЫБРОС АКТИВНОСТИ", true),
    VESSEL_STRENGTH("ПРОЧНОСТЬ КОРПУСА", true),
    STEAM_GENERATOR_DRY("ПГ БЕЗ ВОДЫ", false),
    VENT_FAILURE("ОТКАЗ ВЕНТИЛЯЦИИ", false),
    DRAIN_FAILURE("ОТКАЗ СЛИВА", false),
    HULL_NOT_SEALED("КОРПУС НЕ ГЕРМЕТИЧЕН", false);

    private final String label;
    private final boolean critical;

    BshuError(String label, boolean critical) {
        this.label = label;
        this.critical = critical;
    }

    public String label() {
        return this.label;
    }

    public boolean critical() {
        return this.critical;
    }

    public long bit() {
        return 1L << ordinal();
    }
}
