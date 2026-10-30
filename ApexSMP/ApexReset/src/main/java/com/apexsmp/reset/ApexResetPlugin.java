package com.apexsmp.reset;

import com.apexsmp.access.ApexAccessPlugin;
import com.apexsmp.events.ApexEventsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;

public final class ApexResetPlugin extends JavaPlugin implements Listener {
    private boolean resetInProgress;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("APEX Reset enabled. Only an actual server OP can use /restart.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!event.getMessage().trim().equalsIgnoreCase("/restart")) {
            return;
        }

        event.setCancelled(true);
        Player sender = event.getPlayer();

        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Only an actual server OP can use /restart.");
            return;
        }

        if (resetInProgress) {
            sender.sendMessage(ChatColor.RED + "An APEX SMP reset is already in progress.");
            return;
        }

        ApexAccessPlugin access = getPlugin(ApexAccessPlugin.class, "APEXAccess");
        ApexEventsPlugin events = getPlugin(ApexEventsPlugin.class, "APEXEvents");
        if (access == null || events == null) {
            sender.sendMessage(ChatColor.RED + "APEX Access or APEX Events is missing, so reset was cancelled.");
            return;
        }

        try {
            prepareFreshWorldName();
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Could not prepare the fresh world: " + ex.getMessage());
            getLogger().severe("Fresh-world setup failed: " + ex.getMessage());
            return;
        }

        resetInProgress = true;
        access.resetAllowlistForFreshWorld();
        events.resetForFreshWorld();

        Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "APEX SMP is starting a new season.");
        Bukkit.broadcastMessage(ChatColor.RED + "Player/world progress will reset. Hannes_mini and Space780 stay allowlisted.");

        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                player.kickPlayer(ChatColor.RED + "APEX SMP is restarting into a fresh world. Rejoin after restart.");
            }

            boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
            if (!dispatched) {
                Bukkit.shutdown();
            }
        }, 20L);
    }

    private void prepareFreshWorldName() throws Exception {
        Path propertiesPath = Path.of("server.properties").toAbsolutePath().normalize();
        Properties properties = new Properties();

        if (Files.exists(propertiesPath)) {
            try (InputStream input = Files.newInputStream(propertiesPath)) {
                properties.load(input);
            }
        }

        String newWorldName = "apex_world_" + System.currentTimeMillis();
        properties.setProperty("level-name", newWorldName);
        properties.setProperty("level-seed", "");

        try (OutputStream output = Files.newOutputStream(propertiesPath)) {
            properties.store(output, "Updated automatically by APEX Reset");
        }

        getLogger().warning("Next APEX world set to: " + newWorldName);
    }

    private <T extends Plugin> T getPlugin(Class<T> type, String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return type.isInstance(plugin) ? type.cast(plugin) : null;
    }
}
