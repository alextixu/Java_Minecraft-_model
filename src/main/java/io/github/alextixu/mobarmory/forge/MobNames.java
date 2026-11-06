package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The display name of each mob (R4b decisions O13, Q7 A): the name in its mob file, except on a client connected to
 * another computer's server, which shows the names that server sent ({@link MobSyncChannel}).
 * <p>
 * The names from a server are kept here, apart from the {@link MobLibrary}, because in single player the client and
 * the server share one library: writing a remote server's names into it would leak them into the next single-player
 * world (the same lesson as the weapons' "restore local weapons on logout", {@code 6c01cfd}). They are dropped when the
 * player leaves.
 */
public final class MobNames {
    private static final Map<MobId, String> FROM_SERVER = new ConcurrentHashMap<>();

    private MobNames() {
    }

    /** The name to show for this mob here. */
    public static String of(MobId id, MobLibrary library) {
        String fromServer = FROM_SERVER.get(id);
        if (fromServer != null) {
            return fromServer;
        }
        return library.current().find(id).map(MobDefinition::name).orElse(id.value());
    }

    /** On a remote client: the names the server sent replace any earlier ones. */
    static void replaceFromServer(Map<MobId, String> names) {
        FROM_SERVER.clear();
        FROM_SERVER.putAll(names);
    }

    /** The player left the server: back to the names in this computer's own files. */
    static void clearFromServer() {
        FROM_SERVER.clear();
    }

    /** The names a server sent, for tests. */
    static Map<MobId, String> fromServer() {
        return Map.copyOf(FROM_SERVER);
    }
}
