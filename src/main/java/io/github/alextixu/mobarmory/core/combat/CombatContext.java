package io.github.alextixu.mobarmory.core.combat;

import io.github.alextixu.mobarmory.core.StatReader;
import java.util.Objects;

/**
 * One hit, as the combat effects see it.
 *
 * @param attacker stats of whoever caused the damage; {@link StatReader#NONE} when nobody did
 * @param defender stats of whoever is being hit
 * @param damage   damage before the critical multiplier, never negative
 * @param kind     melee, projectile or environment
 * @param critical true if the hit already counts as critical (a vanilla jump attack)
 */
public record CombatContext(StatReader attacker, StatReader defender, double damage, DamageKind kind, boolean critical) {

    public CombatContext {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(kind, "kind");
        if (!Double.isFinite(damage) || damage < 0) {
            throw new IllegalArgumentException("Damage must be a finite number >= 0, got " + damage);
        }
    }

    /** The context the next effect sees after this outcome. */
    public CombatContext after(CombatOutcome outcome) {
        return new CombatContext(attacker, defender, outcome.damage(), kind, outcome.critical());
    }
}
