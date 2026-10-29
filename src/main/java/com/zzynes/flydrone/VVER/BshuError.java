package com.zzynes.flydrone.VVER;

public enum BshuError {
    NO_LINK("НЕТ СВЯЗИ", true),
    LINK_LOST("СВЯЗЬ ПОТЕРЯНА", true),
    POWER_1("ПИТАНИЕ ОТД. 1", false),
    POWER_2("ПИТАНИЕ ОТД. 2", true),
    POWER_3("ПИТАНИЕ ОТД. 3", false),
    ROD_UNGROUPED("СУЗ БЕЗ ГРУППЫ", false),
    TVS_EMPTY("ПУСТАЯ ТВС", false),
    NO_BANK("ГРУППА НЕ ВЫБРАНА", false),
    AZ5("АЗ-5", true),
    ALARM("ТРЕВОГА", true),
    GCN_OFF("ГЦН ОСТАНОВЛЕНЫ", false),
    DG_FAILED("ДГ НЕ ЗАПУСТИЛСЯ", true);

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

    public int bit() {
        return 1 << ordinal();
    }
}
