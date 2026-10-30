# APEX SMP Paper Plugins

Target server: Paper 1.21.11, Java 21.

This project builds two separate plugins:

- **APEX-Events.jar** — 16-block waiting border, 100,000-block event border, `/start`, 30-minute PvP delay, two-day portal countdown, boss bar, and loaded-chunk End portal activation.
- **APEX-Access.jar** — persistent allowlist. `Hannes_mini` is automatically allowed. The operator-only `/allow <username>` command adds a player to the allowlist. The separate `//op <username>` command allowlists the target and grants operator status immediately if online, or when they first join if offline.

## Install

1. Run a Paper 1.21.11 server on Java 21.
2. Put both plugin JARs in the server's `plugins` directory.
3. Start the server once so the plugins create their config folders.
4. From the server console, run `op Hannes_mini` once if the owner account is not already an operator.
5. Keep `online-mode=true` in `server.properties` so player names are authenticated.

Players do not install these plugins on their own clients.

## APEX Events commands

- `/start` — expands the world border to 100,000 blocks and starts the timers.
- `/event status` — shows timer and PvP/portal state.
- `/event reset` — resets the event and restores the 16-block waiting border.
- `/event scan` — after the two-day timer, queues currently loaded Overworld chunks for scanning.

PvP is disabled before `/start` and for 30 minutes after it. End portal activation starts after two days. To avoid force-loading a huge world, the plugin scans already-loaded Overworld chunks and chunks loaded later; it does not force-load every chunk in the world.

The event start timestamp and announcement state are saved in the APEX Events plugin config and survive restarts. When upgrading from an earlier version, the old default 30,000,000-block event border is automatically changed to 100,000 blocks; custom border values are preserved.

## APEX Access commands

- `/allow <username>` — existing server operators can add a player to the allowlist without granting OP.
- `//op <username>` — only an existing server operator can run this. The target is added to the persistent allowlist and receives operator status immediately if online, or the next time they join.

The owner `Hannes_mini` is automatically allowlisted. The allowlist is stored in the APEX Access plugin config and survives restarts.
