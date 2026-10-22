package com.mediaclient.module;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    private final List<ClientModule> modules = new ArrayList<>();

    public ModuleManager() {
        // Core detection, ESP, and schematic features.
        add(new ClientModule("activity_detector", "Player Activity Detector", "Tracks nearby world changes and player presence.", ModuleCategory.WORLD, true));
        add(new ClientModule("player_esp", "Player ESP", "Boxes, names, distance, health, armor and held items.", ModuleCategory.ESP, false));
        add(new ClientModule("chest_esp", "Container ESP", "Highlights containers in cached loaded chunks.", ModuleCategory.ESP, false));
        add(new ClientModule("machine_esp", "Machine ESP", "Highlights redstone and automation blocks.", ModuleCategory.ESP, false));
        add(new ClientModule("activity_esp", "Activity Highlights", "Temporarily marks nearby block-state changes.", ModuleCategory.ESP, true));
        add(new ClientModule("item_esp", "Dropped Item ESP", "Outlines nearby dropped items with a strict render cap.", ModuleCategory.ESP, false));
        add(new ClientModule("schematic_builder", "Schematic Builder", "Places blocks from a loaded .schem file with inventory checks.", ModuleCategory.AUTOMATION, false));
        add(new ClientModule("performance_mode", "Performance Mode", "Reduces background detector work.", ModuleCategory.PERFORMANCE, false));
        add(new ClientModule("notifications", "Activity Notifications", "Shows short action-bar notifications for tracked events.", ModuleCategory.MISC, true));

        // Combat helpers use normal visible player rotation; nothing spoofs packets.
        add(new ClientModule("target_assist", "Target Assist", "Smoothly rotates toward a nearby player while attack is held.", ModuleCategory.COMBAT, false));
        add(new ClientModule("triggerbot", "Triggerbot", "Attacks a player only while they are under the crosshair.", ModuleCategory.COMBAT, false));
        add(new ClientModule("target_hud", "Target HUD", "Shows crosshair target name, health and distance.", ModuleCategory.COMBAT, false));

        // Player utilities.
        add(new ClientModule("auto_sprint", "Auto Sprint", "Sprints while moving forward when possible.", ModuleCategory.PLAYER, false));
        add(new ClientModule("auto_respawn", "Auto Respawn", "Requests respawn when the death screen is open.", ModuleCategory.PLAYER, false));
        add(new ClientModule("auto_tool", "Auto Tool", "Selects the best hotbar tool while breaking a block.", ModuleCategory.PLAYER, false));
        add(new ClientModule("auto_eat", "Auto Eat", "Uses held food when hunger is low.", ModuleCategory.PLAYER, false));
        add(new ClientModule("totem_alert", "Totem Alert", "Warns when health is low and no totem is equipped.", ModuleCategory.PLAYER, false));

        // Visual / HUD utilities.
        add(new ClientModule("fullbright", "Fullbright", "Temporarily raises gamma and restores the previous setting.", ModuleCategory.VISUAL, false));
        add(new ClientModule("zoom", "Zoom", "Hold C to zoom while this module is enabled.", ModuleCategory.VISUAL, false));
        add(new ClientModule("coordinates_hud", "Coordinates HUD", "Shows your current block coordinates.", ModuleCategory.VISUAL, false));
        add(new ClientModule("fps_hud", "FPS HUD", "Shows the current frame rate.", ModuleCategory.VISUAL, false));
        add(new ClientModule("armor_hud", "Armor HUD", "Shows armor durability.", ModuleCategory.VISUAL, false));

        // Specific growth watches are independently toggleable.
        add(new ClientModule("amethyst_growth_watch", "Amethyst Growth Watch", "Detects increases in amethyst-bud growth stage.", ModuleCategory.WORLD, true));
        add(new ClientModule("kelp_growth_watch", "Kelp Growth Watch", "Detects new kelp placed above an existing kelp stem.", ModuleCategory.WORLD, true));
        add(new ClientModule("crop_growth_watch", "Crop Growth Watch", "Detects crop age increases rather than arbitrary block changes.", ModuleCategory.WORLD, true));

        // Automation / safety helpers.
        add(new ClientModule("auto_planter", "Auto Planter", "Repeats crop placement on farmland while use is held.", ModuleCategory.AUTOMATION, false));
        add(new ClientModule("inventory_reminder", "Inventory Reminder", "Warns when the selected tool is nearly broken.", ModuleCategory.AUTOMATION, false));
        add(new ClientModule("durability_guard", "Durability Guard", "Switches to a healthier matching hotbar tool before breakage.", ModuleCategory.AUTOMATION, false));
        add(new ClientModule("schematic_status", "Schematic Status HUD", "Displays the current schematic queue status.", ModuleCategory.AUTOMATION, false));

        // Performance controls.
        add(new ClientModule("fps_cap", "120 FPS Cap", "Temporarily limits the frame rate to 120 and restores it when disabled.", ModuleCategory.PERFORMANCE, false));
        add(new ClientModule("low_particles", "Low Particles", "Temporarily switches particles to Minimal.", ModuleCategory.PERFORMANCE, false));
        add(new ClientModule("adaptive_scan", "Adaptive Scan", "Uses a slower scan cadence to reduce CPU spikes.", ModuleCategory.PERFORMANCE, false));

        // The former Misc page is repurposed as a DonutSMP utility page.
        add(new ClientModule("nearby_player_alerts", "Nearby Player Alerts", "Highlights player arrivals and departures in detection range.", ModuleCategory.MISC, true));
        add(new ClientModule("session_hud", "Session HUD", "Shows elapsed time in the current world/server session.", ModuleCategory.MISC, false));
        add(new ClientModule("ping_hud", "Ping HUD", "Shows your current server ping.", ModuleCategory.MISC, false));
    }

    private void add(ClientModule module) { modules.add(module); }
    public List<ClientModule> all() { return List.copyOf(modules); }
    public List<ClientModule> inCategory(ModuleCategory category) { return modules.stream().filter(m -> m.category() == category).toList(); }
    public ClientModule get(String id) { return modules.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null); }
}
