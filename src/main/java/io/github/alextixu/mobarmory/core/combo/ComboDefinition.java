package io.github.alextixu.mobarmory.core.combo;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One combo: two swings in this order, the second at most {@link #windowTicks} game ticks after the first; its
 * {@link #effect} applies to the second swing only (decision O4). "出招順序不同就是不同 combo": WALK then RUN and
 * RUN then WALK are two combos. Every field comes from the combo's file in {@code config/mobarmory/combos/}, so a
 * player adds a combo by adding a file (19, Q2 changed to JSON).
 *
 * @param id          the file name without {@code .json}: lowercase letters, digits and underscores
 * @param name        display name, shown as-is on the action bar in any language (like weapon names)
 * @param first       the swing that starts the combo
 * @param second      the swing that completes it
 * @param windowTicks the most ticks the second swing may come after the first (20 ticks = 1 second); that many
 *                    still counts (O5)
 * @param effect      what happens to the second swing
 */
public record ComboDefinition(String id, String name, ComboInput first, ComboInput second, long windowTicks,
                              ComboEffect effect) {
    /** Long enough for any combo name, short enough to fit after a hit on the action bar. */
    public static final int MAX_NAME_LENGTH = 32;
    /** One minute; a longer window would make any two swings a combo. */
    public static final long MAX_WINDOW_TICKS = 1200;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");

    public ComboDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(effect, "effect");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("combo id must be 1 to 64 lowercase letters, digits or _, got '" + id + "'");
        }
        name = name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Combo '" + id + "': name must be 1 to " + MAX_NAME_LENGTH
                + " characters without control characters");
        }
        if (windowTicks < 1 || windowTicks > MAX_WINDOW_TICKS) {
            throw new IllegalArgumentException("Combo '" + id + "': window must be 1 to " + MAX_WINDOW_TICKS
                + " ticks, got " + windowTicks);
        }
    }
}
