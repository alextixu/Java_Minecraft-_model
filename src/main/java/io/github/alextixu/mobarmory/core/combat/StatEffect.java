package io.github.alextixu.mobarmory.core.combat;

import io.github.alextixu.mobarmory.core.stat.StatId;

/**
 * What a stat does in combat (Strategy). A new stat with a combat effect is one new implementation of this
 * interface plus one line in {@code content/ModStats}; the event handlers never change.
 * <p>
 * Contract (checked for every implementation by {@code StatEffectContractTest}): a stat of 0 never
 * triggers; values outside the stat's limits are clamped; odd values (NaN, negative) never throw; the same
 * random sequence gives the same result; environment damage is never changed; damage never becomes
 * negative or NaN.
 */
public interface StatEffect {

    /** The stat that drives this effect. */
    StatId stat();

    CombatOutcome apply(CombatContext ctx, RandomSource rng);
}
