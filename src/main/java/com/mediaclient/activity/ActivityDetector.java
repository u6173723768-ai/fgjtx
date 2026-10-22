package com.mediaclient.activity;

import com.mediaclient.MediaClient;
import com.mediaclient.config.MediaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActivityDetector {
    private final Minecraft client = Minecraft.getInstance();
    private Map<Long, BlockState> lastSnapshot = new HashMap<>();
    private final ArrayDeque<ActivityEvent> events = new ArrayDeque<>();
    private final Set<UUID> inRangePlayers = new HashSet<>();
    private Map<Long, BlockState> indexedContainers = new HashMap<>();
    private Map<Long, BlockState> indexedMachines = new HashMap<>();
    private int tickCounter;
    private int espScanCounter = Integer.MAX_VALUE;
    private int lastEspChunkX = Integer.MIN_VALUE;
    private int lastEspChunkZ = Integer.MIN_VALUE;
    private boolean espScanInProgress;
    private BlockPos espScanCenter;
    private int espScanRadius, espScanMinY, espScanMaxY, espScanCursor, espScanTotal;
    private Map<Long, BlockState> espBuildingContainers = new HashMap<>();
    private Map<Long, BlockState> espBuildingMachines = new HashMap<>();
    private final BlockPos.MutableBlockPos espMutablePos = new BlockPos.MutableBlockPos();
    private long lastContainerNoticeAt;

    public void tick() {
        if (client.level == null || client.player == null) { resetWorldState(); return; }
        MediaConfig config = MediaConfig.get();
        boolean detectorOn = MediaClient.isModuleEnabled("activity_detector");
        boolean highlightOn = MediaClient.isModuleEnabled("activity_esp");
        boolean playerAlertOn = MediaClient.isModuleEnabled("nearby_player_alerts");
        boolean growthWatchOn = MediaClient.isModuleEnabled("amethyst_growth_watch")
                || MediaClient.isModuleEnabled("kelp_growth_watch")
                || MediaClient.isModuleEnabled("crop_growth_watch");
        boolean espScanOn = MediaClient.isModuleEnabled("chest_esp") || MediaClient.isModuleEnabled("machine_esp");
        if (!detectorOn && !highlightOn && !playerAlertOn && !growthWatchOn && !espScanOn) {
            lastSnapshot.clear(); inRangePlayers.clear(); events.clear();
            indexedContainers = new HashMap<>(); indexedMachines = new HashMap<>();
            espBuildingContainers.clear(); espBuildingMachines.clear(); espScanInProgress = false;
            lastEspChunkX = Integer.MIN_VALUE; lastEspChunkZ = Integer.MIN_VALUE; return;
        }
        removeExpiredEvents();
        if (detectorOn || playerAlertOn) updatePlayerPresence(config);
        if (++tickCounter >= effectiveInterval(config)) {
            tickCounter = 0;
            if (detectorOn || highlightOn || growthWatchOn) scanNearbyBlocks(config);
        }
        if (espScanOn) {
            int chunkX = client.player.blockPosition().getX() >> 4;
            int chunkZ = client.player.blockPosition().getZ() >> 4;
            boolean movedChunk = chunkX != lastEspChunkX || chunkZ != lastEspChunkZ;
            if (!espScanInProgress && (movedChunk || ++espScanCounter >= Math.max(40, effectiveInterval(config) * 5))) {
                beginEspScan(config); espScanCounter = 0; lastEspChunkX = chunkX; lastEspChunkZ = chunkZ;
            }
            if (espScanInProgress) advanceEspScan(4096);
        } else {
            indexedContainers = new HashMap<>(); indexedMachines = new HashMap<>();
            espBuildingContainers.clear(); espBuildingMachines.clear(); espScanInProgress = false;
            lastEspChunkX = Integer.MIN_VALUE; lastEspChunkZ = Integer.MIN_VALUE;
        }
        if (detectorOn && Boolean.TRUE.equals(config.eventToggles.get("container_open"))) detectContainerScreen(config);
    }

    private int effectiveInterval(MediaConfig config) {
        return MediaClient.isModuleEnabled("performance_mode") || MediaClient.isModuleEnabled("adaptive_scan")
                ? Math.max(config.scanIntervalTicks, 40) : config.scanIntervalTicks;
    }

    private void updatePlayerPresence(MediaConfig config) {
        double range = Math.max(8, config.activityRadius * 2.0), rangeSq = range * range;
        Set<UUID> now = new HashSet<>();
        for (Player other : client.level.players()) {
            if (other == client.player || other.isRemoved()) continue;
            if (other.distanceToSqr(client.player) <= rangeSq) {
                now.add(other.getUUID());
                if (!inRangePlayers.contains(other.getUUID()) && Boolean.TRUE.equals(config.eventToggles.get("player_range")))
                    addEvent(other.blockPosition(), "Player entered range: " + other.getName().getString(), "player");
            }
        }
        for (UUID old : inRangePlayers) if (!now.contains(old) && Boolean.TRUE.equals(config.eventToggles.get("player_range")))
            addEvent(client.player.blockPosition(), "Player left detection range", "player");
        inRangePlayers.clear(); inRangePlayers.addAll(now);
    }

    private void scanNearbyBlocks(MediaConfig config) {
        Level level = client.level;
        BlockPos center = client.player.blockPosition();
        int radius = config.activityRadius, yRadius = Math.min(radius, 12);
        int minY = Math.max(level.getMinY(), center.getY() - yRadius);
        int maxY = Math.min(level.getMaxY() - 1, center.getY() + yRadius);
        Map<Long, BlockState> next = new HashMap<>((radius * 2 + 1) * (radius * 2 + 1) * (yRadius * 2 + 1));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++)
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++)
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) continue;
                    BlockState current = level.getBlockState(cursor);
                    long packed = cursor.asLong(); next.put(packed, current);
                    BlockState previous = lastSnapshot.get(packed);
                    if (previous != null && !previous.equals(current)) classifyChange(BlockPos.of(packed), previous, current, config);
                }
        lastSnapshot = next;
    }

    private void beginEspScan(MediaConfig config) {
        Level level = client.level;
        if (level == null || client.player == null) return;
        espScanCenter = client.player.blockPosition().immutable();
        espScanRadius = Math.min(48, Math.max(config.containerRange, config.machineRange));
        int yRadius = Math.min(16, espScanRadius);
        espScanMinY = Math.max(level.getMinY(), espScanCenter.getY() - yRadius);
        espScanMaxY = Math.min(level.getMaxY() - 1, espScanCenter.getY() + yRadius);
        int side = espScanRadius * 2 + 1;
        espScanTotal = side * side * Math.max(0, espScanMaxY - espScanMinY + 1);
        espScanCursor = 0; espBuildingContainers = new HashMap<>(); espBuildingMachines = new HashMap<>();
        espScanInProgress = espScanTotal > 0;
    }

    private void advanceEspScan(int maxBlocks) {
        Level level = client.level;
        if (!espScanInProgress || level == null || espScanCenter == null) return;
        int side = espScanRadius * 2 + 1, vertical = espScanMaxY - espScanMinY + 1;
        int end = Math.min(espScanTotal, espScanCursor + maxBlocks);
        for (; espScanCursor < end; espScanCursor++) {
            int yOffset = espScanCursor % vertical, zOffset = (espScanCursor / vertical) % side;
            int xOffset = espScanCursor / (vertical * side);
            espMutablePos.set(espScanCenter.getX() - espScanRadius + xOffset, espScanMinY + yOffset,
                    espScanCenter.getZ() - espScanRadius + zOffset);
            if (!level.hasChunkAt(espMutablePos)) continue;
            BlockState state = level.getBlockState(espMutablePos); long packed = espMutablePos.asLong();
            if (isContainer(state)) espBuildingContainers.put(packed, state);
            if (isMachine(state)) espBuildingMachines.put(packed, state);
        }
        if (espScanCursor >= espScanTotal) {
            indexedContainers = espBuildingContainers; indexedMachines = espBuildingMachines;
            espBuildingContainers = new HashMap<>(); espBuildingMachines = new HashMap<>(); espScanInProgress = false;
        }
    }

    private void classifyChange(BlockPos pos, BlockState before, BlockState after, MediaConfig config) {
        boolean detectorOn = MediaClient.isModuleEnabled("activity_detector");
        boolean highlightOn = MediaClient.isModuleEnabled("activity_esp");
        boolean amethystWatch = MediaClient.isModuleEnabled("amethyst_growth_watch");
        boolean kelpWatch = MediaClient.isModuleEnabled("kelp_growth_watch");
        boolean cropWatch = MediaClient.isModuleEnabled("crop_growth_watch");
        if (!detectorOn && !highlightOn && !amethystWatch && !kelpWatch && !cropWatch) return;
        Block oldBlock = before.getBlock(), newBlock = after.getBlock();
        String oldId = blockId(oldBlock), newId = blockId(newBlock), event = null, eventKey = null;
        if (amethystWatch && Boolean.TRUE.equals(config.eventToggles.get("amethyst_growth")) && isAmethystGrowth(before, after)) {
            event = "Amethyst bud grew"; eventKey = "amethyst_growth";
        } else if (kelpWatch && Boolean.TRUE.equals(config.eventToggles.get("kelp_growth")) && isKelpGrowth(pos, before, after)) {
            event = "Kelp grew"; eventKey = "kelp_growth";
        } else if (cropWatch && Boolean.TRUE.equals(config.eventToggles.get("crop_growth")) && isAgeIncrease(before, after)) {
            event = "Crop grew"; eventKey = "crop_growth";
        } else if (detectorOn && Boolean.TRUE.equals(config.eventToggles.get("container_open")) && oldBlock == newBlock && isContainer(before) && propertyChanged(before, after, "open")) {
            event = "Container open state changed"; eventKey = "container_open";
        } else if (detectorOn && Boolean.TRUE.equals(config.eventToggles.get("machine_activity")) && (isMachine(before) || isMachine(after))) {
            event = "Machine state changed"; eventKey = "machine_activity";
        } else if (detectorOn && Boolean.TRUE.equals(config.eventToggles.get("door_activity")) && (oldId.contains("door") || oldId.contains("trapdoor") || newId.contains("door") || newId.contains("trapdoor"))) {
            event = "Door/trapdoor changed"; eventKey = "door_activity";
        } else if (detectorOn && Boolean.TRUE.equals(config.eventToggles.get("block_place_break")) && before.isAir() != after.isAir()) {
            event = after.isAir() ? "Block removed" : "Block placed/updated"; eventKey = "block_place_break";
        }
        if (event != null && Boolean.TRUE.equals(config.eventToggles.get(eventKey))) {
            String id = after.isAir() ? oldId : newId;
            addEvent(pos, event + " · " + id, id);
        }
    }

    private void detectContainerScreen(MediaConfig config) {
        if (!(client.screen instanceof AbstractContainerScreen<?>) || client.player.containerMenu == client.player.inventoryMenu) return;
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos(); BlockState state = client.level.getBlockState(pos);
        if (!isContainer(state)) return;
        long now = System.currentTimeMillis();
        if (now - lastContainerNoticeAt < 900) return;
        lastContainerNoticeAt = now;
        addEvent(pos, "Container opened · " + blockId(state.getBlock()), blockId(state.getBlock()));
    }

    private void addEvent(BlockPos pos, String label, String blockId) {
        MediaConfig config = MediaConfig.get();
        int max = config.maxTrackedEvents;
        while (events.size() >= max) events.removeFirst();
        long expires = System.currentTimeMillis() + config.highlightDurationSeconds * 1000L;
        events.addLast(new ActivityEvent(pos.immutable(), label, blockId, expires));
        if (MediaClient.isModuleEnabled("notifications") && config.activityNotifications && config.highlightNotifications && client.player != null)
            client.player.displayClientMessage(Component.literal("[Media] " + label).withStyle(ChatFormatting.AQUA), true);
    }

    private void removeExpiredEvents() { long now = System.currentTimeMillis(); events.removeIf(e -> e.expired(now)); }
    public java.util.List<ActivityEvent> currentEvents() { removeExpiredEvents(); return new ArrayList<>(events); }
    public void clearEvents() { events.clear(); }
    // These maps are swapped only when a background-style scan finishes, so the renderer
    // can read the finished snapshot directly without allocating a full copy every frame.
    public Map<Long, BlockState> indexedContainers() { return indexedContainers; }
    public Map<Long, BlockState> indexedMachines() { return indexedMachines; }

    public void resetWorldState() {
        lastSnapshot.clear(); indexedContainers.clear(); indexedMachines.clear(); inRangePlayers.clear(); events.clear();
        tickCounter = 0; espScanCounter = Integer.MAX_VALUE; lastEspChunkX = Integer.MIN_VALUE; lastEspChunkZ = Integer.MIN_VALUE;
        espScanInProgress = false; espScanCenter = null; espBuildingContainers.clear(); espBuildingMachines.clear();
    }

    public static boolean isContainer(BlockState state) {
        String id = blockId(state.getBlock()); MediaConfig config = MediaConfig.get();
        if (id.endsWith("shulker_box")) return Boolean.TRUE.equals(config.containerBlockToggles.get("shulker_box"));
        return Boolean.TRUE.equals(config.containerBlockToggles.get(id));
    }

    public static boolean isContainerId(String id) {
        return id.equals("chest") || id.equals("trapped_chest") || id.equals("ender_chest") || id.equals("barrel") || id.endsWith("shulker_box")
                || id.equals("furnace") || id.equals("blast_furnace") || id.equals("smoker") || id.equals("brewing_stand")
                || id.equals("dispenser") || id.equals("dropper") || id.equals("hopper") || id.equals("crafter")
                || id.equals("chiseled_bookshelf") || id.equals("lectern") || id.equals("decorated_pot");
    }

    public static boolean isMachine(BlockState state) {
        String id = blockId(state.getBlock()); MediaConfig config = MediaConfig.get();
        if (!Boolean.TRUE.equals(config.machineBlockToggles.get(id))) return false;
        return id.equals("piston") || id.equals("sticky_piston") || id.equals("dispenser") || id.equals("dropper") || id.equals("hopper") || id.equals("observer") || id.equals("crafter") || id.equals("redstone_block") || id.equals("redstone_torch") || id.equals("redstone_wall_torch") || id.equals("repeater") || id.equals("comparator") || id.equals("redstone_wire") || id.equals("redstone_lamp") || id.equals("lever") || id.equals("stone_button");
    }

    private static boolean propertyChanged(BlockState before, BlockState after, String propertyName) {
        for (Property<?> property : before.getProperties()) if (property.getName().equals(propertyName) && after.hasProperty(property))
            return !java.util.Objects.equals(before.getValues().get(property), after.getValues().get(property));
        return false;
    }

    private boolean isKelpGrowth(BlockPos pos, BlockState before, BlockState after) {
        if (!before.isAir()) return false;
        String afterId = blockId(after.getBlock());
        if (!afterId.equals("kelp") && !afterId.equals("kelp_plant")) return false;
        if (client.level == null) return false;
        String belowId = blockId(client.level.getBlockState(pos.below()).getBlock());
        return belowId.equals("kelp") || belowId.equals("kelp_plant");
    }

    private static boolean isAmethystGrowth(BlockState before, BlockState after) {
        int oldStage = amethystStage(before.getBlock());
        int newStage = amethystStage(after.getBlock());
        return oldStage > 0 && newStage > oldStage;
    }

    private static int amethystStage(Block block) {
        if (block == Blocks.SMALL_AMETHYST_BUD) return 1;
        if (block == Blocks.MEDIUM_AMETHYST_BUD) return 2;
        if (block == Blocks.LARGE_AMETHYST_BUD) return 3;
        if (block == Blocks.AMETHYST_CLUSTER) return 4;
        return 0;
    }

    private static boolean isAgeIncrease(BlockState before, BlockState after) {
        if (before.getBlock() != after.getBlock() || before.equals(after)) return false;
        Integer oldAge = ageValue(before), newAge = ageValue(after);
        return oldAge != null && newAge != null && newAge > oldAge;
    }

    private static Integer ageValue(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (!property.getName().equals("age")) continue;
            Object value = state.getValues().get(property);
            if (value instanceof Number number) return number.intValue();
        }
        return null;
    }

    public static boolean isAmethyst(Block block) {
        return block == Blocks.BUDDING_AMETHYST || block == Blocks.AMETHYST_BLOCK || block == Blocks.AMETHYST_CLUSTER || block == Blocks.SMALL_AMETHYST_BUD || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.LARGE_AMETHYST_BUD;
    }
    public static boolean hasAgeProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) if (property.getName().equals("age")) return true;
        return false;
    }
    private static String blockId(Block block) { return BuiltInRegistries.BLOCK.getKey(block).getPath(); }
}
