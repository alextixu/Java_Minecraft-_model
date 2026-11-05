package io.github.alextixu.mobarmory.core.combat;

import java.util.SplittableRandom;

/**
 * Source of randomness for combat rolls. Kept as an interface so tests can make a roll certain,
 * and so the game can plug in each entity's own random generator.
 */
@FunctionalInterface
public interface RandomSource {

    /** A number in {@code [0, 1)}. An effect with chance {@code p} triggers when this is below {@code p}. */
    double nextDouble();

    /** Reproducible sequence: the same seed always gives the same rolls. */
    static RandomSource seeded(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        return random::nextDouble;
    }
}
