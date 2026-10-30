package com.apexsmp.events;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ApexEventsPlugin extends JavaPlugin implements Listener {
    private static final long COUNTDOWN_MILLIS = 2L * 24L * 60L * 60L * 1000L;
    private static final long PVP_DELAY_MILLIS = 30L * 60L * 1000L;
    private static final double DEFAULT_WAITING_BORDER = 16.0;
    private static final double DEFAULT_EVENT_BORDER = 100_000.0;

    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();
    private final Set<Long> queuedChunks = new HashSet<>();
    private final Set<Long> scannedChunks = new HashSet<>();

    private BossBar bossBar;
    private long lastBossBarSecond = Long.MIN_VALUE;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateLegacyEventBorder();
        bossBar = Bukkit.createBossBar(
                ChatColor.DARK_PURPLE + "APEX SMP | Waiting for /start",
                BarColor.BLUE,
                BarStyle.SOLID
        );
        bossBar.setVisible(true);

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("start") != null) {
            getCommand("start").setExecutor(this::handleStart);
        }
        if (getCommand("event") != null) {
            getCommand("event").setExecutor(this::handleEvent);
        }

        long startedAt = getStartedAt();
        applyWorldBorder(startedAt > 0L ? eventBorderSize() : waitingBorderSize());

        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        if (isPortalsUnlocked(System.currentTimeMillis())) {
            queueAllLoadedChunks();
        }

        getServer().getScheduler().runTaskTimer(this, this::updateEventState, 1L, 20L);
        getServer().getScheduler().runTaskTimer(this, this::processOneQueuedChunk, 1L, 1L);

        getLogger().info("APEX Events 1.0.2 enabled for Paper 1.21.11.");
    }

    @Override
    public void onDisable() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        scanQueue.clear();
        queuedChunks.clear();
        scannedChunks.clear();
    }

    private boolean handleStart(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "Only an actual server OP can use /start.");
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /start");
            return true;
        }

        if (getStartedAt() > 0L) {
            sender.sendMessage(ChatColor.RED + "The APEX event has already started. Use /event reset first.");
            return true;
        }

        long now = System.currentTimeMillis();
        getConfig().set("event.started-at", now);
        getConfig().set("event.pvp-announced", false);
        getConfig().set("event.portals-announced", false);
        saveConfig();

        applyWorldBorder(eventBorderSize());
        lastBossBarSecond = Long.MIN_VALUE;
        updateBossBar(now);

        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "APEX SMP has begun!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "The world border has expanded. PvP unlocks in 30 minutes.");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "End portals unlock in 2 days.");
        return true;
    }

    private boolean handleEvent(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("apexevents.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage the event.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /event <status|reset|scan>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> showStatus(sender);
            case "reset" -> resetEvent(sender);
            case "scan" -> scanLoadedChunks(sender);
            default -> sender.sendMessage(ChatColor.YELLOW + "Usage: /event <status|reset|scan>");
        }
        return true;
    }

    private void showStatus(CommandSender sender) {
        long now = System.currentTimeMillis();
        long startedAt = getStartedAt();
        if (startedAt <= 0L) {
            sender.sendMessage(ChatColor.GOLD + "APEX SMP: waiting for /start");
            sender.sendMessage(ChatColor.GRAY + "World border: " + (int) waitingBorderSize() + " blocks | PvP: disabled");
            return;
        }

        long remaining = Math.max(0L, startedAt + COUNTDOWN_MILLIS - now);
        sender.sendMessage(ChatColor.GOLD + "APEX SMP event status");
        sender.sendMessage(ChatColor.YELLOW + "End portals unlock in: " + formatDuration(remaining));
        sender.sendMessage(ChatColor.YELLOW + "PvP: " + (isPvpEnabled(now) ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
        sender.sendMessage(ChatColor.YELLOW + "End portals: " + (isPortalsUnlocked(now) ? ChatColor.GREEN + "unlocked" : ChatColor.RED + "locked"));
        sender.sendMessage(ChatColor.YELLOW + "Queued chunks: " + scanQueue.size());
    }

    private void resetEvent(CommandSender sender) {
        resetForFreshWorld();
        Bukkit.broadcastMessage(ChatColor.RED + "APEX SMP event has been reset. PvP is disabled and the border is small again.");
        sender.sendMessage(ChatColor.GREEN + "Event reset. /start is ready.");
    }

    public void resetForFreshWorld() {
        getConfig().set("event.started-at", 0L);
        getConfig().set("event.pvp-announced", false);
        getConfig().set("event.portals-announced", false);
        saveConfig();

        scanQueue.clear();
        queuedChunks.clear();
        scannedChunks.clear();
        applyWorldBorder(waitingBorderSize());
        lastBossBarSecond = Long.MIN_VALUE;
        updateBossBar(System.currentTimeMillis());
    }

    private void scanLoadedChunks(CommandSender sender) {
        if (!isPortalsUnlocked(System.currentTimeMillis())) {
            sender.sendMessage(ChatColor.RED + "End portals are still locked. The two-day countdown must finish first.");
            return;
        }
        int queued = queueAllLoadedChunks();
        sender.sendMessage(ChatColor.GREEN + "Queued " + queued + " loaded Overworld chunks for portal scanning.");
    }

    private void updateEventState() {
        long now = System.currentTimeMillis();
        long startedAt = getStartedAt();

        if (startedAt > 0L) {
            if (!getConfig().getBoolean("event.pvp-announced", false) && now >= startedAt + PVP_DELAY_MILLIS) {
                getConfig().set("event.pvp-announced", true);
                saveConfig();
                Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "PvP is now enabled on APEX SMP!");
            }

            if (isPortalsUnlocked(now) && !getConfig().getBoolean("event.portals-announced", false)) {
                getConfig().set("event.portals-announced", true);
                saveConfig();
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "The two-day countdown has ended!");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "End portals are being activated in loaded Overworld chunks. New chunks will be scanned as they load.");
                queueAllLoadedChunks();
            }
        }

        long second = now / 1000L;
        if (second != lastBossBarSecond) {
            lastBossBarSecond = second;
            updateBossBar(now);
        }
    }

    private void updateBossBar(long now) {
        if (bossBar == null) {
            return;
        }

        long startedAt = getStartedAt();
        if (startedAt <= 0L) {
            bossBar.setTitle(ChatColor.DARK_PURPLE + "APEX SMP | Waiting for /start");
            bossBar.setColor(BarColor.BLUE);
            bossBar.setProgress(1.0);
            return;
        }

        long remaining = Math.max(0L, startedAt + COUNTDOWN_MILLIS - now);
        boolean pvpEnabled = isPvpEnabled(now);

        if (remaining == 0L) {
            bossBar.setTitle(ChatColor.LIGHT_PURPLE + "APEX SMP | END PORTALS UNLOCKED");
            bossBar.setColor(BarColor.GREEN);
            bossBar.setProgress(0.0);
            return;
        }

        bossBar.setTitle(ChatColor.GOLD + "APEX SMP | " + formatDuration(remaining)
                + (pvpEnabled ? ChatColor.GREEN + " | PvP ON" : ChatColor.RED + " | PvP OFF"));
        bossBar.setColor(pvpEnabled ? BarColor.PURPLE : BarColor.BLUE);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) remaining / COUNTDOWN_MILLIS)));
    }

    private void applyWorldBorder(double size) {
        World primary = getOverworld();
        if (primary == null) {
            return;
        }
        Location spawn = primary.getSpawnLocation();
        for (World world : Bukkit.getWorlds()) {
            world.getWorldBorder().setCenter(spawn.getX(), spawn.getZ());
            world.getWorldBorder().setSize(size);
        }
    }

    private World getOverworld() {
        World named = Bukkit.getWorld("world");
        if (named != null) {
            return named;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    private long getStartedAt() {
        return getConfig().getLong("event.started-at", 0L);
    }

    private boolean isPvpEnabled(long now) {
        long startedAt = getStartedAt();
        return startedAt > 0L && now >= startedAt + PVP_DELAY_MILLIS;
    }

    private boolean isPortalsUnlocked(long now) {
        long startedAt = getStartedAt();
        return startedAt > 0L && now >= startedAt + COUNTDOWN_MILLIS;
    }

    private double waitingBorderSize() {
        return getConfig().getDouble("border.waiting-size", DEFAULT_WAITING_BORDER);
    }

    private double eventBorderSize() {
        return getConfig().getDouble("border.event-size", DEFAULT_EVENT_BORDER);
    }

    private void migrateLegacyEventBorder() {
        double configuredSize = getConfig().getDouble("border.event-size", DEFAULT_EVENT_BORDER);
        if (Double.compare(configuredSize, 30_000_000.0) == 0) {
            getConfig().set("border.event-size", DEFAULT_EVENT_BORDER);
            saveConfig();
            getLogger().info("Updated the old 30,000,000-block event border setting to 100,000 blocks.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        bossBar.addPlayer(event.getPlayer());
        updateBossBar(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!isPlayerCausedDamage(event.getDamager())) {
            return;
        }
        if (!isPvpEnabled(System.currentTimeMillis())) {
            event.setCancelled(true);
        }
    }

    private boolean isPlayerCausedDamage(Entity damager) {
        if (damager instanceof Player) {
            return true;
        }
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World overworld = getOverworld();
        if (overworld != null && event.getWorld().equals(overworld)
                && isPortalsUnlocked(System.currentTimeMillis())) {
            queueChunk(event.getChunk().getX(), event.getChunk().getZ());
        }
    }

    private int queueAllLoadedChunks() {
        World overworld = getOverworld();
        if (overworld == null) {
            return 0;
        }
        int added = 0;
        for (Chunk chunk : overworld.getLoadedChunks()) {
            if (queueChunk(chunk.getX(), chunk.getZ())) {
                added++;
            }
        }
        return added;
    }

    private boolean queueChunk(int x, int z) {
        long key = chunkKey(x, z);
        if (queuedChunks.contains(key) || scannedChunks.contains(key)) {
            return false;
        }
        queuedChunks.add(key);
        scanQueue.addLast(key);
        return true;
    }

    private void processOneQueuedChunk() {
        if (scanQueue.isEmpty()) {
            return;
        }
        World world = getOverworld();
        if (world == null) {
            scanQueue.clear();
            queuedChunks.clear();
            return;
        }

        long key = scanQueue.removeFirst();
        queuedChunks.remove(key);
        int x = (int) (key >> 32);
        int z = (int) key;
        if (!world.isChunkLoaded(x, z)) {
            return;
        }

        Chunk chunk = world.getChunkAt(x, z);
        int opened = scanChunk(chunk);
        scannedChunks.add(key);
        if (opened > 0) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Activated " + opened + " End portal(s) in a loaded stronghold chunk.");
        }
    }

    private int scanChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int opened = 0;
        int startX = chunk.getX() << 4;
        int startZ = chunk.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = world.getBlockAt(startX + x, y, startZ + z);
                    if (block.getType() == Material.END_PORTAL_FRAME) {
                        for (BlockFace inward : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                            Block center = block.getRelative(inward, 2);
                            if (activatePortal(world, center)) {
                                opened++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return opened;
    }

    private boolean activatePortal(World world, Block center) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        // Never force-load neighboring chunks while checking a frame ring.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!world.isChunkLoaded((cx + dx) >> 4, (cz + dz) >> 4)) {
                    return false;
                }
            }
        }

        for (int i = -1; i <= 1; i++) {
            if (!isFrame(world, cx + i, cy, cz - 2)
                    || !isFrame(world, cx + i, cy, cz + 2)
                    || !isFrame(world, cx - 2, cy, cz + i)
                    || !isFrame(world, cx + 2, cy, cz + i)) {
                return false;
            }
        }

        boolean alreadyOpen = true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (world.getBlockAt(cx + dx, cy, cz + dz).getType() != Material.END_PORTAL) {
                    alreadyOpen = false;
                }
            }
        }
        if (alreadyOpen) {
            return false;
        }

        // Avoid replacing non-air blocks inside a stronghold frame.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Material type = world.getBlockAt(cx + dx, cy, cz + dz).getType();
                if (type != Material.AIR && type != Material.END_PORTAL) {
                    return false;
                }
            }
        }

        for (int i = -1; i <= 1; i++) {
            setFrameFacing(world, cx + i, cy, cz - 2, BlockFace.SOUTH);
            setFrameFacing(world, cx + i, cy, cz + 2, BlockFace.NORTH);
            setFrameFacing(world, cx - 2, cy, cz + i, BlockFace.EAST);
            setFrameFacing(world, cx + 2, cy, cz + i, BlockFace.WEST);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, cy, cz + dz).setType(Material.END_PORTAL, false);
            }
        }
        return true;
    }

    private boolean isFrame(World world, int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType() == Material.END_PORTAL_FRAME;
    }

    private void setFrameFacing(World world, int x, int y, int z, BlockFace facing) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getBlockData() instanceof EndPortalFrame data) {
            data.setFacing(facing);
            data.setEye(true);
            block.setBlockData(data, false);
        }
    }

    private long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainingSeconds = seconds % 60L;
        return String.format(Locale.ROOT, "%dd %02dh %02dm %02ds", days, hours, minutes, remainingSeconds);
    }
}
