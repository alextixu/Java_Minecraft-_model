package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.combo.ComboCatalog;
import io.github.alextixu.mobarmory.core.combo.ComboDefinition;
import io.github.alextixu.mobarmory.core.combo.ComboLibrary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/**
 * The combo files while the game runs ({@code config/mobarmory/combos/}, R3). Follows the weapon files' rules:
 * <ul>
 *   <li>first start: the default files are installed; after that the folder is the only source;</li>
 *   <li>startup: a wrong file stops the game with a message naming the file (R2b decision O7);</li>
 *   <li>{@code /mobarmory reload}: all or nothing; a wrong file changes nothing (R2b decision O8). Unlike weapons, a
 *       new or deleted combo file applies right away: a combo registers nothing in the game.</li>
 * </ul>
 */
public final class ComboFiles {
    private final JsonFolder folder;
    private final ComboLoader loader;
    private final ComboLibrary library;

    private ComboFiles(JsonFolder folder, ComboLoader loader, ComboLibrary library) {
        this.folder = folder;
        this.loader = loader;
        this.library = library;
    }

    /**
     * Installs the defaults on the very first start, then reads every file.
     *
     * @throws IllegalStateException if a file is wrong, naming every problem
     */
    public static ComboFiles load(JsonFolder folder, ComboLoader loader) {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(loader, "loader");
        try {
            DefaultCombos.installIfMissing(folder);
            return new ComboFiles(folder, loader, new ComboLibrary(loader.load(folder)));
        } catch (WeaponLoadException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Mob Armory could not read its combo files", e);
        }
    }

    public JsonFolder folder() {
        return folder;
    }

    /** The combos in effect; the listener reads it on every swing. */
    public ComboLibrary library() {
        return library;
    }

    /**
     * Reads every file again and replaces all combos at once.
     *
     * @return the ids of the combos now in effect
     * @throws WeaponLoadException if any file is wrong; the combos in effect stay as they were
     */
    public List<String> reload() throws IOException, WeaponLoadException {
        ComboCatalog next = loader.load(folder);
        library.replace(next);
        return next.all().stream().map(ComboDefinition::id).toList();
    }
}
