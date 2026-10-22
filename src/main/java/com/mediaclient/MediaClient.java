package com.mediaclient;

import com.mediaclient.activity.ActivityDetector;
import com.mediaclient.config.MediaConfig;
import com.mediaclient.esp.WorldOverlayRenderer;
import com.mediaclient.gui.MediaClickScreen;
import com.mediaclient.module.ClientModule;
import com.mediaclient.module.ModuleManager;
import com.mediaclient.schematic.SchematicBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class MediaClient implements ClientModInitializer {
    public static MediaConfig CONFIG;
    public static ModuleManager MODULES;
    public static ActivityDetector ACTIVITY_DETECTOR;
    public static SchematicBuilder SCHEMATIC_BUILDER;
    private static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        CONFIG = MediaConfig.get();
        MODULES = new ModuleManager();
        ACTIVITY_DETECTOR = new ActivityDetector();
        SCHEMATIC_BUILDER = new SchematicBuilder();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("media_client", "main"));
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.media_client.open_gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        WorldOverlayRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(MediaClient::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (ACTIVITY_DETECTOR != null) ACTIVITY_DETECTOR.resetWorldState();
            if (SCHEMATIC_BUILDER != null) SCHEMATIC_BUILDER.handleDisconnect();
        });
    }

    private static void onClientTick(Minecraft client) {
        while (openGuiKey.consumeClick()) {
            if (client.screen instanceof MediaClickScreen) client.setScreen(null);
            else client.setScreen(new MediaClickScreen());
        }
        if (ACTIVITY_DETECTOR != null) ACTIVITY_DETECTOR.tick();
        if (SCHEMATIC_BUILDER != null) SCHEMATIC_BUILDER.tick();
    }

    public static boolean isModuleEnabled(String id) {
        if (MODULES == null) return false;
        ClientModule module = MODULES.get(id);
        return module != null && module.enabled();
    }

    public static void enableModule(String id, boolean enabled) {
        if (MODULES == null) return;
        ClientModule module = MODULES.get(id);
        if (module != null) module.setEnabled(enabled);
    }
}
