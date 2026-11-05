package io.github.alextixu.mobarmory.core.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: the stat catalog on its own. */
class StatCatalogTest {

    private static final StatDefinition MOVEMENT_SPEED =
        StatDefinition.vanilla("movement_speed", "minecraft:movement_speed", 0.1, 0, 1024).withFloorRatio(0.3);
    private static final StatDefinition DODGE = StatDefinition.custom("dodge_chance", 0, 0, 0.75);
    private static final StatDefinition CRIT = StatDefinition.custom("crit_chance", 0, 0, 1.0);

    @Test
    @DisplayName("登記後可以用 id 查到同一筆定義，順序和登記順序相同")
    void registerAndLookUp() {
        StatCatalog catalog = StatCatalog.builder().add(MOVEMENT_SPEED).add(DODGE).add(CRIT).build();

        assertEquals(DODGE, catalog.get(StatId.of("dodge_chance")));
        assertEquals(List.of(MOVEMENT_SPEED, DODGE, CRIT), catalog.all());
        assertEquals(3, catalog.size());
        assertTrue(catalog.contains(StatId.of("crit_chance")));
    }

    @Test
    @DisplayName("查不存在的數值：find 回傳空，get 丟出寫有 id 的例外")
    void unknownStat() {
        StatCatalog catalog = StatCatalog.builder().add(DODGE).build();

        assertTrue(catalog.find(StatId.of("block_chance")).isEmpty());
        var error = assertThrows(NoSuchElementException.class, () -> catalog.get(StatId.of("block_chance")));
        assertTrue(error.getMessage().contains("block_chance"));
    }

    @Test
    @DisplayName("H3（元件層）Given 登記表新增一個數值「格擋率」 When 建立登記表 Then 它出現在要掛到所有生物的自訂數值清單裡")
    void newCustomStatIsListedForAllMobs() {
        StatDefinition block = StatDefinition.custom("block_chance", 0, 0, 0.5);
        StatCatalog catalog = StatCatalog.builder().add(MOVEMENT_SPEED).add(DODGE).add(block).build();

        assertEquals(List.of(DODGE, block), catalog.custom());
    }

    @Test
    @DisplayName("B6 Given 兩個數值用了同一個 id When 建立登記表 Then 失敗，訊息寫出重複的 id")
    void duplicateIdFails() {
        var builder = StatCatalog.builder().add(DODGE);
        StatDefinition again = StatDefinition.custom("dodge_chance", 0.1, 0, 1);

        var error = assertThrows(IllegalArgumentException.class, () -> builder.add(again));
        assertTrue(error.getMessage().contains("dodge_chance"), error.getMessage());
    }

    @Test
    @DisplayName("兩個數值對應到同一個原版 Attribute 時失敗（否則同一個效果會被套兩次）")
    void sameVanillaAttributeTwiceFails() {
        var builder = StatCatalog.builder().add(MOVEMENT_SPEED);
        StatDefinition alias = StatDefinition.vanilla("speed", "minecraft:movement_speed", 0.1, 0, 1024);

        var error = assertThrows(IllegalArgumentException.class, () -> builder.add(alias));
        assertTrue(error.getMessage().contains("minecraft:movement_speed"), error.getMessage());
    }

    @Test
    @DisplayName("B7 Given 數值定義的預設值不在上下限內 When 建立定義 Then 失敗，訊息寫出是哪個數值")
    void defaultOutsideRangeFails() {
        var error = assertThrows(IllegalArgumentException.class,
            () -> StatDefinition.custom("dodge_chance", 0.9, 0, 0.75));
        assertTrue(error.getMessage().contains("dodge_chance"), error.getMessage());
    }

    @Test
    @DisplayName("定義不合理時失敗：下限大於上限、數字不是有限值、下限比例不在 0～1、id 格式錯")
    void invalidDefinitionsFail() {
        assertThrows(IllegalArgumentException.class, () -> StatDefinition.custom("a", 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> StatDefinition.custom("a", Double.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> StatDefinition.custom("a", 0, 0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> DODGE.withFloorRatio(1.5));
        assertThrows(IllegalArgumentException.class, () -> StatId.of("Dodge Chance"));
        assertThrows(IllegalArgumentException.class, () -> StatId.of(""));
        assertThrows(IllegalArgumentException.class,
            () -> StatDefinition.vanilla("speed", "movement speed", 0.1, 0, 1));
    }

    @Test
    @DisplayName("自訂數值和原版數值分得出來")
    void customVersusVanilla() {
        assertTrue(DODGE.isCustom());
        assertFalse(MOVEMENT_SPEED.isCustom());
    }
}
