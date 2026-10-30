# End Countdown (Fabric 1.21.11)

A server-side event mod with a two-day countdown, temporary PvP protection, world-border expansion and End Portal frame activation.

## Requirements
- Minecraft Java Edition 1.21.11
- Fabric Loader 0.19.3 or compatible
- Fabric API 0.141.6+1.21.11
- Java 21

## Commands
- `/start` — starts the two-day event, expands the world border to 30,000,000 blocks and begins the 30-minute PvP protection period.
- `/event status` — shows the timer, PvP state and portal state.
- `/event reset` — resets the event and returns the border to 16 blocks.
- `/event scan` — queues currently loaded chunks around players for portal scanning once portals have unlocked.

Commands require operator/game-master permissions.

The server stores the start timestamp in world persistent state. The timer therefore continues while the server is offline.

## Portal scanning limitation
Minecraft worlds can be extremely large. The mod does not force-load every chunk in the world. When the timer ends, it checks loaded chunks around online players and checks additional chunks as they load later. Use `/event scan` to rescan loaded chunks around players. This avoids generating or loading a huge part of the world in one operation.

The activation code looks for a complete 12-frame End Portal ring and fills its 3x3 center with End Portal blocks. Back up your world before using any mod that changes portal blocks.

## Build
Run `gradle build` with Gradle 9.2.1 and Java 21. The server jar is written to `build/libs/end-countdown-1.0.0.jar`.

This repository's CI workflow builds the jar and publishes it as a downloadable workflow artifact.
