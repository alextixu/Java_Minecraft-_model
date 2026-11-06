package io.github.alextixu.mobarmory.core.mob;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Component tests for B3 as changed on 11/07 (decision O2 → B, review-02 6-3 a): a mob whose equipment is missing or
 * in the wrong slot is still created, spawns without that piece, and the problem names the mob, the piece and the slot.
 * Uses its own small catalogs, so it does not depend on the shipped weapon files.
 */
class MobEquipmentCheckTest {
    private static final StatDefinition SPEED =
        StatDefinition.vanilla("movement_speed", "minecraft:movement_speed", 0.1, 0, 1024).withFloorRatio(0.3);
    private static final StatDefinition DODGE = StatDefinition.custom("dodge_chance", 0, 0, 0.75);
    private static final StatCatalog STATS = StatCatalog.builder().add(SPEED).add(DODGE).build();

    private static final EquipmentDefinition SWORD = EquipmentDefinition.of("heavy_sword", EquipmentSlotKind.MAINHAND,
        "minecraft:netherite_sword", percent(SPEED.id(), -0.30));
    private static final EquipmentDefinition CHESTPLATE = EquipmentDefinition.of("evasion_chestplate", EquipmentSlotKind.CHEST,
        "minecraft:chainmail_chestplate", add(DODGE.id(), 0.15));

    private static final MobDefinition BRUTE = MobDefinition.builder("brute")
        .look(MobLook.of(MobLook.Body.ZOMBIE))
        .equip(EquipmentSlotKind.MAINHAND, "heavy_sword")
        .equip(EquipmentSlotKind.CHEST, "evasion_chestplate")
        .build();

    private static EquipmentCatalog weapons(EquipmentDefinition... pieces) {
        EquipmentCatalog.Builder builder = EquipmentCatalog.builder(STATS);
        for (EquipmentDefinition piece : pieces) {
            builder.add(piece);
        }
        return builder.build();
    }

    @Test
    @DisplayName("Given 怪物用的裝備都在、欄位也對 When 檢查 Then 全部拿到，沒有問題")
    void everythingThere() {
        var result = MobEquipmentCheck.check(BRUTE, weapons(SWORD, CHESTPLATE)::find);

        assertEquals(BRUTE.equipment(), result.given());
        assertEquals(List.of(), result.problems());
    }

    @Test
    @DisplayName("B3（11/07 改 B）Given heavy_sword.json 被刪掉 When 檢查狂戰士 Then 不拿重劍、胸甲照拿，問題寫出怪物、裝備和欄位")
    void missingPieceIsLeftOut() {
        var result = MobEquipmentCheck.check(BRUTE, weapons(CHESTPLATE)::find);

        assertEquals(Map.of(EquipmentSlotKind.CHEST, EquipmentId.of("evasion_chestplate")), result.given());
        assertEquals(1, result.problems().size());
        var problem = result.problems().getFirst();
        assertEquals(EquipmentId.of("heavy_sword"), problem.equipment());
        assertNull(problem.worksIn());
        for (String word : List.of("brute", "heavy_sword", "mainhand", "deleted")) {
            assertTrue(problem.message().contains(word), "message should name '" + word + "': " + problem.message());
        }
    }

    @Test
    @DisplayName("B3（11/07 改 B）Given 重劍的檔案把欄位改成副手 When 檢查狂戰士（主手拿重劍） Then 不拿重劍，問題寫出現在能用的欄位")
    void pieceInAnotherSlotIsLeftOut() {
        var result = MobEquipmentCheck.check(BRUTE, weapons(SWORD.withSlot(EquipmentSlotKind.OFFHAND), CHESTPLATE)::find);

        assertEquals(Map.of(EquipmentSlotKind.CHEST, EquipmentId.of("evasion_chestplate")), result.given());
        var problem = result.problems().getFirst();
        assertEquals(EquipmentSlotKind.OFFHAND, problem.worksIn());
        for (String word : List.of("brute", "heavy_sword", "mainhand", "offhand")) {
            assertTrue(problem.message().contains(word), "message should name '" + word + "': " + problem.message());
        }
    }

    @Test
    @DisplayName("B3（11/07 改 B）Given 怪物的裝備一件都不存在 When 建立怪物登記表 Then 不會啟動失敗；生成時兩件都不拿")
    void catalogAcceptsMobWhoseEquipmentIsGone() {
        MobCatalog mobs = MobCatalog.builder(STATS).add(BRUTE).build();

        var result = MobEquipmentCheck.check(mobs.get(BRUTE.id()), weapons()::find);

        assertEquals(Map.of(), result.given());
        assertEquals(2, result.problems().size());
    }

    @Test
    @DisplayName("Given 怪物沒有裝備 When 檢查 Then 什麼都不拿，也沒有問題")
    void noEquipmentNoProblems() {
        MobDefinition bare = MobDefinition.builder("bare").look(MobLook.of(MobLook.Body.HUSK)).build();

        var result = MobEquipmentCheck.check(bare, weapons()::find);

        assertEquals(Map.of(), result.given());
        assertEquals(List.of(), result.problems());
    }
}
