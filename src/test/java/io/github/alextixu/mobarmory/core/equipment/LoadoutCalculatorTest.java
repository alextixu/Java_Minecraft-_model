package io.github.alextixu.mobarmory.core.equipment;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.data.DefaultWeapons;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Module tests: "equipment changes the player's stats", with the success conditions from
 * 02-given-when-then.md broken one at a time. Uses the real prototype content (O7).
 */
class LoadoutCalculatorTest {
    private static final double EPS = 1e-9;

    private static final StatId ATTACK = ModStats.ATTACK_DAMAGE.id();
    private static final StatId SPEED = ModStats.MOVEMENT_SPEED.id();
    private static final StatId CRIT = ModStats.CRIT_CHANCE.id();
    private static final StatId DODGE = ModStats.DODGE_CHANCE.id();

    /** Base values of a vanilla player. */
    private static final StatId ATTACK_SPEED = ModStats.ATTACK_SPEED.id();
    private static final Map<StatId, Double> PLAYER_BASE = Map.of(
        ATTACK, 1.0, SPEED, 0.1, ModStats.ARMOR.id(), 0.0, ATTACK_SPEED, 4.0, CRIT, 0.0, DODGE, 0.0);

    /** The shipped default pieces (R2b: read from mobarmory/default_weapons/*.json instead of Java constants). */
    private static final EquipmentCatalog DEFAULTS = DefaultWeapons.catalog(ModStats.CATALOG);
    private static final EquipmentDefinition HEAVY_SWORD = DEFAULTS.get(EquipmentId.of("heavy_sword"));
    private static final EquipmentDefinition DAGGER = DEFAULTS.get(EquipmentId.of("dagger"));
    private static final EquipmentDefinition EVASION_CHESTPLATE = DEFAULTS.get(EquipmentId.of("evasion_chestplate"));
    private static final EquipmentDefinition HEAVY_ARMOR = DEFAULTS.get(EquipmentId.of("heavy_armor"));
    private final LoadoutCalculator calculator = new LoadoutCalculator(DEFAULTS);

    private static Map<EquipmentSlotKind, EquipmentId> loadout(Object... slotAndEquipment) {
        Map<EquipmentSlotKind, EquipmentId> map = new EnumMap<>(EquipmentSlotKind.class);
        for (int i = 0; i < slotAndEquipment.length; i += 2) {
            map.put((EquipmentSlotKind) slotAndEquipment[i], ((EquipmentDefinition) slotAndEquipment[i + 1]).id());
        }
        return map;
    }

    /** The value the entity ends up with: computed if equipment touches the stat, otherwise its base. */
    private double valueOf(LoadoutCalculator calc, Map<EquipmentSlotKind, EquipmentId> worn, StatId stat) {
        return calc.compute(worn, PLAYER_BASE::get).getOrDefault(stat, PLAYER_BASE.get(stat));
    }

    private double valueOf(Map<EquipmentSlotKind, EquipmentId> worn, StatId stat) {
        return valueOf(calculator, worn, stat);
    }

    @Test
    @DisplayName("H1 Given 玩家空手，移動速度 0.1 When 主手拿起重劍 Then 移動速度 = 0.1 × 0.7 = 0.07")
    void h1HeavySwordSlowsDown() {
        assertEquals(0.07, valueOf(loadout(EquipmentSlotKind.MAINHAND, HEAVY_SWORD), SPEED), EPS);
    }

    @Test
    @DisplayName("N3 Given 玩家空手，攻擊速度 4.0 When 主手拿起重劍 Then 攻擊速度 = 4.0 × 0.6 = 2.4；放下回到 4.0")
    void n3HeavySwordSwingsSlower() {
        assertEquals(2.4, valueOf(loadout(EquipmentSlotKind.MAINHAND, HEAVY_SWORD), ATTACK_SPEED), EPS);
        assertEquals(4.0, valueOf(Map.of(), ATTACK_SPEED), EPS);
    }

    @Test
    @DisplayName("H2／B10 Given 玩家主手拿著重劍（0.07） When 換回空手或把重劍丟到地上 Then 移動速度回到 0.1，不殘留修飾")
    void h2EmptyHandRestoresSpeed() {
        valueOf(loadout(EquipmentSlotKind.MAINHAND, HEAVY_SWORD), SPEED);
        Map<StatId, Double> after = calculator.compute(Map.of(), PLAYER_BASE::get);

        assertTrue(after.isEmpty(), "no stat should still be modified: " + after);
        assertEquals(0.1, valueOf(Map.of(), SPEED), EPS);
    }

    @Test
    @DisplayName("H4 Given 玩家空手，迴避率 0 When 穿上迴避胸甲 Then 迴避率 = 0.15")
    void h4EvasionChestplate() {
        assertEquals(0.15, valueOf(loadout(EquipmentSlotKind.CHEST, EVASION_CHESTPLATE), DODGE), EPS);
    }

    @Test
    @DisplayName("H7 Given 玩家主手拿短刀 When 查詢攻擊力 Then 1.0 ＋ 2 = 3.0")
    void h7DaggerAttack() {
        assertEquals(3.0, valueOf(loadout(EquipmentSlotKind.MAINHAND, DAGGER), ATTACK), EPS);
    }

