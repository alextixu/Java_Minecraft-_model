package io.github.alextixu.mobarmory.core.combo;

/**
 * What a combo does to the swing that completes it (Strategy, decision Q2 A). The first kind only changes the damage
 * ({@link DamageMultiplier}); knockback or slowness would be other implementations (Q5 B, not chosen).
 * <p>
 * Contract, checked for every implementation by {@code ComboEffectContractTest}: for any number another mod might
 * hand over (0, tiny, huge, infinite, negative, NaN) it never throws and never returns a negative number or NaN.
 */
@FunctionalInterface
public interface ComboEffect {

    /**
     * @param damage the swing's damage before armor, already including a critical hit (O3: they multiply)
     * @return the damage to use instead
     */
    double apply(double damage);
}
