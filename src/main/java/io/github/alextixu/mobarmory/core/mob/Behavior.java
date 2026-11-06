package io.github.alextixu.mobarmory.core.mob;

/**
 * One thing a mob does, described as data: what to do and with which parameters, never how (Strategy, Q1/Q2 A).
 * The Forge layer turns each kind of behavior into Minecraft AI goals with one {@code GoalBinder}.
 * <p>
 * Adding a behavior means a new record here, a binder, one line in the binder table and one line in the mob file
 * format's table (R4b). Existing mobs and existing behaviors do not change.
 */
public interface Behavior {
    /** The fastest a speed multiplier may be (R4b decision O7): three times the mob's movement speed. */
    double MAX_SPEED = 3.0;

    /** Short name, also the {@code type} in a mob file, e.g. {@code wander}. */
    String name();

    /**
     * Whether this behavior decides whom the mob fights (attacking players, neutral). A mob has at most one of these
     * (R4b decision O3).
     */
    default boolean choosesTargets() {
        return false;
    }

    /** Whether this behavior only works while another one gives the mob a target (leap, R4b decision O4). */
    default boolean needsTarget() {
        return false;
    }

    /**
     * Speed multipliers are relative to the mob's movement speed stat: above 0 and at most {@link #MAX_SPEED}
     * (R4b decision O7; the same rule for files and the web page).
     */
    static double requireSpeed(String behavior, double value) {
        if (!Double.isFinite(value) || value <= 0 || value > MAX_SPEED) {
            throw new IllegalArgumentException(
                "Behavior '" + behavior + "': speed must be above 0 and at most " + MAX_SPEED + ", got " + value);
        }
        return value;
    }
}
