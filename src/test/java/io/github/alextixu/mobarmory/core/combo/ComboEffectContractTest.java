package io.github.alextixu.mobarmory.core.combo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.alextixu.mobarmory.data.ComboEffectTypes;
import io.github.alextixu.mobarmory.data.DefaultCombos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract for every combo effect (Strategy): the kinds a combo file can pick ({@link ComboEffectTypes#STANDARD}, one
 * sample each) and the effects of the shipped combos all run the same checks. A new kind of effect only needs one
 * line in {@link #SAMPLES}; the test below fails until it has one.
 */
class ComboEffectContractTest {
    /** One valid effect object per kind a file can use. */
    private static final Map<String, String> SAMPLES = Map.of(
        "damage_multiplier", "{\"type\": \"damage_multiplier\", \"value\": 1.5}");

    private static final double[] ODD_DAMAGE = {
        0, -0.0, 1e-9, 0.04, 1, 7, 1e38, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, -1,
    };

    record Case(String name, ComboEffect effect) {
        @Override
        public String toString() {
            return name;
        }
    }

    static List<Case> allEffects() throws Exception {
        List<Case> cases = new ArrayList<>();
        for (var sample : SAMPLES.entrySet()) {
            cases.add(new Case("kind " + sample.getKey(),
                ComboEffectTypes.STANDARD.read(JsonParser.parseString(sample.getValue()))));
        }
        for (ComboDefinition combo : DefaultCombos.catalog().all()) {
            cases.add(new Case("shipped " + combo.id(), combo.effect()));
        }
        return cases;
    }

    @Test
    @DisplayName("每一種效果都有樣本：新增效果種類時，這裡會提醒補一行")
    void everyKindHasASample() {
        assertEquals(ComboEffectTypes.STANDARD.names(), new TreeSet<>(SAMPLES.keySet()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEffects")
    @DisplayName("任何傷害：不丟例外、不會變負數、不會變 NaN")
    void anyDamageGivesUsableDamage(Case c) {
        for (double damage : ODD_DAMAGE) {
            double result = assertDoesNotThrow(() -> c.effect().apply(damage), "damage " + damage);
            assertFalse(Double.isNaN(result), c + " turned " + damage + " into NaN");
            assertTrue(result >= 0, c + " turned " + damage + " into " + result);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEffects")
    @DisplayName("一般的傷害（0.1～100）結果還是一般的正數，而且不會因為傷害大反而變小")
    void normalDamageStaysNormal(Case c) {
        double previous = 0;
        for (double damage = 0.1; damage <= 100; damage *= 1.7) {
            double result = c.effect().apply(damage);
            assertTrue(result > 0 && Double.isFinite(result), c + ": " + damage + " -> " + result);
            assertTrue(result >= previous, c + ": more damage in, less out at " + damage);
            previous = result;
        }
    }
}
