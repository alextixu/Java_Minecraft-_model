package io.github.alextixu.mobarmory.core.combo;

import java.util.Objects;

/**
 * The combos in effect right now. Loaded from the combo files at startup and swapped as a whole by
 * {@code /mobarmory reload} (all or nothing, like the weapon files): a reader always sees one complete catalog,
 * never half of an old one and half of a new one.
 */
public final class ComboLibrary {
    private volatile ComboCatalog current;

    public ComboLibrary(ComboCatalog initial) {
        this.current = Objects.requireNonNull(initial, "initial");
    }

    public ComboCatalog current() {
        return current;
    }

    public void replace(ComboCatalog next) {
        current = Objects.requireNonNull(next, "next");
    }
}
