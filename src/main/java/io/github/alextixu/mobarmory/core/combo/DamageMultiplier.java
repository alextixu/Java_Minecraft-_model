package io.github.alextixu.mobarmory.core.combo;

/**
 * Multiplies the damage of the swing that completes the combo (decision Q5 A: Lunge x1.5, Follow-up x1.3).
 *
 * @param factor how many times the damage; a positive, finite number
 */
public record DamageMultiplier(double factor) implements ComboEffect {

    public DamageMultiplier {
        if (!(factor > 0) || Double.isInfinite(factor)) {
            throw new IllegalArgumentException("damage factor must be a positive number, got " + factor);
        }
    }

    /** No damage, a negative amount or NaN from another mod has nothing to multiply and becomes 0. */
    @Override
    public double apply(double damage) {
        return damage > 0 ? damage * factor : 0;
    }
}
