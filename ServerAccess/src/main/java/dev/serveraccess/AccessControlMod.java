package dev.serveraccess;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class AccessControlMod implements ModInitializer {
    public static final String DEFAULT_ALLOWED_PLAYER = "Hannes_mini";

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Ensure the owner stays allowed, including when loading an older allowlist file.
            state(server).allow(DEFAULT_ALLOWED_PLAYER);
        });

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            // The access widener exposes the authenticated GameProfile on the login handler.
            GameProfile profile = handler.profile;
            String username = profile == null ? null : profile.name();

            if (username == null || !state(server).isAllowed(username)) {
                String attemptedName = username == null ? "Unknown player" : username;
                handler.disconnect(Text.literal(
                        "You are not allowlisted on this server. Ask the owner to add you with /allow <username>."
                ));
                server.sendMessage(Text.literal("Rejected login for " + attemptedName + ": not allowlisted."));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("allow")
                .requires(AccessControlMod::isAdmin)
                .then(CommandManager.argument("username", StringArgumentType.word())
                        .executes(context -> allow(
                                context.getSource(),
                                StringArgumentType.getString(context, "username")
                        )))
                .then(CommandManager.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("username", StringArgumentType.word())
                                .executes(context -> remove(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "username")
                                ))))
        );
    }

    private static boolean isAdmin(ServerCommandSource source) {
        return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS));
    }

    private static int allow(ServerCommandSource source, String username) {
        if (!isValidUsername(username)) {
            source.sendError(Text.literal("Invalid username. Minecraft usernames must be 1-16 letters, numbers, or underscores."));
            return 0;
        }

        AllowedPlayersState allowlist = state(source.getServer());
        if (!allowlist.allow(username)) {
            source.sendFeedback(() -> Text.literal(username + " is already allowlisted."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("Allowed " + username + ". They can now join the server."), true);
        return 1;
    }

    private static int list(ServerCommandSource source) {
        var names = state(source.getServer()).getAllowedNames();
        source.sendFeedback(() -> Text.literal(
                "Allowed players (" + names.size() + "): " + String.join(", ", names)
        ), false);
        return 1;
    }

    private static int remove(ServerCommandSource source, String username) {
        if (!isValidUsername(username)) {
            source.sendError(Text.literal("Invalid username."));
            return 0;
        }

        if (username.equalsIgnoreCase(DEFAULT_ALLOWED_PLAYER)) {
            source.sendError(Text.literal(DEFAULT_ALLOWED_PLAYER + " is the protected server owner and cannot be removed."));
            return 0;
        }

        if (!state(source.getServer()).remove(username)) {
            source.sendError(Text.literal(username + " is not on the allowlist."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Removed " + username + " from the allowlist."), true);
        return 1;
    }

    private static boolean isValidUsername(String username) {
        return username != null && username.matches("[A-Za-z0-9_]{1,16}");
    }

    private static AllowedPlayersState state(MinecraftServer server) {
        AllowedPlayersState allowlist =
                server.getOverworld().getPersistentStateManager().getOrCreate(AllowedPlayersState.TYPE);
        allowlist.allow(DEFAULT_ALLOWED_PLAYER);
        return allowlist;
    }
}
