package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Whose weapon definitions a client is using, and the way back to its own (review-03 P2-8).
 * <p>
 * While a player is on someone else's server, the server's definitions replace this game's own
 * ({@link #applyFromServer}), so names and tooltips match the server. When the player leaves, {@link #restoreLocal}
 * reads this game's {@code config/mobarmory/weapons/} again, so single player and the next server start from the
 * local files, not from the last server's. The files are the one source of truth (08), so nothing is kept in memory
 * to go back to. A single-player game never applies anything here, because its client and server share one library.
 */
public final class ClientWeaponState {
    private final EquipmentLibrary library;
    private final WeaponStore store;
    private final WeaponLoader loader;
    private boolean fromServer;

    public ClientWeaponState(EquipmentLibrary library, WeaponStore store, WeaponLoader loader) {
        this.library = library;
        this.store = store;
        this.loader = loader;
    }

    /**
     * Puts the server's definitions into effect on this client ({@link WeaponSync#apply}).
     *
     * @throws WeaponFormatException if the server sent something this version cannot read; nothing changes
     */
    public synchronized ReloadReport applyFromServer(Map<String, String> sent) throws WeaponFormatException {
        ReloadReport report = WeaponSync.apply(library, sent);
        fromServer = true;
        return report;
    }

    /**
     * After leaving a server: this game's own definitions again, read from its files. All or nothing, like
     * {@code /mobarmory reload}.
     *
     * @return what changed, or empty if the library already held this game's own definitions
     * @throws WeaponLoadException if a local file has a problem; the server's definitions stay until the next try
     */
    public synchronized Optional<ReloadReport> restoreLocal() throws IOException, WeaponLoadException {
        if (!fromServer) {
            return Optional.empty();
        }
        ReloadReport report;
        try {
            report = library.replaceAll(loader.load(store));
        } catch (IllegalArgumentException e) {
            throw new WeaponLoadException(List.of(e.getMessage()));
        }
        fromServer = false;
        return Optional.of(report);
    }

    /** True while the library holds a server's definitions instead of this game's files. */
    public synchronized boolean holdsServerDefinitions() {
        return fromServer;
    }
}
