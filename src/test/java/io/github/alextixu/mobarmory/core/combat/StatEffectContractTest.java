package io.github.alextixu.mobarmory.core.combat;

import static io.github.alextixu.mobarmory.core.combat.CombatTestSupport.always;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.StatReader;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract tests: every combat effect runs the same checks. They do not know which side (attacker or
 * defender) an effect reads, so the stat is set on both. A new effect is covered as soon as it is listed in
 * {@link ModStats#COMBAT_EFFECTS}.
 */
class StatEffectContractTest {
    private static final double[] ROLLS = {0.0, 0.1, 0.25, 0.5, 0.74, 0.75, 0.9, 0.999999};

    static Stream<StatEffect> allEffects() {
        return ModStats.COMBAT_EFFECTS.all().stream();
    }

    private static StatDefinition definition(StatEffect effect) {
        return ModStats.CATALOG.get(effect.stat());
    }

    private static CombatContext hit(StatEffect effect, double statValue, DamageKind kind, double damage) {
        StatReader both = id -> id.equals(effect.stat()) ? statValue : 0.0;
        return new CombatContext(both, both, damage, kind, false);
    }

    private static boolean triggers(StatEffect effect, CombatContext ctx, RandomSource rng) {
        return !effect.apply(ctx, rng).equals(CombatOutcome.unchanged(ctx));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEffects")
    void zeroNeverTriggers(StatEffect effect) {
        for (double roll : ROLLS) {
            assertTrue(!triggers(effect, hit(effect, 0, DamageKind.MELEE, 10), always(roll)), "roll " + roll);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEffects")
    void atMaximumTheLowestRollTriggers(StatEffect effect) {
        double max = definition(effect).max();

        assertTrue(triggers(effect, hit(effect, max, DamageKind.MELEE, 10), always(0.0)));
        assertTrue(triggers(effect, hit(effect, max, DamageKind.PROJECTILE, 10), always(0.0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEffects")
    void valuesAboveMaximumAreClamped(StatEffect effect) {
        double max = definition(effect).max();
        for (double roll : ROLLS) {
            for (double above : new double[] {max + 0.01, max * 10 + 5, Double.POSITIVE_INFINITY}) {
                assertEquals(
                    effect.apply(hit(effect, max, DamageKind.MELEE, 10), always(roll)),
                    effect.apply(hit(effect, above, DamageKind.MELEE, 10), always(roll)),
                    "value " + above + ", roll " + roll);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEffects")
    void oddValuesDoNotThrowAndActLikeZero(StatEffect effect) {
        for (double odd : new double[] {Double.NaN, -1, Double.NEGATIVE_INFINITY}) {
            for (double roll : ROLLS) {
                CombatContext ctx = hit(effect, odd, DamageKind.MELEE, 10);
                assertDoesNotThrow(() -> effect.apply(ctx, always(roll)));
                assertTrue(!triggers(effect, ctx, always(roll)), "value " + odd + ", roll " + roll);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEffects")
    void sameSeedSameResults(StatEffect effect) {
        double half = definition(effect).max() / 2;

        assertEquals(run(effect, half, 42), run(effect, half, 42));
        assertNotEquals(run(effect, half, 42), run(effect, 0, 42)); // the seed matters only through the stat
    }

    private static List<CombatOutcome> run(StatEffect effect, double statValue, long seed) {
        RandomSource rng = RandomSource.seeded(seed);
        List<CombatOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            outcomes.add(effect.apply(hit(effect, statValue, DamageKind.MELEE, 10), rng));
        }
        return outcomes;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEffects")
    void environmentDamageIsNeverChanged(StatEffect effect) {
        double max = definition(effect).max();
        for (double roll : ROLLS) {
            CombatContext ctx = hit(effect, max, DamageKind.ENVIRONMENT, 10);
            assertEquals(CombatOutcome.unchanged(ctx), effect.apply(ctx, always(roll)));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEffects")
    void damageStaysFiniteAndNonNegative(StatEffect effect) {
        double max = definition(effect).max();
        for (double damage : new double[] {0, 0.5, 10, 1000}) {
            for (double value : new double[] {0, max / 2, max}) {
                for (double roll : ROLLS) {
                    double result = effect.apply(hit(effect, value, DamageKind.MELEE, damage), always(roll)).damage();
                    assertTrue(Double.isFinite(result) && result >= 0, "damage " + damage + " became " + result);
                }
            }
        }
    }
}
