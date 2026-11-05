package io.github.alextixu.mobarmory.core.stat;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifier of a stat, for example {@code dodge_chance}.
 * Only lowercase letters, digits and underscores, so it can double as a Minecraft resource path.
 */
public record StatId(String value) {
    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9_]*");

    public StatId {
        Objects.requireNonNull(value, "stat id");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Invalid stat id '" + value + "': use lowercase letters, digits and underscores, starting with a letter");
        }
    }

    public static StatId of(String value) {
        return new StatId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
