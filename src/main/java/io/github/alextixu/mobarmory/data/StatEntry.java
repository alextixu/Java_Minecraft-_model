package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import java.util.Objects;
import java.util.Optional;

/**
 * What one stat file says (section E, 24-stat-files-boundary-note.md): the stat itself, plus two things only the game
 * needs and {@code core/stat} does not know about.
 *
 * @param definition the stat, exactly as the rest of the mod uses it
 * @param name       shown in tooltips and {@code /mobarmory stats}; only for a new attribute (Q7 A)
 * @param sync       whether clients receive the value; only for a new attribute (O2)
 */
public record StatEntry(StatDefinition definition, Optional<String> name, boolean sync) {
    public StatEntry {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(name, "name");
        if (!definition.isCustom() && (name.isPresent() || !sync)) {
            throw new IllegalArgumentException("Stat '" + definition.id()
                + "': 'name' and 'sync' only apply to a new attribute, not to " + definition.vanillaAttribute().orElseThrow());
        }
    }

    /** A stat with no name of its own and synced to clients, as every stat was before section E. */
    public static StatEntry of(StatDefinition definition) {
        return new StatEntry(definition, Optional.empty(), true);
    }
}
