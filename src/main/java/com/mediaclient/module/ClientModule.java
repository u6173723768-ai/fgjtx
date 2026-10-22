package com.mediaclient.module;

import com.mediaclient.config.MediaConfig;

public final class ClientModule {
    private final String id;
    private final String name;
    private final String description;
    private final ModuleCategory category;
    private boolean enabled;

    public ClientModule(String id, String name, String description, ModuleCategory category, boolean defaultEnabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = MediaConfig.get().moduleStates.getOrDefault(id, defaultEnabled);
        MediaConfig.get().moduleStates.putIfAbsent(id, this.enabled);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public ModuleCategory category() { return category; }
    public boolean enabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        MediaConfig.get().moduleStates.put(id, enabled);
        MediaConfig.get().save();
    }

    public void toggle() { setEnabled(!enabled); }
}
