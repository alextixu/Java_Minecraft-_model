package io.github.alextixu.mobarmory.core.stat;

import java.util.Objects;

/**
 * One change to one stat, for example "movement speed -30%".
 *
 * @param stat      the stat being changed
 * @param operation fixed amount or percentage
 * @param amount    the size of the change; a percentage is written as a fraction ({@code -0.30} is -30%)
 */
public record StatModifier(StatId stat, ModifierOperation operation, double amount) {

    public StatModifier {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(operation, "operation");
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Modifier on '" + stat + "' must be a finite number, got " + amount);
        }
        if (operation == ModifierOperation.MULTIPLY && amount <= -1.0) {
            throw new IllegalArgumentException(
                "Percentage modifier on '" + stat + "' must be greater than -100%, got " + amount);
        }
    }

    /** A fixed amount, e.g. {@code add(ATTACK_DAMAGE, 6)}. */
    public static StatModifier add(StatId stat, double amount) {
        return new StatModifier(stat, ModifierOperation.ADD, amount);
    }

    /** A percentage written as a fraction, e.g. {@code percent(MOVEMENT_SPEED, -0.30)} for -30%. */
    public static StatModifier percent(StatId stat, double fraction) {
        return new StatModifier(stat, ModifierOperation.MULTIPLY, fraction);
    }
}
