package io.github.alextixu.mobarmory.forge.client;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uploaded textures a remote client received from the server (review-01 risk 4). The runtime resource pack prefers
 * these over the client's own {@code config/mobarmory/textures/}, so every player sees the server's PNG. Kept in memory
 * only; a single-player game never fills it, because its server and client share the same folder.
 */
public final class ClientTextures {
    private static final Map<String, byte[]> RECEIVED = new ConcurrentHashMap<>();

    private ClientTextures() {
    }

    public static void put(String name, byte[] png) {
        RECEIVED.put(name, png.clone());
    }

    public static Optional<byte[]> get(String name) {
        return Optional.ofNullable(RECEIVED.get(name)).map(byte[]::clone);
    }

    /**
     * Forgets everything a server sent, when the player leaves it (review-03 P2-8), so the next world uses this game's
     * own textures. What is already on screen changes at the next resource reload, which the next world's sync asks
     * for if its textures differ.
     */
    public static void clear() {
        RECEIVED.clear();
    }
}
