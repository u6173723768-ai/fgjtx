package com.mediaclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent settings. Missing keys are populated from field initializers by Gson. */
public final class MediaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("media-client.json");
    private static MediaConfig instance;

    public Map<String, Boolean> moduleStates = new LinkedHashMap<>();
    public Map<String, Boolean> machineBlockToggles = new LinkedHashMap<>();
    public Map<String, Boolean> containerBlockToggles = new LinkedHashMap<>();
    public Map<String, Boolean> eventToggles = new LinkedHashMap<>();
    public List<String> friendNames = new ArrayList<>();

    public int playerRange = 64;
    public int containerRange = 32;
    public int machineRange = 32;
    public int activityRadius = 8;
    public int scanIntervalTicks = 20;
    public int highlightDurationSeconds = 5;
    public int maxTrackedEvents = 300;
    public int placementDelayMs = 120;
    public int blocksPerSecond = 8;
    public int builderPauseDistance = 6;

    public boolean playerBox = true;
    public boolean playerOutline = true;
    public boolean playerName = true;
    public boolean playerDistance = true;
    public boolean playerHealth = true;
    public boolean playerArmor = false;
    public boolean playerHeldItems = false;
    public boolean playerEsp2D = false;
    public boolean ignoreInvisiblePlayers = true;
    public boolean hideFriends = false;
    public boolean useFriendColor = true;
    public boolean hideTeammates = false;
    public boolean highlightNotifications = true;
    public boolean activityNotifications = true;
    public boolean pauseBuilderWhenMoving = true;
    public boolean onlyHighlightLoadedChunks = true;

    public int playerColor = 0xFF54D6FF;
    public int friendColor = 0xFF58E68A;
    public int containerColor = 0xFFFFC85A;
    public int machineColor = 0xFFB28AFF;
    public int activityColor = 0xFFFF6B82;

    private MediaConfig() { setDefaultsIfAbsent(); }

    private void setDefaultsIfAbsent() {
        if (moduleStates == null) moduleStates = new LinkedHashMap<>();
        if (machineBlockToggles == null) machineBlockToggles = new LinkedHashMap<>();
        if (containerBlockToggles == null) containerBlockToggles = new LinkedHashMap<>();
        if (eventToggles == null) eventToggles = new LinkedHashMap<>();
        if (friendNames == null) friendNames = new ArrayList<>();
        putDefault(machineBlockToggles, "piston", true);
        putDefault(machineBlockToggles, "sticky_piston", true);
        putDefault(machineBlockToggles, "dispenser", true);
        putDefault(machineBlockToggles, "dropper", true);
        putDefault(machineBlockToggles, "hopper", true);
        putDefault(machineBlockToggles, "observer", true);
        putDefault(machineBlockToggles, "crafter", true);
        putDefault(machineBlockToggles, "redstone_block", true);
        putDefault(machineBlockToggles, "redstone_torch", true);
        putDefault(machineBlockToggles, "redstone_wall_torch", true);
        putDefault(machineBlockToggles, "repeater", true);
        putDefault(machineBlockToggles, "comparator", true);
        putDefault(machineBlockToggles, "redstone_wire", true);
        putDefault(machineBlockToggles, "redstone_lamp", true);
        putDefault(machineBlockToggles, "lever", true);
        putDefault(machineBlockToggles, "stone_button", true);
        putDefault(containerBlockToggles, "chest", true);
        putDefault(containerBlockToggles, "trapped_chest", true);
        putDefault(containerBlockToggles, "ender_chest", true);
        putDefault(containerBlockToggles, "barrel", true);
        putDefault(containerBlockToggles, "shulker_box", true);
        putDefault(containerBlockToggles, "furnace", false);
        putDefault(containerBlockToggles, "blast_furnace", false);
        putDefault(containerBlockToggles, "smoker", false);
        putDefault(containerBlockToggles, "brewing_stand", false);
        putDefault(containerBlockToggles, "dispenser", false);
        putDefault(containerBlockToggles, "dropper", false);
        putDefault(containerBlockToggles, "hopper", false);
        putDefault(containerBlockToggles, "crafter", false);
        putDefault(containerBlockToggles, "chiseled_bookshelf", false);
        putDefault(containerBlockToggles, "lectern", false);
        putDefault(containerBlockToggles, "decorated_pot", false);
        putDefault(eventToggles, "player_range", true);
        putDefault(eventToggles, "block_place_break", true);
        putDefault(eventToggles, "crop_growth", true);
        putDefault(eventToggles, "kelp_growth", true);
        putDefault(eventToggles, "amethyst_growth", true);
        putDefault(eventToggles, "machine_activity", true);
        putDefault(eventToggles, "container_open", true);
        putDefault(eventToggles, "door_activity", true);
    }

    private static void putDefault(Map<String, Boolean> map, String key, boolean value) {
        map.putIfAbsent(key, value);
    }

    public static synchronized MediaConfig get() {
        if (instance == null) {
            instance = new MediaConfig();
            if (Files.exists(FILE)) {
                try {
                    MediaConfig read = GSON.fromJson(Files.readString(FILE), MediaConfig.class);
                    if (read != null) {
                        read.setDefaultsIfAbsent();
                        instance = read;
                    }
                } catch (Exception e) {
                    System.err.println("[Media Client] Could not read config; defaults will be used: " + e.getMessage());
                }
            }
            instance.clamp();
        }
        return instance;
    }

    public synchronized void save() {
        clamp();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[Media Client] Could not save config: " + e.getMessage());
        }
    }

    public void clamp() {
        playerRange = clamp(playerRange, 4, 512);
        containerRange = clamp(containerRange, 4, 48);
        machineRange = clamp(machineRange, 4, 48);
        activityRadius = clamp(activityRadius, 3, 20);
        scanIntervalTicks = clamp(scanIntervalTicks, 5, 200);
        highlightDurationSeconds = clamp(highlightDurationSeconds, 1, 60);
        maxTrackedEvents = clamp(maxTrackedEvents, 50, 2000);
        placementDelayMs = clamp(placementDelayMs, 0, 1500);
        blocksPerSecond = clamp(blocksPerSecond, 1, 20);
        builderPauseDistance = clamp(builderPauseDistance, 2, 32);
    }

    private static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
}
