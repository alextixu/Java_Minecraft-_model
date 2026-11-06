package io.github.alextixu.mobarmory.core.mob;

import java.util.Objects;
import java.util.regex.Pattern;

/** Identifier of a kind of mob, for example {@code brute}. Also its entity type name: {@code /summon mobarmory:brute}. */
public record MobId(String value) {
    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9_]*");

    public MobId {
        Objects.requireNonNull(value, "mob id");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Invalid mob id '" + value + "': use lowercase letters, digits and underscores, starting with a letter");
        }
    }

    public static MobId of(String value) {
        return new MobId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
