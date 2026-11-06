package io.github.alextixu.mobarmory.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.LoadoutCalculator;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobEquipmentCheck;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Module tests for the prototype mobs (O1), read from the shipped mob files (R4b, mobarmory/default_mobs/): their own
 * stats plus their equipment, worked out with R2's calculator exactly as the game does it. The in-game tests check the same numbers on real entities.
 */
class DefaultMobsTest {
    private static final double EPS = 1e-9;
    /** Every stat after equipment: the spawn value, replaced by the equipped value where equipment changes it. */
    private static Map<StatId, Double> equipped(MobDefinition mob) {
        return equipped(mob, MobTestCatalog.SHIPPED_WEAPONS);
    }

    /** The same with other weapon files: only what the mob really gets counts (O2 changed to B). */
    private static Map<StatId, Double> equipped(MobDefinition mob, EquipmentCatalog weapons) {
        Map<StatId, Double> base = mob.spawnStats(ModStats.CATALOG);
        Map<StatId, Double> values = new LinkedHashMap<>(base);
        var given = MobEquipmentCheck.check(mob, weapons::find).given();
        values.putAll(new LoadoutCalculator(weapons).compute(given, base::get));
        return values;
    }

    private static double of(Map<StatId, Double> values, StatDefinition stat) {
        return values.get(stat.id());
    }

    @Test
    @DisplayName("H1 + H2 Given 剛生成的狂戰士（生命 40、攻擊 4、速度 0.25、主手重劍） When 讀取數值 Then 最大生命 40、攻擊力 10、移動速度 0.175、攻擊速度 2.4，暴擊和迴避為預設 0")
    void bruteWithHeavySword() {
        Map<StatId, Double> brute = equipped(MobTestCatalog.BRUTE);

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
        Map<StatId, Double> skirmisher = equipped(MobTestCatalog.SKIRMISHER);

        assertEquals(16, of(skirmisher, ModStats.MAX_HEALTH), EPS);
        assertEquals(4, of(skirmisher, ModStats.ATTACK_DAMAGE), EPS);
        assertEquals(0.5, of(skirmisher, ModStats.CRIT_CHANCE), EPS);
        assertEquals(0.30, of(skirmisher, ModStats.DODGE_CHANCE), EPS);
        assertEquals(0.352, of(skirmisher, ModStats.MOVEMENT_SPEED), EPS);
    }

    @Test
    @DisplayName("B3（11/07 改 B，review-02 6-3 a）Given heavy_sword.json 被刪掉 When 狂戰士生成 Then 不拿重劍：攻擊力 4、移動速度 0.25；遊擊兵不受影響")
    void bruteWithoutHeavySword() {
        EquipmentCatalog.Builder builder = EquipmentCatalog.builder(ModStats.CATALOG);
        MobTestCatalog.SHIPPED_WEAPONS.all().stream()
            .filter(piece -> !piece.id().equals(EquipmentId.of("heavy_sword")))
            .forEach(builder::add);
        EquipmentCatalog withoutSword = builder.build();

        Map<StatId, Double> brute = equipped(MobTestCatalog.BRUTE, withoutSword);
        assertEquals(40, of(brute, ModStats.MAX_HEALTH), EPS);
        assertEquals(4, of(brute, ModStats.ATTACK_DAMAGE), EPS);
        assertEquals(0.25, of(brute, ModStats.MOVEMENT_SPEED), EPS);
        assertEquals(1, MobEquipmentCheck.check(MobTestCatalog.BRUTE, withoutSword::find).problems().size());

        assertEquals(equipped(MobTestCatalog.SKIRMISHER), equipped(MobTestCatalog.SKIRMISHER, withoutSword));
        assertEquals(List.of(), MobEquipmentCheck.check(MobTestCatalog.SKIRMISHER, withoutSword::find).problems());
    }
}
