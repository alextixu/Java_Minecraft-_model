package io.github.alextixu.mobarmory.core.combat;

/** Where damage comes from. Only damage caused by a mob can be dodged or crit (decision O3). */
public enum DamageKind {
    /** Hit directly by a living attacker (sword, zombie punch). */
    MELEE,
    /** Hit by something a living attacker shot or threw (arrow, trident). */
    PROJECTILE,
    /** No living attacker: falling, fire, lava, drowning, starving. */
    ENVIRONMENT;

    public boolean causedByMob() {
        return this != ENVIRONMENT;
    }
}
