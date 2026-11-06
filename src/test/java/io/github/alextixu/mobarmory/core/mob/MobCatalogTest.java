package io.github.alextixu.mobarmory.core.mob;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Component and module tests for mob definitions: the catalog accepts a valid mob and rejects each broken
 * success condition from 06 (B1-B5) at startup, naming the mob and the problem. Uses its own small stat and
 * equipment catalogs, so it does not depend on where the real equipment comes from (R2b).
 */
class MobCatalogTest {
    private static final StatDefinition HEALTH = StatDefinition.vanilla("max_health", "minecraft:max_health", 20, 1, 1024);
    private static final StatDefinition SPEED =
        StatDefinition.vanilla("movement_speed", "minecraft:movement_speed", 0.1, 0, 1024).withFloorRatio(0.3);
    private static final StatDefinition DODGE = StatDefinition.custom("dodge_chance", 0, 0, 0.75);
    private static final StatDefinition CRIT = StatDefinition.custom("crit_chance", 0, 0, 1.0);
    private static final StatCatalog STATS = StatCatalog.builder().add(HEALTH).add(SPEED).add(DODGE).add(CRIT).build();

    private static final EquipmentCatalog EQUIPMENT = EquipmentCatalog.builder(STATS)
        .add(EquipmentDefinition.of("heavy_sword", EquipmentSlotKind.MAINHAND, "minecraft:netherite_sword",
            percent(SPEED.id(), -0.30)))
        .add(EquipmentDefinition.of("evasion_chestplate", EquipmentSlotKind.CHEST, "minecraft:chainmail_chestplate",
            add(DODGE.id(), 0.15)))
        .build();

    private static MobDefinition.Builder brute() {
        return MobDefinition.builder("brute")
            .look(MobLook.of(MobLook.Body.ZOMBIE).scaled(1.2))
            .stat(HEALTH, 40)
            .stat(SPEED, 0.25)
            .equip(EquipmentSlotKind.MAINHAND, "heavy_sword")
            .behavior(new WanderBehavior(1.0))
            .behavior(new AttackPlayersBehavior(1.0));
    }

    private static MobCatalog.Builder catalog() {
        return MobCatalog.builder(STATS, EQUIPMENT);
    }

    /** Asserts the call fails with a message that names every given word. */
    private static void failsNaming(Executable call, String... words) {
        var error = assertThrows(IllegalArgumentException.class, call::execute);
        for (String word : words) {
            assertTrue(error.getMessage().contains(word), "message should name '" + word + "': " + error.getMessage());
        }
    }

    @Test
    @DisplayName("H1 Given 一筆合法的怪物定義 When 建立怪物登記表 Then 可以用 id 查到同一筆定義；查不存在的怪物時 get 丟出寫有 id 的例外")
    void registerAndLookUp() {
        MobDefinition definition = brute().build();
        MobCatalog mobs = catalog().add(definition).build();

        assertEquals(definition, mobs.get(MobId.of("brute")));
        assertEquals(List.of(definition), mobs.all());
        var error = assertThrows(NoSuchElementException.class, () -> mobs.get(MobId.of("ogre")));
        assertTrue(error.getMessage().contains("ogre"));
    }

    @Test
    @DisplayName("B1 Given 怪物定義寫了不存在的數值 block_chance When 建立怪物登記表 Then 啟動失敗，訊息寫出是哪隻怪物、哪個數值")
    void unknownStatFails() {
        MobDefinition blocker = brute()
            .stat(StatDefinition.custom("block_chance", 0, 0, 1), 0.2)
            .build();

        failsNaming(() -> catalog().add(blocker), "brute", "block_chance");
    }

    @Test
    @DisplayName("B2 Given 怪物的迴避率寫 0.9（上限 0.75） When 建立怪物登記表 Then 啟動失敗，不默默夾到 0.75")
    void statOutOfRangeFails() {
        MobDefinition tooSlippery = brute().stat(DODGE, 0.9).build();

        failsNaming(() -> catalog().add(tooSlippery), "brute", "dodge_chance", "0.9");
    }

