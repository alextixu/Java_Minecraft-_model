package io.github.alextixu.mobarmory.core.feedback;

/** Who took part in a hit, as far as the display rules care. */
public enum Party {
    /** A player (only players can be shown an action bar). */
    PLAYER,
    /** Any other living thing: cows, zombies, our custom mobs. */
    MOB,
    /** Nobody: the damage has no living cause (falling, fire, an arrow whose shooter left). */
    NONE
}
