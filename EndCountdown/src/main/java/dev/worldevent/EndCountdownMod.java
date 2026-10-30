package dev.worldevent;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class EndCountdownMod implements ModInitializer {
    private static final double WAITING_BORDER = 16.0;
    private static final double EVENT_BORDER = 30_000_000.0;
    private static final int SCAN_RADIUS = 4;

    private static final ServerBossBar BAR = new ServerBossBar(
            Text.literal("END EVENT | Waiting for /start"),
            BossBar.Color.PURPLE,
            BossBar.Style.PROGRESS
    );

    private static final ArrayDeque<Long> SCAN_QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static long lastDisplayedSecond = Long.MIN_VALUE;

    @Override
    public void onInitialize() {
        BAR.setVisible(true);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SCAN_QUEUE.clear();
            QUEUED.clear();
            EventState state = state(server);
            setBorder(server, state.started() ? EVENT_BORDER : WAITING_BORDER);
            updateBar(state, System.currentTimeMillis());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            BAR.addPlayer(handler.player);
            EventState state = state(server);
            long now = System.currentTimeMillis();
            updateBar(state, now);
            if (state.portalsUnlocked(now) && handler.player.getWorld() == server.getOverworld()) {
                queuePlayerArea(server.getOverworld(), handler.player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BAR.removePlayer(handler.player));

        ServerTickEvents.END_SERVER_TICK.register(EndCountdownMod::tick);

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return true;
            if (!(damageSource.getAttacker() instanceof PlayerEntity attacker) || attacker == victim) return true;
            MinecraftServer server = victim.getServer();
            return server == null || state(server).pvpEnabled(System.currentTimeMillis());
        });

        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                EventState state = state(world.getServer());
                if (state.portalsUnlocked(System.currentTimeMillis())) queueChunk(chunk.getPos());
            }
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("start")
                .requires(source -> source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                .executes(context -> start(context.getSource())));

        dispatcher.register(CommandManager.literal("event")
                .requires(source -> source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                .then(CommandManager.literal("status").executes(context -> status(context.getSource())))
                .then(CommandManager.literal("reset").executes(context -> reset(context.getSource())))
                .then(CommandManager.literal("scan").executes(context -> scan(context.getSource()))));
    }

    private static int start(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        EventState state = state(server);
        if (state.started()) {
            source.sendError(Text.literal("The event has already started. Use /event reset first."));
            return 0;
        }

        long now = System.currentTimeMillis();
        state.start(now);
        setBorder(server, EVENT_BORDER);
        lastDisplayedSecond = Long.MIN_VALUE;
        updateBar(state, now);
        server.getPlayerManager().broadcast(Text.literal(
                "The event has started! World border: 30,000,000 blocks. PvP unlocks in 30 minutes. End portals unlock in 2 days."
        ), false);
        return 1;
    }

    private static int status(ServerCommandSource source) {
        EventState state = state(source.getServer());
        long now = System.currentTimeMillis();
        if (!state.started()) {
            source.sendFeedback(() -> Text.literal("Waiting for /start | Border: 16 blocks | PvP: disabled"), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal(
                "End portals in " + format(state.endAt() - now)
                        + " | PvP: " + (state.pvpEnabled(now) ? "enabled" : "disabled")
                        + " | Portals: " + (state.portalsUnlocked(now) ? "unlocked" : "locked")
        ), false);
        return 1;
    }

    private static int reset(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        EventState state = state(server);
        state.reset();
        SCAN_QUEUE.clear();
        QUEUED.clear();
        setBorder(server, WAITING_BORDER);
        lastDisplayedSecond = Long.MIN_VALUE;
        updateBar(state, System.currentTimeMillis());
        source.sendFeedback(() -> Text.literal("Event reset. Border is 16 blocks, PvP is disabled, and /start is ready."), true);
        return 1;
    }

    private static int scan(ServerCommandSource source) {
        EventState state = state(source.getServer());
        if (!state.portalsUnlocked(System.currentTimeMillis())) {
            source.sendError(Text.literal("Portals are still locked until the two-day countdown ends."));
            return 0;
        }
        int count = queueAreasAroundPlayers(source.getServer());
        source.sendFeedback(() -> Text.literal("Queued " + count + " loaded chunks for portal scanning."), true);
        return 1;
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        EventState state = state(server);

        if (state.started()) {
            if (!state.pvpAnnounced() && now >= state.pvpAt()) {
                state.markPvpAnnounced();
                server.getPlayerManager().broadcast(Text.literal("PvP is now enabled!"), false);
            }

            if (state.portalsUnlocked(now) && !state.portalsAnnounced()) {
                state.markPortalsAnnounced();
                server.getPlayerManager().broadcast(Text.literal(
                        "The two-day countdown has ended! End portals are being activated in loaded stronghold chunks."
                ), false);
                queueAreasAroundPlayers(server);
            }
        }

        long second = now / 1000L;
        if (second != lastDisplayedSecond) {
            lastDisplayedSecond = second;
            updateBar(state, now);
        }
        processOneChunk(server);
    }

    private static void updateBar(EventState state, long now) {
        if (!state.started()) {
            BAR.setName(Text.literal("END EVENT | Waiting for /start"));
            BAR.setPercent(1.0F);
            BAR.setColor(BossBar.Color.PURPLE);
            return;
        }

        long remaining = Math.max(0L, state.endAt() - now);
        if (remaining == 0L) {
            BAR.setName(Text.literal("END EVENT | END PORTALS UNLOCKED"));
            BAR.setPercent(0.0F);
            BAR.setColor(BossBar.Color.GREEN);
            return;
        }

        String pvp = state.pvpEnabled(now) ? "PvP ON" : "PvP OFF";
        BAR.setName(Text.literal("END PORTALS IN " + format(remaining) + " | " + pvp));
        BAR.setPercent(Math.max(0.0F, Math.min(1.0F, (float) remaining / (float) EventState.COUNTDOWN)));
        BAR.setColor(state.pvpEnabled(now) ? BossBar.Color.PURPLE : BossBar.Color.BLUE);
    }

    private static String format(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        return String.format(Locale.ROOT, "%dd %02dh %02dm %02ds", days, hours, minutes, seconds % 60L);
    }

    private static EventState state(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(EventState.TYPE);
    }

    private static void setBorder(MinecraftServer server, double size) {
        BlockPos spawn = server.getOverworld().getSpawnPos();
        double x = spawn.getX() + 0.5;
        double z = spawn.getZ() + 0.5;
        for (ServerWorld world : server.getWorlds()) {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(x, z);
            border.setSize(size);
        }
    }

    private static int queueAreasAroundPlayers(MinecraftServer server) {
        int added = 0;
        ServerWorld overworld = server.getOverworld();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld() == overworld) added += queuePlayerArea(overworld, player);
        }
        return added;
    }

    private static int queuePlayerArea(ServerWorld world, ServerPlayerEntity player) {
        int added = 0;
        int cx = player.getBlockX() >> 4;
        int cz = player.getBlockZ() >> 4;
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                Chunk chunk = world.getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
                if (chunk != null && queueChunk(chunk.getPos())) added++;
            }
        }
        return added;
    }

    private static boolean queueChunk(ChunkPos pos) {
        long key = ((long) pos.x << 32) | (pos.z & 0xffffffffL);
        if (!QUEUED.add(key)) return false;
        SCAN_QUEUE.addLast(key);
        return true;
    }

    private static void processOneChunk(MinecraftServer server) {
        if (SCAN_QUEUE.isEmpty()) return;
        long key = SCAN_QUEUE.removeFirst();
        QUEUED.remove(key);
        int x = (int) (key >> 32);
        int z = (int) key;
        Chunk chunk = server.getOverworld().getChunk(x, z, ChunkStatus.FULL, false);
        if (chunk instanceof WorldChunk worldChunk) {
            int opened = scanChunk(server.getOverworld(), worldChunk);
            if (opened > 0) {
                server.getPlayerManager().broadcast(Text.literal("Activated " + opened + " End portal(s) in a loaded chunk."), false);
            }
        }
    }

    private static int scanChunk(ServerWorld world, WorldChunk chunk) {
        int count = 0;
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getBottomY(); y < world.getTopY(); y++) {
                    cursor.set(startX + x, y, startZ + z);
                    if (chunk.getBlockState(cursor).isOf(Blocks.END_PORTAL_FRAME)) {
                        if (activatePortal(world, cursor.toImmutable())) count++;
                    }
                }
            }
        }
        return count;
    }

    private static boolean activatePortal(ServerWorld world, BlockPos frame) {
        for (Direction inward : Direction.Type.HORIZONTAL) {
            BlockPos center = frame.offset(inward, 2);
            if (!completeRingLoaded(world, center)) continue;

            boolean open = true;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!world.getBlockState(center.add(dx, 0, dz)).isOf(Blocks.END_PORTAL)) open = false;
                }
            }
            if (open) return false;

            for (int i = -1; i <= 1; i++) {
                setFrame(world, center.add(i, 0, -2), Direction.SOUTH);
                setFrame(world, center.add(i, 0, 2), Direction.NORTH);
                setFrame(world, center.add(-2, 0, i), Direction.EAST);
                setFrame(world, center.add(2, 0, i), Direction.WEST);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlockState(center.add(dx, 0, dz), Blocks.END_PORTAL.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
            return true;
        }
        return false;
    }

    private static boolean completeRingLoaded(ServerWorld world, BlockPos center) {
        for (int i = -1; i <= 1; i++) {
            if (!frameAt(world, center.add(i, 0, -2))
                    || !frameAt(world, center.add(i, 0, 2))
                    || !frameAt(world, center.add(-2, 0, i))
                    || !frameAt(world, center.add(2, 0, i))) return false;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (world.getChunk(center.getX() + dx >> 4, center.getZ() + dz >> 4, ChunkStatus.FULL, false) == null) return false;
            }
        }
        return true;
    }

    private static boolean frameAt(ServerWorld world, BlockPos pos) {
        if (world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) == null) return false;
        return world.getBlockState(pos).isOf(Blocks.END_PORTAL_FRAME);
    }

    private static void setFrame(ServerWorld world, BlockPos pos, Direction facing) {
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.END_PORTAL_FRAME)) {
            world.setBlockState(pos, state.with(EndPortalFrameBlock.FACING, facing)
                    .with(EndPortalFrameBlock.EYE, true), Block.NOTIFY_ALL);
        }
    }
}
