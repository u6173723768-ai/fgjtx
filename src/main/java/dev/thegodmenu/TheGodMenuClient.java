package dev.thegodmenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class TheGodMenuClient implements ClientModInitializer {
    public enum Category {
        MOVEMENT("Movement"),
        VISUAL("Visual"),
        HUD("HUD"),
        WORLD("World"),
        MISC("Misc");

        public final String label;
        Category(String label) { this.label = label; }
    }

    public static final class Module {
        public final String id;
        public final String name;
        public final String description;
        public final Category category;
        public final boolean premiumOnly;
        private boolean enabled;

        private Module(String id, String name, String description, Category category, boolean premiumOnly) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.premiumOnly = premiumOnly;
        }

        public boolean isEnabled() { return enabled; }
        public boolean isLocked() { return premiumOnly && !premium; }
    }

    private static final List<Module> MODULES = new ArrayList<>();
    private static boolean premium;
    private static KeyMapping openMenuKey;
    private static Double previousGamma;
    private static double fallStartY = Double.NaN;
    private static double currentFallDistance;
    private static String edition = "Free";

    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    static {
        add("auto_sprint", "Auto Sprint", "Automatically sprint while moving forward.", Category.MOVEMENT, false);
        add("auto_jump", "Auto Jump", "Jump when moving forward and grounded.", Category.MOVEMENT, false);

        add("fullbright", "Fullbright", "Brighten the game using your client gamma setting.", Category.VISUAL, false);
        add("cursor_trail", "Cursor Smoke", "Show a soft particle trail while the menu is open.", Category.VISUAL, false);

        add("coordinates", "Coordinates HUD", "Display your current X, Y and Z position.", Category.HUD, false);
        add("fps", "FPS HUD", "Display your current frames per second.", Category.HUD, false);
        add("fall_distance", "Fall Height HUD", "Track your current downward fall distance.", Category.HUD, false);

        add("chunk", "Chunk HUD", "Display the chunk coordinates you are standing in.", Category.WORLD, true);
        add("velocity", "Velocity HUD", "Display your horizontal movement speed.", Category.HUD, true);
        add("direction", "Direction HUD", "Display the direction you are facing.", Category.HUD, true);
        add("clock", "Clock HUD", "Display your local computer time.", Category.HUD, true);
        add("ping", "Ping HUD", "Display your current server latency when available.", Category.HUD, true);
        add("watermark", "Watermark HUD", "Show The GOD MENU branding on screen.", Category.MISC, true);
        add("compact_hud", "Compact HUD", "Use a tighter layout for enabled HUD lines.", Category.MISC, true);
    }

    private static void add(String id, String name, String description, Category category, boolean premiumOnly) {
        MODULES.add(new Module(id, name, description, category, premiumOnly));
    }

    @Override
    public void onInitializeClient() {
        premium = readEdition();
        edition = premium ? "Premium" : "Free";
        loadConfig();

        KeyMapping.Category keyCategory =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("thegodmenu", "main"));

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.thegodmenu.open_menu",
                net.minecraft.client.input.InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                keyCategory
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.screen instanceof TheGodMenuScreen) {
                    client.setScreen(null);
                } else if (client.screen == null) {
                    client.setScreen(new TheGodMenuScreen());
                }
            }
            tickModules(client);
        });

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("thegodmenu", "module_hud"),
                (graphics, tickCounter) -> renderHud(Minecraft.getInstance(), graphics)
        );
    }

    public static String getEditionName() { return edition; }
    public static boolean isPremium() { return premium; }
    public static List<Category> getCategories() { return List.of(Category.values()); }

    public static List<Module> getModules(Category category) {
        return MODULES.stream().filter(module -> module.category == category).toList();
    }

    public static void toggle(Module module) {
        if (module == null || module.isLocked()) return;
        module.enabled = !module.enabled;
        if (!module.enabled && module.id.equals("fullbright")) {
            restoreGamma(Minecraft.getInstance());
        }
        saveConfig();
    }

    public static void setEnabled(String id, boolean enabled) {
        for (Module module : MODULES) {
            if (module.id.equals(id) && !module.isLocked()) {
                module.enabled = enabled;
                return;
            }
        }
    }

    private static boolean readEdition() {
        try (InputStream in = TheGodMenuClient.class.getResourceAsStream("/thegodmenu-edition.properties")) {
            if (in == null) return false;
            Properties properties = new Properties();
            properties.load(in);
            return "premium".equalsIgnoreCase(properties.getProperty("edition", "free").trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("thegodmenu.properties");
    }

    private static void loadConfig() {
        Properties properties = new Properties();
        Path path = configPath();
        if (!Files.exists(path)) return;

        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
            for (Module module : MODULES) {
                if (module.isLocked()) continue;
                module.enabled = Boolean.parseBoolean(properties.getProperty(module.id, "false"));
            }
        } catch (IOException exception) {
            System.err.println("[The GOD MENU] Could not load config: " + exception.getMessage());
        }
    }

    private static void saveConfig() {
        Properties properties = new Properties();
        for (Module module : MODULES) {
            properties.setProperty(module.id, Boolean.toString(module.enabled));
        }

        try {
            Files.createDirectories(configPath().getParent());
            try (OutputStream out = Files.newOutputStream(configPath())) {
                properties.store(out, "The GOD MENU module settings");
            }
        } catch (IOException exception) {
            System.err.println("[The GOD MENU] Could not save config: " + exception.getMessage());
        }
    }

    private static boolean enabled(String id) {
        return MODULES.stream().anyMatch(module -> module.id.equals(id) && module.enabled && !module.isLocked());
    }

    private static void tickModules(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (previousGamma != null) restoreGamma(client);
            fallStartY = Double.NaN;
            currentFallDistance = 0;
            return;
        }

        if (enabled("fullbright")) {
            if (previousGamma == null) previousGamma = client.options.gamma().get();
            client.options.gamma().set(16.0);
        } else if (previousGamma != null) {
            restoreGamma(client);
        }

        boolean movingForward = client.options.keyUp.isDown();
        if (enabled("auto_sprint")) {
            client.player.setSprinting(movingForward && !client.player.isCrouching());
        }

        if (enabled("auto_jump") && movingForward && client.player.onGround() && !client.player.isPassenger()) {
            client.player.jump();
        }

        if (client.player.onGround()) {
            fallStartY = Double.NaN;
            currentFallDistance = 0;
        } else if (client.player.getDeltaMovement().y < 0) {
            if (Double.isNaN(fallStartY)) fallStartY = client.player.getY();
            currentFallDistance = Math.max(currentFallDistance, fallStartY - client.player.getY());
        }
    }

    private static void restoreGamma(Minecraft client) {
        if (previousGamma == null) return;
        try {
            client.options.gamma().set(previousGamma);
        } catch (Exception ignored) {
        }
        previousGamma = null;
    }

    private static void renderHud(Minecraft client, GuiGraphics graphics) {
        if (client.player == null || client.level == null || client.options.hideGui) return;

        int x = 8;
        int y = 8;
        int lineHeight = enabled("compact_hud") ? 11 : 13;
        int textColor = 0xFFF2F0FF;
        int shadowColor = 0xB5000000;

        if (enabled("watermark")) {
            graphics.drawString(client.font, "THE GOD MENU  •  " + edition.toUpperCase(Locale.ROOT), x, y, 0xFFFF8B4A, true);
            y += lineHeight + 2;
        }
        if (enabled("coordinates")) {
            graphics.drawString(client.font, String.format(Locale.ROOT, "XYZ  %.1f  %.1f  %.1f",
                    client.player.getX(), client.player.getY(), client.player.getZ()), x, y, textColor, true);
            y += lineHeight;
        }
        if (enabled("fps")) {
            graphics.drawString(client.font, "FPS  " + client.getFps(), x, y, textColor, true);
            y += lineHeight;
        }
        if (enabled("fall_distance")) {
            graphics.drawString(client.font, String.format(Locale.ROOT, "FALL  %.1f blocks", currentFallDistance), x, y, textColor, true);
            y += lineHeight;
        }
        if (enabled("chunk")) {
            graphics.drawString(client.font, "CHUNK  " + Math.floorDiv(client.player.getBlockX(), 16)
                    + " / " + Math.floorDiv(client.player.getBlockZ(), 16), x, y, textColor, true);
            y += lineHeight;
        }
        if (enabled("velocity")) {
            var velocity = client.player.getDeltaMovement();
            double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
            graphics.drawString(client.font, String.format(Locale.ROOT, "SPEED  %.2f blocks/s", speed), x, y, textColor, true);
            y += lineHeight;
        }
        if (enabled("direction")) {
            int facing = Math.floorMod(Math.round(client.player.getYRot() / 90.0f), 4);
            String[] directions = {"S", "W", "N", "E"};
            graphics.drawString(client.font, "FACING  " + directions[facing], x, y, textColor, true);
            y += lineHeight;
        }
        if (enabled("clock")) {
            graphics.drawString(client.font, "TIME  " + LocalTime.now().format(CLOCK_FORMAT), x, y, textColor, true);
            y += lineHeight;
        }
        if (enabled("ping") && client.getConnection() != null) {
            var info = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info != null) {
                graphics.drawString(client.font, "PING  " + info.getLatency() + " ms", x, y, textColor, true);
                y += lineHeight;
            }
        }

        if (y > 8) {
            graphics.fill(4, 4, 170, y + 3, shadowColor);
        }
    }
}
