package io.github.alextixu.mobarmory.forge.client;

import io.github.alextixu.mobarmory.forge.WeaponSyncChannel;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/**
 * Client-only wiring for weapon sync (review-03 P2-8): when the player leaves a world or a server, the client drops
 * what that server sent and goes back to its own weapon files. Only registered on the client, because
 * {@link ClientPlayerNetworkEvent} does not exist on a dedicated server.
 */
public final class WeaponClientEvents {
    private WeaponClientEvents() {
    }

    public static void register(WeaponSyncChannel sync) {
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> sync.leftServer());
    }
}
