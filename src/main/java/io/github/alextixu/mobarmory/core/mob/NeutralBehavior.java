package io.github.alextixu.mobarmory.core.mob;

/**
 * Neutral (R4b, decision Q5 A): leaves players alone until something hurts it, then hits back at whoever did, in
 * melee. It does not call others of its kind (O5) and calms down by Minecraft's own rules: the target dies, gets away
 * beyond the follow range or stays out of sight for a while (O6).
 *
 * @param speed multiplier on the mob's movement speed while chasing (1.0 = its normal speed); above 1 it runs
 */
public record NeutralBehavior(double speed) implements SpeedBehavior {
    public static final String NAME = "neutral";

    public NeutralBehavior {
        Behavior.requireSpeed(NAME, speed);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean choosesTargets() {
        return true;
    }
}
