package io.github.alextixu.mobarmory.core.combat;

import java.util.List;

/** Runs a group of effects in order. Each effect sees the previous one's result; a cancelled hit stops the chain. */
public final class CombatResolver {

    private CombatResolver() {
    }

    public static CombatOutcome resolve(List<StatEffect> effects, CombatContext ctx, RandomSource rng) {
        CombatOutcome outcome = CombatOutcome.unchanged(ctx);
        CombatContext current = ctx;
        for (StatEffect effect : effects) {
            outcome = effect.apply(current, rng);
            if (outcome.cancelled()) {
                return outcome;
            }
            current = current.after(outcome);
        }
        return outcome;
    }
}
