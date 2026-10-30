package dev.serveraccess;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AllowedPlayersState extends PersistentState {
    private static final Codec<AllowedPlayersState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf()
                    .optionalFieldOf("allowed_players", List.of(AccessControlMod.DEFAULT_ALLOWED_PLAYER))
                    .forGetter(AllowedPlayersState::getAllowedNames)
    ).apply(instance, AllowedPlayersState::new));

    public static final PersistentStateType<AllowedPlayersState> TYPE = new PersistentStateType<>(
            "server_access_allowlist",
            AllowedPlayersState::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private final Map<String, String> allowedPlayers = new LinkedHashMap<>();

    public AllowedPlayersState() {
        this(List.of(AccessControlMod.DEFAULT_ALLOWED_PLAYER));
    }

    private AllowedPlayersState(List<String> names) {
        for (String name : names) {
            if (isValidUsername(name)) {
                allowedPlayers.put(normalize(name), name);
            }
        }
        allowedPlayers.putIfAbsent(normalize(AccessControlMod.DEFAULT_ALLOWED_PLAYER),
                AccessControlMod.DEFAULT_ALLOWED_PLAYER);
    }

    public boolean allow(String username) {
        String key = normalize(username);
        if (allowedPlayers.containsKey(key)) {
            return false;
        }
        allowedPlayers.put(key, username);
        markDirty();
        return true;
    }

    public boolean remove(String username) {
        if (username.equalsIgnoreCase(AccessControlMod.DEFAULT_ALLOWED_PLAYER)) {
            return false;
        }
        String removed = allowedPlayers.remove(normalize(username));
        if (removed != null) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean isAllowed(String username) {
        return username != null && allowedPlayers.containsKey(normalize(username));
    }

    public List<String> getAllowedNames() {
        List<String> names = new ArrayList<>(allowedPlayers.values());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static boolean isValidUsername(String username) {
        return username != null && username.matches("[A-Za-z0-9_]{1,16}");
    }
}
