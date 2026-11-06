package io.github.alextixu.mobarmory.core.feedback;

/** How a hit ended, from the combat effects' point of view. */
public enum HitResult {
    /** The hit landed normally. */
    HIT,
    /** The hit landed as a critical hit (a vanilla jump attack or a crit chance roll; shown the same, decision O10). */
    CRITICAL,
    /** A defensive effect cancelled the hit (dodge). */
    DODGED
}
