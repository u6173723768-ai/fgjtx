package dev.worldevent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class EventState extends PersistentState {
    public static final long COUNTDOWN = 2L * 24L * 60L * 60L * 1000L;
    public static final long PVP_DELAY = 30L * 60L * 1000L;

    private static final Codec<EventState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("started_at", 0L).forGetter(state -> state.startedAt),
            Codec.BOOL.optionalFieldOf("pvp_announced", false).forGetter(state -> state.pvpAnnounced),
            Codec.BOOL.optionalFieldOf("portals_announced", false).forGetter(state -> state.portalsAnnounced)
    ).apply(instance, EventState::new));

    public static final PersistentStateType<EventState> TYPE = new PersistentStateType<>(
            "end_countdown_event", EventState::new, CODEC, DataFixTypes.LEVEL
    );

    private long startedAt;
    private boolean pvpAnnounced;
    private boolean portalsAnnounced;

    public EventState() {
        this(0L, false, false);
    }

    private EventState(long startedAt, boolean pvpAnnounced, boolean portalsAnnounced) {
        this.startedAt = Math.max(0L, startedAt);
        this.pvpAnnounced = pvpAnnounced;
        this.portalsAnnounced = portalsAnnounced;
    }

    public boolean started() {
        return startedAt > 0L;
    }

    public long endAt() {
        return started() ? startedAt + COUNTDOWN : 0L;
    }

    public long pvpAt() {
        return started() ? startedAt + PVP_DELAY : 0L;
    }

    public boolean pvpEnabled(long now) {
        return started() && now >= pvpAt();
    }

    public boolean portalsUnlocked(long now) {
        return started() && now >= endAt();
    }

    public boolean pvpAnnounced() {
        return pvpAnnounced;
    }

    public boolean portalsAnnounced() {
        return portalsAnnounced;
    }

    public void start(long now) {
        startedAt = now;
        pvpAnnounced = false;
        portalsAnnounced = false;
        markDirty();
    }

    public void markPvpAnnounced() {
        pvpAnnounced = true;
        markDirty();
    }

    public void markPortalsAnnounced() {
        portalsAnnounced = true;
        markDirty();
    }

    public void reset() {
        startedAt = 0L;
        pvpAnnounced = false;
        portalsAnnounced = false;
        markDirty();
    }
}
