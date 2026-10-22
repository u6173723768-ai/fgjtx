package com.mediaclient.schematic;

import com.mediaclient.MediaClient;
import com.mediaclient.config.MediaConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Conservative schematic placer. It never breaks an existing block to force a match. */
public final class SchematicBuilder {
    private final Minecraft client = Minecraft.getInstance();
    private final Path schematicDirectory = FabricLoader.getInstance().getGameDir().resolve("media-client").resolve("schematics");
    private SchematicData schematic;
    private List<SchematicData.Placement> pending = new ArrayList<>();
    private BlockPos origin;
    private String dimensionId;
    private boolean building;
    private String status = "No schematic loaded";
    private long nextActionAt;
    private final Map<Long, Long> lastAttempts = new HashMap<>();
    private final Map<Long, Integer> attempts = new HashMap<>();
    private int placedCount;
    private int skippedCount;

    public void loadByName(String requestedName) {
        try {
            Files.createDirectories(schematicDirectory);
            String safeName = requestedName.trim();
            if (!safeName.toLowerCase().endsWith(".schem")) safeName += ".schem";
            Path root = schematicDirectory.toAbsolutePath().normalize();
            Path file = root.resolve(safeName).normalize();
            if (!file.startsWith(root)) { setStatus("Use a filename inside the schematics folder."); return; }
            if (!Files.isRegularFile(file)) { setStatus("Not found: " + file.getFileName() + " (put it in media-client/schematics)"); return; }
            SchematicData loaded = SchematicLoader.load(file);
            schematic = loaded; origin = null; pending = new ArrayList<>(loaded.blocks()); building = false;
            placedCount = 0; skippedCount = 0; attempts.clear(); lastAttempts.clear();
            setStatus("Loaded " + file.getFileName() + " · " + loaded.width() + "×" + loaded.height() + "×" + loaded.length() + " · " + loaded.blocks().size() + " blocks");
            notifyPlayer(status);
        } catch (IOException | RuntimeException e) {
            setStatus("Schematic load failed: " + e.getMessage()); notifyPlayer(status);
        }
    }

    public void toggleBuilding() {
        if (building) { building = false; setStatus("Builder paused · " + progress()); return; }
        if (schematic == null) { setStatus("Load a .schem file first."); notifyPlayer(status); return; }
        if (client.level == null || client.player == null) { setStatus("Join a world before starting the builder."); notifyPlayer(status); return; }
        if (origin == null || dimensionId == null || !dimensionId.equals(client.level.dimension().location().toString())) {
            origin = client.player.blockPosition().relative(client.player.getDirection());
            dimensionId = client.level.dimension().location().toString();
            rebuildQueueForWorld();
        }
        building = true; MediaClient.enableModule("schematic_builder", true);
        setStatus("Building · " + progress()); notifyPlayer(status);
    }

    private void rebuildQueueForWorld() {
        pending = new ArrayList<>(schematic.blocks()); placedCount = 0; skippedCount = 0; attempts.clear(); lastAttempts.clear();
    }
    public void stop() { building = false; setStatus(schematic == null ? "No schematic loaded" : "Stopped · " + progress()); }
    public void handleDisconnect() { building = false; dimensionId = null; origin = null; setStatus(schematic == null ? "No schematic loaded" : "Paused · disconnected"); }
    public boolean isLoaded() { return schematic != null; }
    public boolean isBuilding() { return building; }
    public String status() { return status; }

