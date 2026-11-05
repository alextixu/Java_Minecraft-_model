package io.github.alextixu.mobarmory.core.combat;

import io.github.alextixu.mobarmory.core.StatReader;
import io.github.alextixu.mobarmory.core.stat.StatId;

/** Small helpers shared by the combat tests. */
final class CombatTestSupport {

    private CombatTestSupport() {
    }

    /** Every roll returns {@code value}: 0.0 makes any non-zero chance succeed. */
    static RandomSource always(double value) {
        return () -> value;
    }

    /** A reader where {@code stat} is {@code value} and everything else is 0. */
    static StatReader with(StatId stat, double value) {
        return id -> id.equals(stat) ? value : 0.0;
    }

    static CombatContext melee(StatReader attacker, StatReader defender, double damage) {
        return new CombatContext(attacker, defender, damage, DamageKind.MELEE, false);
    }
}
