package com.apexsmp.access;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ApexAccessPlugin extends JavaPlugin implements Listener {
    private static final String DEFAULT_ALLOWED_PLAYER = "Hannes_mini";

    // Reads from the async pre-login thread, so use a concurrent set.
    private final Set<String> allowedNames = ConcurrentHashMap.newKeySet();
    // Only accessed from the server thread.
    private final Set<String> pendingOps = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadAccessLists();
        ensureOwnerAllowed();

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("APEX Access enabled. " + allowedNames.size() + " player(s) are allowlisted.");
        getLogger().info("Only existing server operators can use //op <username>.");
    }

    private void loadAccessLists() {
        allowedNames.clear();
        for (String name : getConfig().getStringList("allowed-players")) {
            if (isValidUsername(name)) {
                allowedNames.add(normalize(name));
            }
        }

        pendingOps.clear();
        for (String name : getConfig().getStringList("pending-ops")) {
            if (isValidUsername(name)) {
                pendingOps.add(normalize(name));
            }
        }
    }

    private void ensureOwnerAllowed() {
        if (allowedNames.add(normalize(DEFAULT_ALLOWED_PLAYER))) {
            List<String> names = new ArrayList<>(getConfig().getStringList("allowed-players"));
            names.add(DEFAULT_ALLOWED_PLAYER);
            getConfig().set("allowed-players", names);
            saveConfig();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String normalized = normalize(event.getName());
        if (!allowedNames.contains(normalized)) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    ChatColor.RED + "You are not allowlisted on APEX SMP. Ask an operator to add you."
            );
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String normalized = normalize(player.getName());

        if (pendingOps.remove(normalized)) {
            player.setOp(true);
            persistPendingOps();
            getLogger().info("Granted operator status to " + player.getName() + " after their first allowed join.");
            Bukkit.broadcastMessage(ChatColor.GOLD + player.getName() + " has joined the APEX SMP staff.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        if (!message.equalsIgnoreCase("//op") && !message.regionMatches(true, 0, "//op ", 0, 5)) {
            return;
        }

        // Consume the custom command so it cannot fall through to another command handler.
        event.setCancelled(true);

        Player sender = event.getPlayer();
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Only an existing server operator can use //op.");
            return;
        }

        String[] parts = message.split("\\s+");
        if (parts.length != 2 || !parts[0].equalsIgnoreCase("//op")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: //op <username>");
            return;
        }

        String targetName = parts[1];
        if (!isValidUsername(targetName)) {
            sender.sendMessage(ChatColor.RED + "Invalid Minecraft username.");
            return;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            allowAndPromoteOnline(sender, onlineTarget);
        } else {
            allowAndQueuePromotion(sender, targetName);
        }
    }

    private void allowAndPromoteOnline(Player sender, Player target) {
        addAllowed(target.getName());
        pendingOps.remove(normalize(target.getName()));
        target.setOp(true);
        persistPendingOps();

        sender.sendMessage(ChatColor.GREEN + "Allowed " + target.getName() + " and granted operator status.");
        target.sendMessage(ChatColor.GOLD + "You have been allowlisted and made an APEX SMP operator.");
    }

    private void allowAndQueuePromotion(Player sender, String targetName) {
        boolean newlyAllowed = addAllowed(targetName);
        pendingOps.add(normalize(targetName));
        persistPendingOps();

        if (newlyAllowed) {
            sender.sendMessage(ChatColor.GREEN + "Added " + targetName + " to the APEX SMP allowlist.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + targetName + " was already allowlisted.");
        }
        sender.sendMessage(ChatColor.YELLOW + targetName + " will receive operator status when they join.");
    }

    private boolean addAllowed(String name) {
        String normalized = normalize(name);
        boolean added = allowedNames.add(normalized);
        if (added) {
            List<String> names = new ArrayList<>(getConfig().getStringList("allowed-players"));
            names.add(name);
            getConfig().set("allowed-players", names);
            saveConfig();
        }
        return added;
    }

    private void persistPendingOps() {
        List<String> names = new ArrayList<>(pendingOps);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        getConfig().set("pending-ops", names);
        saveConfig();
    }

    private boolean isValidUsername(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
