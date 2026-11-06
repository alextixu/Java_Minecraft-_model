package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import io.github.alextixu.mobarmory.core.mob.MobReloadReport;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * The one place that changes mobs while the game runs (R4b): the reload command, the web page's save and its new-mob
 * form all come here, so files and game always agree. Same rules as the weapons' {@link WeaponEditor}: the file is
 * written first, and only a successful write changes the game (B18); the later of two saves wins (B19).
 * <p>
 * Plain Java; the Forge adapter runs it on the server thread and then updates the mobs in the world.
 */
public final class MobEditor {
    private final MobLibrary library;
    private final MobStore store;
    private final MobLoader loader;

    public MobEditor(MobLibrary library, MobStore store, MobLoader loader) {
        this.library = Objects.requireNonNull(library, "library");
        this.store = Objects.requireNonNull(store, "store");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public MobLibrary library() {
        return library;
    }

    /**
     * Reads every mob file again and replaces the definitions (H9). All or nothing (B15).
     *
     * @throws MobLoadException if any file has a problem; nothing changes
     */
    public synchronized MobReloadReport reload() throws IOException, MobLoadException {
        List<MobDefinition> loaded = loader.load(store);
        try {
            return library.replaceAll(loaded);
        } catch (IllegalArgumentException e) {
            throw new MobLoadException(List.of(e.getMessage()));
        }
    }

    /**
     * Saves a changed mob (H4): checks it, writes its file, then puts it into effect.
     *
     * @throws NoSuchElementException if the mob does not exist (404)
     * @throws WeaponFormatException  if the change is not valid, naming the field (B11; 400)
     * @throws IOException            if the file cannot be written; the game is not changed (B18)
     */
    public synchronized void update(MobDefinition changed) throws IOException, WeaponFormatException {
        MobId id = changed.id();
        MobDefinition current = library.current().find(id)
            .orElseThrow(() -> new NoSuchElementException("Unknown mob '" + id + "'"));
        if (current.look().body() != changed.look().body()) {
            throw new WeaponFormatException("look.body", "can only change after a restart (now "
                + current.look().body().name().toLowerCase(Locale.ROOT) + ")");
        }
        check(changed);
        store.writeMob(id, MobFileJson.write(changed));
        library.update(changed);
    }

    /**
     * Saves a new mob (H3). It can be spawned after a restart (platform limit).
     *
     * @throws WeaponFormatException if the id is taken (B16) or the mob is not valid
     */
    public synchronized void create(MobDefinition definition) throws IOException, WeaponFormatException {
        if (library.isRegistered(definition.id()) || library.pending().containsKey(definition.id())) {
            throw new WeaponFormatException("id", "'" + definition.id() + "' already exists");
        }
        check(definition);
        store.writeMob(definition.id(), MobFileJson.write(definition));
        library.addPending(definition);
    }

    /** The stats a definition sets must exist and be in range; normally already checked when its JSON was read. */
    private void check(MobDefinition definition) throws WeaponFormatException {
        MobFileJson.read(definition.id(), MobFileJson.write(definition), library.stats());
    }
}
