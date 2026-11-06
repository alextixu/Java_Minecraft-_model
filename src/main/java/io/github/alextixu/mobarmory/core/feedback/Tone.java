package io.github.alextixu.mobarmory.core.feedback;

/** How a line should look. The Forge layer maps each tone to a text color. */
public enum Tone {
    /** A plain hit you dealt. */
    NORMAL,
    /** A critical hit you dealt. */
    CRITICAL,
    /** A dodged hit, seen from either side. */
    MISS,
    /** A hit you took. */
    HURT
}
