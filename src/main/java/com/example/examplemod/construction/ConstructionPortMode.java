package com.example.examplemod.construction;

import java.util.Locale;

public enum ConstructionPortMode {
    // Muted, warm, painted-metal indicators in the Create (机械动力) palette.
    // Within each family the input is the lighter tint and the output the
    // deeper/warmer shade, so direction reads at a glance without neon.
    DISABLED(0x000000),
    KINETIC_INPUT(0x9A86C4),
    KINETIC_OUTPUT(0x6E5AA0),
    ITEM_INPUT(0x7FB069),
    ITEM_OUTPUT(0x4F8A5B),
    FLUID_INPUT(0x6C9BD1),
    FLUID_OUTPUT(0x3E6FA8),
    REDSTONE_INPUT(0xD9B44A),
    REDSTONE_OUTPUT(0xC1642F),
    /** Unloads the room when a redstone signal is present. Used to pause factories. */
    UNLOAD(0x8B4C4C);

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

    public String translationKey() {
        return "port_mode.examplemod." + name().toLowerCase(Locale.ROOT);
    }
}