    @Test
    @DisplayName("B3 Given 怪物的裝備引用不存在的裝備 When 建立怪物登記表 Then 啟動失敗，寫出怪物和裝備")
    void unknownEquipmentFails() {
        MobDefinition spearman = MobDefinition.builder("spearman")
            .look(MobLook.of(MobLook.Body.SKELETON))
            .equip(EquipmentSlotKind.MAINHAND, "spear")
            .build();

        failsNaming(() -> catalog().add(spearman), "spearman", "spear");
    }

    @Test
    @DisplayName("B3 Given 怪物把重劍放在胸甲欄 When 建立怪物登記表 Then 啟動失敗，寫出怪物、裝備和欄位")
    void equipmentInWrongSlotFails() {
        MobDefinition confused = MobDefinition.builder("confused")
            .look(MobLook.of(MobLook.Body.ZOMBIE))
            .equip(EquipmentSlotKind.CHEST, "heavy_sword")
            .build();

        failsNaming(() -> catalog().add(confused), "confused", "heavy_sword", "CHEST");
    }

    @Test
    @DisplayName("B5 Given 兩隻怪物用同一個 id When 建立怪物登記表 Then 啟動失敗")
    void duplicateIdFails() {
        var builder = catalog().add(brute().build());

        failsNaming(() -> builder.add(brute().build()), "brute");
    }

    @Test
    @DisplayName("B6 Given 定義的行為清單是空的 When 建立怪物登記表 Then 允許：站著不動的木樁")
    void noBehaviorsIsAllowed() {
        MobDefinition dummy = MobDefinition.builder("training_dummy")
            .look(MobLook.of(MobLook.Body.HUSK))
            .stat(HEALTH, 100)
            .equip(EquipmentSlotKind.CHEST, "evasion_chestplate")
            .build();

        assertEquals(List.of(), catalog().add(dummy).build().get(MobId.of("training_dummy")).behaviors());
    }

    @Test
    @DisplayName("B7 Given 定義沒寫暴擊率 When 算生成時的數值 Then 每個數值都有值：有寫的用定義的值，沒寫的用登記表的預設值")
    void unsetStatsUseCatalogDefaults() {
        var values = brute().build().spawnStats(STATS);

        assertEquals(STATS.size(), values.size());
        assertEquals(40.0, values.get(HEALTH.id()));
        assertEquals(0.25, values.get(SPEED.id()));
        assertEquals(0.0, values.get(CRIT.id()));
        assertEquals(0.0, values.get(DODGE.id()));
    }

    @Test
    @DisplayName("定義本身不合理時建立就失敗：同一個數值寫兩次、同一個欄位放兩件、同一種行為加兩次、沒有外觀")
    void contradictoryDefinitionsFail() {
        failsNaming(() -> brute().stat(HEALTH, 50), "brute", "max_health");
        failsNaming(() -> brute().equip(EquipmentSlotKind.MAINHAND, "dagger"), "brute", "MAINHAND");
        failsNaming(() -> brute().behavior(new WanderBehavior(0.5)).build(), "brute", "wander");
        var noLook = assertThrows(NullPointerException.class, () -> MobDefinition.builder("ghost").build());
        assertTrue(noLook.getMessage().contains("ghost"), noLook.getMessage());
    }

    @Test
    @DisplayName("行為參數不合法時建立就失敗：速度倍率必須是正數")
    void behaviorParametersAreChecked() {
        failsNaming(() -> new WanderBehavior(0), "wander", "speed");
        failsNaming(() -> new AttackPlayersBehavior(-1), "attack_players", "speed");
        failsNaming(() -> new AttackPlayersBehavior(Double.NaN), "attack_players", "speed");
    }

    @Test
    @DisplayName("外觀的大小倍率要在 Minecraft 允許的 0.0625～16 之間；id 格式和其他 id 一樣")
    void lookAndIdAreChecked() {
        failsNaming(() -> MobLook.of(MobLook.Body.ZOMBIE).scaled(0), "scale");
        failsNaming(() -> MobLook.of(MobLook.Body.ZOMBIE).scaled(20), "scale");
        failsNaming(() -> MobId.of("Big Brute"), "Big Brute");
    }

    /** A call that may throw, so the helper can take lambdas returning anything. */
    @FunctionalInterface
    private interface Executable {
        void execute() throws Throwable;
    }
}
