package io.github.alextixu.mobarmory.core.feedback;

/** Which side of a hit a watching player is on (decision Q5: only the players taking part see it). */
public enum Viewer {
    /** The player who dealt the hit. */
    ATTACKER,
    /** The player who was hit. */
    DEFENDER
}
