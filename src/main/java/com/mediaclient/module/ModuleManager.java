package com.mediaclient.module;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    private final List<ClientModule> modules = new ArrayList<>();

    public ModuleManager() {
        add(new ClientModule("activity_detector", "Player Activity Detector", "Tracks local world updates and nearby player changes.", ModuleCategory.WORLD, true));
        add(new ClientModule("player_esp", "Player ESP", "Player boxes and status labels.", ModuleCategory.ESP, false));
        add(new ClientModule("chest_esp", "Container ESP", "Highlights loaded containers in range.", ModuleCategory.ESP, false));
        add(new ClientModule("machine_esp", "Machine ESP", "Highlights configured redstone and automation blocks.", ModuleCategory.ESP, false));
        add(new ClientModule("activity_esp", "Activity Highlights", "Temporarily marks interesting block-state changes.", ModuleCategory.ESP, true));
        add(new ClientModule("schematic_builder", "Schematic Builder", "Places blocks from a loaded .schem file with inventory checks.", ModuleCategory.AUTOMATION, false));
        add(new ClientModule("performance_mode", "Performance Mode", "Reduces detector work by slowing its scan cadence.", ModuleCategory.PERFORMANCE, false));
        add(new ClientModule("notifications", "Activity Notifications", "Shows short action-bar notifications for tracked events.", ModuleCategory.MISC, true));
    }

    private void add(ClientModule module) { modules.add(module); }
    public List<ClientModule> all() { return List.copyOf(modules); }
    public List<ClientModule> inCategory(ModuleCategory category) { return modules.stream().filter(m -> m.category() == category).toList(); }
    public ClientModule get(String id) { return modules.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null); }
}
