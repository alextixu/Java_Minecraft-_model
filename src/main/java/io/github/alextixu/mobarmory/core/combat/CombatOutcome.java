package io.github.alextixu.mobarmory.core.combat;

/**
 * What a combat effect decided.
 *
 * @param damage    damage to deal; 0 when cancelled
 * @param cancelled the hit does not land at all (dodged)
 * @param critical  the hit is critical
 */
public record CombatOutcome(double damage, boolean cancelled, boolean critical) {

    /** The effect did nothing: same damage, same critical state. */
    public static CombatOutcome unchanged(CombatContext ctx) {
        return new CombatOutcome(ctx.damage(), false, ctx.critical());
    }

    /** The hit is avoided entirely. */
    public static CombatOutcome avoided() {
        return new CombatOutcome(0, true, false);
    }
}
