package com.mediaclient.module;

public enum ModuleCategory {
    COMBAT("Combat"), PLAYER("Player"), VISUAL("Visual"), WORLD("World"), ESP("ESP"), AUTOMATION("Automation"), PERFORMANCE("Performance"), MISC("DonutSMP");
    private final String title;
    ModuleCategory(String title) { this.title = title; }
    public String title() { return title; }
}
