package io.github.alextixu.mobarmory.core.equipment;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: the equipment catalog validates definitions against the stat catalog. */
class EquipmentCatalogTest {
    private static final StatDefinition SPEED =
        StatDefinition.vanilla("movement_speed", "minecraft:movement_speed", 0.1, 0, 1024).withFloorRatio(0.3);
    private static final StatDefinition DODGE = StatDefinition.custom("dodge_chance", 0, 0, 0.75);
    private static final StatCatalog STATS = StatCatalog.builder().add(SPEED).add(DODGE).build();

    private static final EquipmentDefinition HEAVY_SWORD = EquipmentDefinition.of(
        "heavy_sword", EquipmentSlotKind.MAINHAND, "minecraft:netherite_sword", percent(SPEED.id(), -0.30));

    @Test
    @DisplayName("登記後可以用 id 查到裝備；查不存在的裝備時 get 丟出寫有 id 的例外")
    void registerAndLookUp() {
        EquipmentCatalog catalog = EquipmentCatalog.builder(STATS).add(HEAVY_SWORD).build();

        assertEquals(HEAVY_SWORD, catalog.get(EquipmentId.of("heavy_sword")));
        assertEquals(List.of(HEAVY_SWORD), catalog.all());
        var error = assertThrows(NoSuchElementException.class, () -> catalog.get(EquipmentId.of("spear")));
        assertTrue(error.getMessage().contains("spear"));
    }

    @Test
    @DisplayName("B5 Given 某件裝備的定義引用了不存在的數值 When 建立裝備登記表 Then 失敗，訊息寫出是哪件裝備、哪個數值")
    void unknownStatFails() {
        EquipmentDefinition blocker = EquipmentDefinition.of(
            "tower_shield", EquipmentSlotKind.OFFHAND, "minecraft:shield", add(StatId.of("block_chance"), 0.2));
        var builder = EquipmentCatalog.builder(STATS);

        var error = assertThrows(IllegalArgumentException.class, () -> builder.add(blocker));
        assertTrue(error.getMessage().contains("tower_shield"), error.getMessage());
        assertTrue(error.getMessage().contains("block_chance"), error.getMessage());
    }

    @Test
    @DisplayName("B14 Given 同一件裝備被定義了兩次同一個數值的修飾 When 建立定義 Then 失敗：一件裝備對同一個數值只能有一個修飾")
    void sameStatTwiceFails() {
        var error = assertThrows(IllegalArgumentException.class, () -> EquipmentDefinition.of(
            "odd_sword", EquipmentSlotKind.MAINHAND, "minecraft:iron_sword",
            percent(SPEED.id(), -0.1), percent(SPEED.id(), -0.2)));
        assertTrue(error.getMessage().contains("odd_sword"), error.getMessage());
        assertTrue(error.getMessage().contains("movement_speed"), error.getMessage());
    }

    @Test
    @DisplayName("兩件裝備用了同一個 id 時失敗")
    void duplicateIdFails() {
        var builder = EquipmentCatalog.builder(STATS).add(HEAVY_SWORD);

        var error = assertThrows(IllegalArgumentException.class, () -> builder.add(HEAVY_SWORD));
        assertTrue(error.getMessage().contains("heavy_sword"), error.getMessage());
    }

    @Test
    @DisplayName("外觀必須是原版物品 id 的格式")
    void lookMustBeAnItemId() {
        assertThrows(IllegalArgumentException.class, () -> EquipmentDefinition.of(
            "heavy_sword", EquipmentSlotKind.MAINHAND, "netherite sword", percent(SPEED.id(), -0.30)));
    }
}