    @Test
    @DisplayName("B1 Given 重劍在主手 When 把重劍移到副手 Then 減速不生效：武器只在主手生效")
    void b1OffhandDoesNotCount() {
        assertEquals(0.1, valueOf(loadout(EquipmentSlotKind.OFFHAND, HEAVY_SWORD), SPEED), EPS);
    }

    @Test
    @DisplayName("B1 補充：胸甲拿在手上也不生效")
    void b1ChestplateInHandDoesNotCount() {
        assertEquals(0.0, valueOf(loadout(EquipmentSlotKind.MAINHAND, EVASION_CHESTPLATE), DODGE), EPS);
    }

    @Test
    @DisplayName("B2 Given 主手重劍（−30%）、身穿重甲（−15%） When 計算移動速度 Then 相乘：0.1 × 0.7 × 0.85 = 0.0595")
    void b2SlowdownsMultiply() {
        var worn = loadout(EquipmentSlotKind.MAINHAND, HEAVY_SWORD, EquipmentSlotKind.CHEST, HEAVY_ARMOR);

        assertEquals(0.0595, valueOf(worn, SPEED), EPS);
    }

    @Test
    @DisplayName("B3 Given 疊了很多減速裝備 When 算出來的移動速度低於下限 Then 夾在下限：不低於基礎值的 30%（0.03）")
    void b3SpeedFloor() {
        var lead = EquipmentCatalog.builder(ModStats.CATALOG)
            .add(HEAVY_SWORD)
            .add(EquipmentDefinition.of("lead_helmet", EquipmentSlotKind.HEAD, "minecraft:iron_helmet", percent(SPEED, -0.5)))
            .add(EquipmentDefinition.of("lead_boots", EquipmentSlotKind.FEET, "minecraft:iron_boots", percent(SPEED, -0.5)))
            .build();
        var worn = Map.of(
            EquipmentSlotKind.MAINHAND, EquipmentId.of("heavy_sword"),
            EquipmentSlotKind.HEAD, EquipmentId.of("lead_helmet"),
            EquipmentSlotKind.FEET, EquipmentId.of("lead_boots"));

        assertEquals(0.03, valueOf(new LoadoutCalculator(lead), worn, SPEED), EPS); // raw 0.0175
    }

    @Test
    @DisplayName("B4 Given 疊了很多迴避裝備 When 迴避率加總超過上限 Then 夾在上限 75%")
    void b4DodgeCap() {
        var builder = EquipmentCatalog.builder(ModStats.CATALOG);
        var worn = new EnumMap<EquipmentSlotKind, EquipmentId>(EquipmentSlotKind.class);
        for (EquipmentSlotKind slot : EquipmentSlotKind.values()) {
            if (slot.isArmor()) {
                String id = "evasive_" + slot.name().toLowerCase();
                builder.add(EquipmentDefinition.of(id, slot, "minecraft:chainmail_chestplate", add(DODGE, 0.3)));
                worn.put(slot, EquipmentId.of(id));
            }
        }

        assertEquals(0.75, valueOf(new LoadoutCalculator(builder.build()), worn, DODGE), EPS); // raw 1.2
    }

    @Test
    @DisplayName("B11／B12 Given 玩家拿重劍時死亡重生或登出再登入 When 依當下裝備重新計算 Then 移動速度 = 0.07，不會重複疊加成 0.049")
    void b11b12RecomputeDoesNotStack() {
        var worn = loadout(EquipmentSlotKind.MAINHAND, HEAVY_SWORD);

        assertEquals(0.07, valueOf(worn, SPEED), EPS);
        assertEquals(0.07, valueOf(worn, SPEED), EPS);
        assertEquals(calculator.compute(worn, PLAYER_BASE::get), calculator.compute(worn, PLAYER_BASE::get));
    }

    @Test
    @DisplayName("B13 Given 玩家主手拿原版鑽石劍（不是我們的裝備） When 查詢暴擊率、迴避率 Then 都是預設值 0")
    void b13VanillaItemAddsNothing() {
        Map<EquipmentSlotKind, EquipmentId> worn = Map.of(); // vanilla items never enter the loadout

        assertEquals(0.0, valueOf(worn, CRIT), EPS);
        assertEquals(0.0, valueOf(worn, DODGE), EPS);
    }

    @Test
    @DisplayName("不在登記表裡的裝備 id 不提供任何數值")
    void unknownEquipmentIgnored() {
        var worn = Map.of(EquipmentSlotKind.MAINHAND, EquipmentId.of("removed_sword"));

        assertTrue(calculator.compute(worn, PLAYER_BASE::get).isEmpty());
    }

    @Test
    @DisplayName("短刀同時改三個數值：攻擊 3.0、暴擊 0.25、移速 0.11")
    void daggerChangesEveryListedStat() {
        var worn = loadout(EquipmentSlotKind.MAINHAND, DAGGER);

        assertEquals(3.0, valueOf(worn, ATTACK), EPS);
        assertEquals(0.25, valueOf(worn, CRIT), EPS);
        assertEquals(0.11, valueOf(worn, SPEED), EPS);
    }
}
