package io.github.alextixu.mobarmory.core.mob;

/** A behavior with a speed multiplier (wandering, attacking players, neutral), so the file format can write it once. */
public interface SpeedBehavior extends Behavior {
    /** Multiplier on the mob's movement speed while doing this; 1.0 is its normal speed. */
    double speed();
}
