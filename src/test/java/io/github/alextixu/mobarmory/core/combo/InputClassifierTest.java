package io.github.alextixu.mobarmory.core.combo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.alextixu.mobarmory.content.ModCombos;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.StatReader;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Component tests: a swing is walking or running by the movement speed when it starts (Q4 A, O12, 17's speed table). */
class InputClassifierTest {

    private static ComboInput at(double speed) {
        return ModCombos.INPUTS.classify(StatReader.of(Map.of(ModStats.MOVEMENT_SPEED.id(), speed)));
    }

    @ParameterizedTest(name = "{0}: speed {1} -> {2}")
    @CsvSource({
        "empty hand walking,          0.100,   WALK",
        "empty hand sprinting x1.3,   0.13,    RUN",
        "dagger walking (+10%),       0.110,   WALK",
        "dagger sprinting,            0.143,   RUN",
        "heavy armor sprinting,       0.1105,  WALK",
        "heavy sword sprinting (B12), 0.091,   WALK",
        "speed potion I walking (H5), 0.12,    RUN",
    })
    @DisplayName("17 的速度表：數值決定輸入，不看按了什麼鍵")
    void speedTable(String what, double speed, ComboInput expected) {
        assertEquals(expected, at(speed), what);
    }

    @Test
    @DisplayName("門檻 0.12 本身算奔跑，差一點點就算走路（O12）")
    void thresholdIsInclusive() {
        assertEquals(ComboInput.RUN, at(0.12));
        assertEquals(ComboInput.WALK, at(Math.nextDown(0.12)));
    }

    @Test
    @DisplayName("H5 照原版算法：速度藥水 I 是 0.1 × (1 + 0.2)，也算奔跑")
    void speedPotionUsesMinecraftsArithmetic() {
        assertEquals(ComboInput.RUN, at(0.1 * (1 + 0.2)));
    }

    @Test
    @DisplayName("讀到 NaN、0 或負數都算走路，不丟例外")
    void oddSpeedsAreWalking() {
        assertEquals(ComboInput.WALK, at(Double.NaN));
        assertEquals(ComboInput.WALK, at(0));
        assertEquals(ComboInput.WALK, at(-1));
        assertEquals(ComboInput.RUN, at(Double.POSITIVE_INFINITY));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -0.1, Double.NaN, Double.POSITIVE_INFINITY})
    @DisplayName("門檻一定是正數，寫錯時啟動就失敗")
    void rejectsBadRunSpeed(double runSpeed) {
        assertThrows(IllegalArgumentException.class, () -> new InputClassifier(ModStats.MOVEMENT_SPEED.id(), runSpeed));
    }
}
