package io.github.alextixu.mobarmory.core.mob;

/**
 * Hunts survival and adventure players within 16 blocks and hits them in melee; hits back at any mob that hurts
 * it; never starts a fight with other mobs (Q7 A, O8). Creative and spectator players are left alone (B8).
 *
 * @param speed multiplier on the mob's movement speed while chasing (1.0 = its normal speed)
 */
public record AttackPlayersBehavior(double speed) implements Behavior {
    public static final String NAME = "attack_players";

    public AttackPlayersBehavior {
        Behavior.requirePositive(NAME, "speed", speed);
    }

    @Override
    public String name() {
        return NAME;
    }
}
