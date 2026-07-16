package com.example.examplemod.construction;

public enum ConstructionPortMode {
    DISABLED(0x000000),
    KINETIC_INPUT(0xA855F7),
    KINETIC_OUTPUT(0xEF4444),
    ITEM_INPUT(0x22C55E),
    ITEM_OUTPUT(0x14B8A6),
    FLUID_INPUT(0x3B82F6),
    FLUID_OUTPUT(0x06B6D4),
    REDSTONE_INPUT(0xFACC15),
    REDSTONE_OUTPUT(0xF97316);

    private static final ConstructionPortMode[] VALUES = values();
    private final int indicatorColor;

    ConstructionPortMode(int indicatorColor) {
        this.indicatorColor = indicatorColor;
    }

    public ConstructionPortMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static ConstructionPortMode fromId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : DISABLED;
    }

    public boolean isKinetic() {
        return this == KINETIC_INPUT || this == KINETIC_OUTPUT;
    }

    public int indicatorColor() {
        return indicatorColor;
    }
}
