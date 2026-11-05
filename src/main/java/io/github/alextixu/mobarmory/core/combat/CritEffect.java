package io.github.alextixu.mobarmory.core.combat;

import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.Objects;

/**
 * The attacker may land a critical hit worth {@value #MULTIPLIER}x damage. A hit is critical if it is already
 * a vanilla jump attack or the attacker's crit chance rolls; either way the multiplier applies once and
 * never stacks (decision O4). This effect is the only place the multiplier is applied.
 */
public final class CritEffect implements StatEffect {
    public static final double MULTIPLIER = 1.5;

    private final StatDefinition stat;

    public CritEffect(StatDefinition stat) {
        this.stat = Objects.requireNonNull(stat, "stat");
    }

    @Override
    public StatId stat() {
        return stat.id();
    }

    @Override
    public CombatOutcome apply(CombatContext ctx, RandomSource rng) {
        if (!ctx.kind().causedByMob()) {
            return CombatOutcome.unchanged(ctx);
        }
        boolean critical = ctx.critical() || rolls(ctx, rng);
        if (!critical) {
            return CombatOutcome.unchanged(ctx);
        }
        return new CombatOutcome(ctx.damage() * MULTIPLIER, false, true);
    }

    private boolean rolls(CombatContext ctx, RandomSource rng) {
        double chance = stat.clamp(ctx.attacker().get(stat.id()));
        return chance > 0 && rng.nextDouble() < chance;
    }

    @Override
    public String toString() {
        return "CritEffect(" + stat.id() + ")";
    }
}
