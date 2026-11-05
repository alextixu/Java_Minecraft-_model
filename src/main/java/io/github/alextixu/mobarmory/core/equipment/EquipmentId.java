package io.github.alextixu.mobarmory.core.equipment;

import java.util.Objects;
import java.util.regex.Pattern;

/** Identifier of a piece of equipment, for example {@code heavy_sword}. Also used as the item's registry name. */
public record EquipmentId(String value) {
    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9_]*");

    public EquipmentId {
        Objects.requireNonNull(value, "equipment id");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Invalid equipment id '" + value + "': use lowercase letters, digits and underscores, starting with a letter");
        }
    }

    public static EquipmentId of(String value) {
        return new EquipmentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
