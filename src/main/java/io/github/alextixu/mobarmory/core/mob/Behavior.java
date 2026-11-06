package io.github.alextixu.mobarmory.core.mob;

/**
 * One thing a mob does, described as data: what to do and with which parameters, never how (Strategy, Q1/Q2 A).
 * The Forge layer turns each kind of behavior into Minecraft AI goals with one {@code GoalBinder}.
 * <p>
 * Adding a behavior (run, charge, neutral for R6) means a new record here, a binder, and one line in the
 * binder table. Existing mobs and existing behaviors do not change.
 */
public interface Behavior {

    /** Short name for messages and, later, the R6 web page, e.g. {@code wander}. */
    String name();

    /** Speed multipliers are relative to the mob's movement speed stat, so they must be positive. */
    static double requirePositive(String behavior, String what, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(
                "Behavior '" + behavior + "': " + what + " must be a positive number, got " + value);
        }
        return value;
    }
}
