package com.apexsmp.maceblade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.List;

public final class ApexMaceBladePlugin extends JavaPlugin implements Listener {
    private NamespacedKey itemKey;
    private NamespacedKey recipeKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        itemKey = new NamespacedKey(this, "the_mace_blade");
        recipeKey = new NamespacedKey(this, "the_mace_blade_recipe");

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("maceblade") == null) {
            throw new IllegalStateException("/maceblade is missing from plugin.yml");
        }
        getCommand("maceblade").setExecutor(this::handleCommand);

        if (getConfig().getBoolean("crafting.enabled", true)) {
            registerRecipe();
        }

        getLogger().info("APEX Mace Blade 1.0.0 enabled.");
    }

    private ItemStack createMaceBlade() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(
                Component.text("the mace blade", NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)
        );

        meta.lore(List.of(
                Component.text("A sword with the force of a mace.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Supports Wind Burst, Density and Breach.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        meta.setItemModel(NamespacedKey.minecraft("netherite_sword"));

        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        return item;
    }

    private boolean isMaceBlade(ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) {
            return false;
        }

        Byte value = item.getItemMeta()
                .getPersistentDataContainer()
                .get(itemKey, PersistentDataType.BYTE);

        return value != null && value == (byte) 1;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMaceBladeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (!isMaceBlade(attacker.getInventory().getItemInMainHand())) {
            return;
        }

        double minimumFall = getConfig().getDouble("smash.minimum-fall-distance", 1.5);
        float fallDistance = attacker.getFallDistance();

        if (attacker.isOnGround() || attacker.isGliding() || fallDistance < minimumFall) {
            return;
        }

        double perBlock = getConfig().getDouble("smash.damage-per-block", 2.5);
        double maxBonus = getConfig().getDouble("smash.maximum-bonus-damage", 40.0);
        double bonusDamage = Math.min(maxBonus, fallDistance * perBlock);

        event.setDamage(event.getDamage() + bonusDamage);

        double knockbackStrength = getConfig().getDouble("smash.knockback-strength", 1.15);
        Vector horizontal = target.getLocation().toVector()
                .subtract(attacker.getLocation().toVector())
                .setY(0);

        if (horizontal.lengthSquared() > 0.0001) {
            horizontal.normalize().multiply(knockbackStrength);
        }

        horizontal.setY(0.45);
        target.setVelocity(target.getVelocity().add(horizontal));

        target.getWorld().spawnParticle(
                Particle.EXPLOSION,
                target.getLocation().add(0, 0.8, 0),
                1
        );

        target.getWorld().playSound(
                target.getLocation(),
                Sound.ITEM_MACE_SMASH_GROUND_HEAVY,
                1.0f,
                1.0f
        );

        attacker.setFallDistance(0.0f);
    }

    private boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage(Component.text(
                    "Only an actual server OP can use /maceblade.",
                    NamedTextColor.RED
            ));
            return true;
        }

        if (args.length != 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text(
                    "Usage: /maceblade give <player>",
                    NamedTextColor.YELLOW
            ));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text(
                    "That player is not online.",
                    NamedTextColor.RED
            ));
            return true;
        }

        ItemStack blade = createMaceBlade();
        var leftovers = target.getInventory().addItem(blade);

        for (ItemStack leftover : leftovers.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        sender.sendMessage(Component.text(
                "Gave the mace blade to " + target.getName() + ".",
                NamedTextColor.GREEN
        ));

        target.sendMessage(
                Component.text("You received ", NamedTextColor.GRAY)
                        .append(Component.text("the mace blade", NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(".", NamedTextColor.GRAY))
        );

        return true;
    }

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createMaceBlade());
        recipe.shape(
                " T ",
                " H ",
                " S "
        );
        recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
        recipe.setIngredient('H', Material.HEAVY_CORE);
        recipe.setIngredient('S', Material.STICK);

        Bukkit.addRecipe(recipe);
    }
}
