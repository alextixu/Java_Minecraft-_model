package io.github.alextixu.mobarmory.core.mob;

/**
 * Walks around at random when it has nothing else to do, and looks at nearby players.
 *
 * @param speed multiplier on the mob's movement speed while wandering (1.0 = its normal speed)
 */
public record WanderBehavior(double speed) implements SpeedBehavior {
    public static final String NAME = "wander";

    public WanderBehavior {
        Behavior.requireSpeed(NAME, speed);
    }

    @Override
    public String name() {
        return NAME;
    }
}
