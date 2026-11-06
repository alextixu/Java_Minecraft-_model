package io.github.alextixu.mobarmory.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.alextixu.mobarmory.core.equipment.LoadoutCalculator;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Module tests for the prototype mobs (O1): their own stats plus their equipment, worked out with R2's
 * calculator exactly as the game does it. The in-game tests check the same numbers on real entities.
 */
class ModMobsTest {
    private static final double EPS = 1e-9;
    private final LoadoutCalculator calculator = new LoadoutCalculator(MobTestCatalog.MOBS.equipment());

    /** Every stat after equipment: the spawn value, replaced by the equipped value where equipment changes it. */
    private Map<StatId, Double> equipped(MobDefinition mob) {
        Map<StatId, Double> base = mob.spawnStats(ModStats.CATALOG);
        Map<StatId, Double> values = new LinkedHashMap<>(base);
        values.putAll(calculator.compute(mob.equipment(), base::get));
        return values;
    }

    private static double of(Map<StatId, Double> values, StatDefinition stat) {
        return values.get(stat.id());
    }

    @Test
    @DisplayName("H1 + H2 Given 剛生成的狂戰士（生命 40、攻擊 4、速度 0.25、主手重劍） When 讀取數值 Then 最大生命 40、攻擊力 10、移動速度 0.175、攻擊速度 2.4，暴擊和迴避為預設 0")
    void bruteWithHeavySword() {
        Map<StatId, Double> brute = equipped(ModMobs.BRUTE);

        assertEquals(40, of(brute, ModStats.MAX_HEALTH), EPS);
        assertEquals(10, of(brute, ModStats.ATTACK_DAMAGE), EPS);
        assertEquals(0.175, of(brute, ModStats.MOVEMENT_SPEED), EPS);
        assertEquals(2.4, of(brute, ModStats.ATTACK_SPEED), EPS);
        assertEquals(0, of(brute, ModStats.CRIT_CHANCE), EPS);
        assertEquals(0, of(brute, ModStats.DODGE_CHANCE), EPS);
    }

    @Test
    @DisplayName("H5 + H6 前提 Given 剛生成的遊擊兵（生命 16、攻擊 2、速度 0.32、暴擊 0.25、迴避 0.30、主手短刀） When 讀取數值 Then 攻擊力 4、暴擊率 0.5、迴避率 0.30、移動速度 0.352")
    void skirmisherWithDagger() {
        Map<StatId, Double> skirmisher = equipped(ModMobs.SKIRMISHER);

        assertEquals(16, of(skirmisher, ModStats.MAX_HEALTH), EPS);
        assertEquals(4, of(skirmisher, ModStats.ATTACK_DAMAGE), EPS);
        assertEquals(0.5, of(skirmisher, ModStats.CRIT_CHANCE), EPS);
        assertEquals(0.30, of(skirmisher, ModStats.DODGE_CHANCE), EPS);
        assertEquals(0.352, of(skirmisher, ModStats.MOVEMENT_SPEED), EPS);
    }
}
