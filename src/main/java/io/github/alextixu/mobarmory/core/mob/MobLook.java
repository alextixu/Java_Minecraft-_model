package io.github.alextixu.mobarmory.core.mob;

import java.util.Objects;

/**
 * What a mob looks like (Q4 A): a borrowed vanilla humanoid body, with its texture, and a size multiplier.
 * The client renderer draws whatever this says, so a new mob never needs its own renderer class.
 *
 * @param body  which vanilla body and texture to borrow
 * @param scale size multiplier; 1.2 is 20% bigger, hitbox included
 */
public record MobLook(Body body, double scale) {
    /** Minecraft's own limits for an entity's scale. */
    public static final double MIN_SCALE = 0.0625;
    public static final double MAX_SCALE = 16;

    /** The vanilla humanoid bodies a mob can borrow (Q4 A). */
    public enum Body {
        ZOMBIE,
        HUSK,
        SKELETON,
        STRAY
    }

    public MobLook {
        Objects.requireNonNull(body, "body");
        if (!Double.isFinite(scale) || scale < MIN_SCALE || scale > MAX_SCALE) {
            throw new IllegalArgumentException(
                "Look scale must be between " + MIN_SCALE + " and " + MAX_SCALE + ", got " + scale);
        }
    }

    /** The vanilla body at its normal size. */
    public static MobLook of(Body body) {
        return new MobLook(body, 1.0);
    }

    public MobLook scaled(double newScale) {
        return new MobLook(body, newScale);
    }
}