    public void tick() {
        if (!building || !MediaClient.isModuleEnabled("schematic_builder")) return;
        if (client.level == null || client.player == null || client.gameMode == null) { setStatus("Paused · no world loaded"); return; }
        if (!client.level.dimension().location().toString().equals(dimensionId)) {
            building = false; setStatus("Paused · dimension changed"); notifyPlayer(status); return;
        }
        if (client.screen != null) return;
        if (pending.isEmpty()) { building = false; setStatus("Completed · " + placedCount + " placed, " + skippedCount + " unchanged/skipped"); notifyPlayer(status); return; }
        MediaConfig config = MediaConfig.get();
        long now = System.currentTimeMillis();
        long minDelay = Math.max(config.placementDelayMs, 1000L / config.blocksPerSecond);
        if (now < nextActionAt) return;
        double pauseRangeSq = config.builderPauseDistance * (double) config.builderPauseDistance;
        SchematicData.Placement chosen = null;
        BlockPos obstructed = null;
        boolean anyInRange = false, anyLoadedNear = false;
        Iterator<SchematicData.Placement> iterator = pending.iterator();
        while (iterator.hasNext()) {
            SchematicData.Placement placement = iterator.next();
            BlockPos target = origin.offset(placement.relativePos());
            boolean inRange = target.distToCenterSqr(client.player.position()) <= pauseRangeSq;
            if (inRange) anyInRange = true;
            if (!client.level.hasChunkAt(target)) continue;
            if (inRange) anyLoadedNear = true;
            BlockState actual = client.level.getBlockState(target);
            if (actual.getBlock() == placement.requiredState().getBlock()) {
                iterator.remove();
                if (lastAttempts.containsKey(target.asLong())) placedCount++; else skippedCount++;
                continue;
            }
            if (!actual.isAir() && !actual.canBeReplaced()) { if (inRange && obstructed == null) obstructed = target; continue; }
            if (!inRange || !BlockPlacer.canPlace(client, target)) continue;
            Long attemptedAt = lastAttempts.get(target.asLong());
            if (attemptedAt != null && now - attemptedAt < 700) continue;
            chosen = placement; break;
        }
        if (pending.isEmpty()) { building = false; setStatus("Completed · " + placedCount + " placed, " + skippedCount + " unchanged/skipped"); notifyPlayer(status); return; }
        if (chosen == null) {
            if (obstructed != null && anyLoadedNear) { building = false; setStatus("Paused · obstruction at " + obstructed.toShortString() + " (won't break existing blocks)"); notifyPlayer(status); }
            else if (!anyInRange) { setStatus("Paused · move closer to the remaining blocks"); nextActionAt = now + 300; }
            else { setStatus("Waiting for a supporting block / loaded chunk · " + progress()); nextActionAt = now + 300; }
            return;
        }
        BlockPos target = origin.offset(chosen.relativePos());
        Block requiredBlock = chosen.requiredState().getBlock();
        int slot = InventoryManager.prepareBlock(client, requiredBlock);
        if (slot < 0) { setStatus("Paused · missing material: " + BuiltInRegistries.BLOCK.getKey(requiredBlock)); building = false; notifyPlayer(status); return; }
        client.player.getInventory().setSelectedSlot(slot);
        boolean placed = BlockPlacer.place(client, target);
        long packed = target.asLong();
        if (placed) {
            lastAttempts.put(packed, now); attempts.merge(packed, 1, Integer::sum); nextActionAt = now + minDelay; setStatus("Placing · " + progress());
        } else {
            int retryCount = attempts.getOrDefault(packed, 0) + 1;
            attempts.put(packed, retryCount); lastAttempts.put(packed, now); nextActionAt = now + Math.max(250, minDelay);
            if (retryCount >= 5) { building = false; setStatus("Paused · couldn't place at " + target.toShortString() + " (reach/support/permissions)"); notifyPlayer(status); }
        }
    }

    private String progress() {
        if (schematic == null) return "no schematic";
        return Math.max(0, schematic.blocks().size() - pending.size()) + "/" + schematic.blocks().size() + " blocks";
    }
    private void setStatus(String value) { status = value; }
    private void notifyPlayer(String value) {
        if (MediaConfig.get().activityNotifications && client.player != null)
            client.player.displayClientMessage(Component.literal("[Media Builder] " + value).withStyle(ChatFormatting.AQUA), true);
    }
}
