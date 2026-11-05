package io.github.alextixu.mobarmory.core.stat;

import java.util.Collection;
import java.util.Objects;

/**
 * How several modifiers on one stat combine (decision Q4 A):
 * <pre>
 *   raw   = (base + sum of fixed amounts) * product of (1 + percentage)
 *   value = raw clamped into [max(min, floorRatio * base), max]
 * </pre>
 * Example: heavy sword (-30%) and heavy armor (-15%) on a 0.1 walker give 0.1 * 0.7 * 0.85 = 0.0595.
 */
public final class StackingRules {

    private StackingRules() {
    }

    /** The result of stacking: the raw number and the value after limits. */
    public record Result(double raw, double value) {
        public boolean clamped() {
            return Double.compare(raw, value) != 0;
        }
    }

    /**
     * @param definition the stat being computed
     * @param base       the entity's own base value for this stat
     * @param modifiers  every modifier currently on the stat; all must target {@code definition.id()}
     * @throws IllegalArgumentException if a modifier targets another stat
     */
    public static Result stack(StatDefinition definition, double base, Collection<StatModifier> modifiers) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(modifiers, "modifiers");
        double flat = 0;
        double factor = 1;
        for (StatModifier modifier : modifiers) {
            if (!modifier.stat().equals(definition.id())) {
                throw new IllegalArgumentException(
                    "Modifier for '" + modifier.stat() + "' passed while stacking '" + definition.id() + "'");
            }
            switch (modifier.operation()) {
                case ADD -> flat += modifier.amount();
                case MULTIPLY -> factor *= 1 + modifier.amount();
            }
        }
        double raw = (base + flat) * factor;
        double lower = Math.max(definition.min(), definition.floorRatio() * base);
        double value = Math.min(definition.max(), Math.max(lower, raw));
        return new Result(raw, value);
    }

    /** Shortcut for {@code stack(...).value()}. */
    public static double value(StatDefinition definition, double base, Collection<StatModifier> modifiers) {
        return stack(definition, base, modifiers).value();
    }
}
