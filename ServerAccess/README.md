# Server Access

Server-side Fabric mod for Minecraft 1.21.11.

## Commands

- `/allow <username>` — permanently add a player to the allowlist.
- `/allow list` — show all allowed player names.
- `/allow remove <username>` — remove a player from the allowlist.

These commands require operator-level permission. The owner `Hannes_mini` is automatically allowlisted and cannot be removed.

The allowlist is saved in the world's persistent data and survives server restarts. Players not on the list are disconnected during login, before they enter the world.

Install the JAR in the dedicated server's `mods` folder. The server needs Fabric Loader and Fabric API for Minecraft 1.21.11; players do not need the mod installed.

For identity protection, keep the server in online mode. Offline-mode servers cannot verify that a connecting person actually owns a given Minecraft username.
