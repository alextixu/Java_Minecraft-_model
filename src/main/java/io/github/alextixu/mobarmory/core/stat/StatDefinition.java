package io.github.alextixu.mobarmory.core.stat;

import java.util.Objects;
import java.util.Optional;

/**
 * One entry of the stat catalog: what the stat is called, its default and its limits.
 *
 * @param id               the stat id
 * @param defaultValue     value an entity has when nothing modifies it
 * @param min              lowest allowed value
 * @param max              highest allowed value
 * @param floorRatio       the value may not drop below {@code floorRatio * base}; 0 means no such floor
 *                         (movement speed uses 0.3: never slower than 30% of the base speed)
 * @param vanillaAttribute the Minecraft attribute this stat maps to, such as {@code minecraft:movement_speed};
 *                         empty for stats this mod adds itself
 */
public record StatDefinition(
    StatId id,
    double defaultValue,
    double min,
    double max,
    double floorRatio,
    Optional<String> vanillaAttribute
) {

    public StatDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vanillaAttribute, "vanillaAttribute");
        requireFinite(id, "default value", defaultValue);
        requireFinite(id, "minimum", min);
        requireFinite(id, "maximum", max);
        requireFinite(id, "floor ratio", floorRatio);
        if (min > max) {
            throw new IllegalArgumentException(
                "Stat '" + id + "': minimum " + min + " is greater than maximum " + max);
        }
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(
                "Stat '" + id + "': default value " + defaultValue + " is outside [" + min + ", " + max + "]");
        }
        if (floorRatio < 0 || floorRatio > 1) {
            throw new IllegalArgumentException(
                "Stat '" + id + "': floor ratio must be between 0 and 1, got " + floorRatio);
        }
        vanillaAttribute.ifPresent(key -> {
            if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException(
                    "Stat '" + id + "': vanilla attribute must look like 'minecraft:movement_speed', got '" + key + "'");
            }
        });
    }

    /** A stat this mod adds to every living entity. */
    public static StatDefinition custom(String id, double defaultValue, double min, double max) {
        return new StatDefinition(StatId.of(id), defaultValue, min, max, 0, Optional.empty());
    }

    /**
     * A stat backed by an attribute Minecraft already has. The default is only used when an entity lacks
     * the attribute; entities that have it keep their own base value (players walk at 0.1, zombies at 0.23).
     */
    public static StatDefinition vanilla(String id, String attribute, double defaultValue, double min, double max) {
        return new StatDefinition(StatId.of(id), defaultValue, min, max, 0, Optional.of(attribute));
    }

    /** Same stat, but never below {@code ratio * base} after modifiers. */
    public StatDefinition withFloorRatio(double ratio) {
        return new StatDefinition(id, defaultValue, min, max, ratio, vanillaAttribute);
    }

    public boolean isCustom() {
        return vanillaAttribute.isEmpty();
    }

    /** Clamps a value into {@code [min, max]}. NaN becomes the minimum. */
    public double clamp(double value) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.min(max, Math.max(min, value));
    }

    private static void requireFinite(StatId id, String what, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Stat '" + id + "': " + what + " must be a finite number, got " + value);
        }
    }
}
