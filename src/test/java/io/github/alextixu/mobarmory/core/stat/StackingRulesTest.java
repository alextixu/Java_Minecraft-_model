package io.github.alextixu.mobarmory.core.stat;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: how modifiers on one stat combine (decision Q4 A). */
class StackingRulesTest {
    private static final double EPS = 1e-9;

    private static final StatDefinition MOVEMENT_SPEED =
        StatDefinition.vanilla("movement_speed", "minecraft:movement_speed", 0.1, 0, 1024).withFloorRatio(0.3);
    private static final StatDefinition ATTACK_DAMAGE =
        StatDefinition.vanilla("attack_damage", "minecraft:attack_damage", 1.0, 0, 2048);
    private static final StatDefinition DODGE = StatDefinition.custom("dodge_chance", 0, 0, 0.75);
    private static final StatDefinition CRIT = StatDefinition.custom("crit_chance", 0, 0, 1.0);

    private static final StatId SPEED = MOVEMENT_SPEED.id();

    @Test
    @DisplayName("沒有任何修飾時，值等於基礎值")
    void noModifiersKeepsBase() {
        assertEquals(0.1, StackingRules.value(MOVEMENT_SPEED, 0.1, List.of()), EPS);
        assertEquals(0.23, StackingRules.value(MOVEMENT_SPEED, 0.23, List.of()), EPS);
    }

    @Test
    @DisplayName("H1（元件層）Given 移動速度 0.1 When 加上重劍的 −30% Then 0.1 × 0.7 = 0.07")
    void heavySwordPercent() {
        assertEquals(0.07, StackingRules.value(MOVEMENT_SPEED, 0.1, List.of(percent(SPEED, -0.30))), EPS);
    }

    @Test
    @DisplayName("H7（元件層）Given 攻擊力 1.0 When 加上短刀的 +2 Then 1.0 ＋ 2 = 3.0")
    void daggerFlat() {
        assertEquals(3.0, StackingRules.value(ATTACK_DAMAGE, 1.0, List.of(add(ATTACK_DAMAGE.id(), 2))), EPS);
    }

    @Test
    @DisplayName("B2 Given 主手重劍（−30%）、身穿重甲（−15%） When 計算移動速度 Then 相乘：0.1 × 0.7 × 0.85 = 0.0595（不是相加的 0.055）")
    void percentagesMultiply() {
        double value = StackingRules.value(MOVEMENT_SPEED, 0.1, List.of(percent(SPEED, -0.30), percent(SPEED, -0.15)));

        assertEquals(0.0595, value, EPS);
        assertFalse(Math.abs(value - 0.055) < EPS);
    }

    @Test
    @DisplayName("固定值先相加，再乘上百分比：(1.0 ＋ 6 ＋ 2) × 1.1 = 9.9")
    void flatThenPercent() {
        StatId attack = ATTACK_DAMAGE.id();
        double value = StackingRules.value(ATTACK_DAMAGE, 1.0, List.of(add(attack, 6), percent(attack, 0.10), add(attack, 2)));

        assertEquals(9.9, value, EPS);
    }

    @Test
    @DisplayName("B3 Given 疊了很多減速裝備 When 算出來的移動速度低於下限 Then 夾在下限：不低於基礎值的 30%（0.03）")
    void movementSpeedFloor() {
        var result = StackingRules.stack(MOVEMENT_SPEED, 0.1,
            List.of(percent(SPEED, -0.30), percent(SPEED, -0.30), percent(SPEED, -0.30), percent(SPEED, -0.30)));

        assertEquals(0.1 * Math.pow(0.7, 4), result.raw(), EPS);
        assertEquals(0.03, result.value(), EPS);
        assertTrue(result.clamped());
    }

    @Test
    @DisplayName("下限比例跟著基礎值走：殭屍基礎 0.23，最低 0.069")
    void floorFollowsBase() {
        double value = StackingRules.value(MOVEMENT_SPEED, 0.23, List.of(percent(SPEED, -0.90)));

        assertEquals(0.069, value, EPS);
    }

    @Test
    @DisplayName("B4 Given 疊了很多迴避裝備 When 迴避率加總超過上限 Then 夾在上限 75%；暴擊率最高 100%")
    void chanceCaps() {
        StatId dodge = DODGE.id();
        StatId crit = CRIT.id();

        assertEquals(0.75, StackingRules.value(DODGE, 0, List.of(add(dodge, 0.15), add(dodge, 0.4), add(dodge, 0.4))), EPS);
        assertEquals(1.0, StackingRules.value(CRIT, 0, List.of(add(crit, 0.8), add(crit, 0.8))), EPS);
    }

    @Test
    @DisplayName("算出來的值不會低於定義的最小值")
    void neverBelowMin() {
        assertEquals(0.0, StackingRules.value(DODGE, 0, List.of(add(DODGE.id(), -0.5))), EPS);
    }

    @Test
    @DisplayName("把別的數值的修飾拿來疊加會丟例外（接線錯誤要早點發現）")
    void wrongStatRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> StackingRules.value(DODGE, 0, List.of(add(CRIT.id(), 0.1))));
    }

    @Test
    @DisplayName("修飾值不合法時無法建立：NaN、無限大、百分比 ≤ −100%")
    void invalidModifiersRejected() {
        assertThrows(IllegalArgumentException.class, () -> add(SPEED, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> add(SPEED, Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> percent(SPEED, -1.0));
        assertThrows(IllegalArgumentException.class, () -> percent(SPEED, -1.5));
    }
}
