package io.github.alextixu.mobarmory.core.combat;

import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.Objects;

/**
 * The defender may avoid a hit completely. Only damage caused by a mob (melee or projectile) can be dodged;
 * falling, fire and drowning cannot (decision O3). The chance is capped by the stat's maximum (75%).
 */
public final class DodgeEffect implements StatEffect {
    private final StatDefinition stat;

    public DodgeEffect(StatDefinition stat) {
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
        double chance = stat.clamp(ctx.defender().get(stat.id()));
        if (chance > 0 && rng.nextDouble() < chance) {
            return CombatOutcome.avoided();
        }
        return CombatOutcome.unchanged(ctx);
    }

    @Override
    public String toString() {
        return "DodgeEffect(" + stat.id() + ")";
    }
}
