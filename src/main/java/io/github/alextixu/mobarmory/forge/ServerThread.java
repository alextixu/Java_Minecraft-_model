package io.github.alextixu.mobarmory.forge;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Runs a change on the server thread and waits for the answer, for the admins the web page calls from its own threads
 * ({@link ServerWeaponAdmin}, {@link ServerMobAdmin}). Moved out of {@code ServerWeaponAdmin} for R4b without changing
 * what it does (21-r4b-mob-admin-boundary-note.md, Q2 A). The server's queue keeps running while a single-player game
 * is paused, so saving from the browser works then too (R2b B15).
 */
final class ServerThread {
    static final long TIMEOUT_SECONDS = 10;

    private ServerThread() {
    }

    /** A change; may throw whatever the caller's admin declares. */
    @FunctionalInterface
    interface Action<T> {
        T run() throws Exception;
    }

    private record Outcome<T>(T value, Exception error) {
    }

    /**
     * Runs the action on the server thread and waits up to {@value #TIMEOUT_SECONDS} seconds; directly if already there
     * or if no server is running (the reload command, game tests, unit tests).
     *
     * @throws IOException if the game does not answer in time or fails outside the action
     * @throws Exception   whatever the action threw, unchanged
     */
    static <T> T call(Action<T> action) throws Exception {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.isSameThread()) {
            return action.run();
        }
        CompletableFuture<Outcome<T>> future = server.submit(() -> {
            try {
                return new Outcome<>(action.run(), null);
            } catch (Exception e) {
                return new Outcome<>(null, e);
            }
        });
        Outcome<T> outcome;
        try {
            outcome = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("the game did not answer within " + TIMEOUT_SECONDS + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for the game");
        } catch (ExecutionException e) {
            throw new IOException("the game failed to apply the change: " + e.getCause(), e.getCause());
        }
        if (outcome.error() != null) {
            throw outcome.error();
        }
        return outcome.value();
    }
}
