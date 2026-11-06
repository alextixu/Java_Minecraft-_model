package io.github.alextixu.mobarmory.core.equipment;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: replacing definitions while the game runs (09-weapon-data-web-given-when-then.md). */
class EquipmentLibraryTest {
    private static final StatDefinition ATTACK = StatDefinition.vanilla("attack_damage", "minecraft:attack_damage", 1.0, 0, 2048);
    private static final StatDefinition SPEED =
        StatDefinition.vanilla("movement_speed", "minecraft:movement_speed", 0.1, 0, 1024).withFloorRatio(0.3);
    private static final StatCatalog STATS = StatCatalog.builder().add(ATTACK).add(SPEED).build();
    private static final EquipmentId SWORD_ID = EquipmentId.of("heavy_sword");
    private static final EquipmentId DAGGER_ID = EquipmentId.of("dagger");

    private static final EquipmentDefinition SWORD = EquipmentDefinition.of(
        "heavy_sword", EquipmentSlotKind.MAINHAND, "minecraft:netherite_sword", add(ATTACK.id(), 6), percent(SPEED.id(), -0.30));
    private static final EquipmentDefinition DAGGER = EquipmentDefinition.of(
        "dagger", EquipmentSlotKind.MAINHAND, "minecraft:iron_sword", add(ATTACK.id(), 2));

    private static EquipmentLibrary library() {
        return new EquipmentLibrary(EquipmentCatalog.builder(STATS).add(SWORD).add(DAGGER).build());
    }

    private static EquipmentDefinition swordWithAttack(double attack) {
        return new EquipmentDefinition(SWORD_ID, "Heavy Sword", EquipmentSlotKind.MAINHAND,
            List.of(add(ATTACK.id(), attack), percent(SPEED.id(), -0.30)), EquipmentLook.item("minecraft:netherite_sword"));
    }

    private static double attackWithSword(LoadoutCalculator calculator) {
        return calculator.compute(Map.of(EquipmentSlotKind.MAINHAND, SWORD_ID), stat -> STATS.get(stat).defaultValue())
            .get(ATTACK.id());
    }

    @Test
    @DisplayName("H3/H5 Given 計算器讀的是 library When 把重劍攻擊力改成 +10 Then 下一次計算就是 11（R2 Q6：同種裝備共用一份定義）")
    void updateAppliesToNextCalculation() {
        EquipmentLibrary library = library();
        LoadoutCalculator calculator = new LoadoutCalculator(library::current);
        assertEquals(7.0, attackWithSword(calculator), 1e-9);

        library.update(swordWithAttack(10));

        assertEquals(11.0, attackWithSword(calculator), 1e-9);
        assertSame(DAGGER, library.current().get(DAGGER_ID), "other pieces stay as they were");
    }

    @Test
    @DisplayName("B11、O10 Given 兩個分頁先後存重劍（+10、+99） When 都存完 Then 後存的蓋掉先存的：+99")
    void lastSaveWins() {
        EquipmentLibrary library = library();
        library.update(swordWithAttack(10));

        library.update(swordWithAttack(99));

        assertEquals(swordWithAttack(99), library.current().get(SWORD_ID));
    }

    @Test
    @DisplayName("B9 Given 重劍在主手 When 把欄位改成胸甲 Then 拒絕：欄位要重開才能改")
    void slotCannotChangeWhileRunning() {
        EquipmentLibrary library = library();
        EquipmentDefinition moved = SWORD.withSlot(EquipmentSlotKind.CHEST);

        var error = assertThrows(IllegalArgumentException.class, () -> library.update(moved));

        assertTrue(error.getMessage().contains("restart"), error.getMessage());
        assertSame(SWORD, library.current().get(SWORD_ID));
    }

    @Test
    @DisplayName("B10 Given 新定義引用不存在的數值 When 更新 Then 拒絕，什麼都沒變")
    void invalidUpdateChangesNothing() {
        EquipmentLibrary library = library();
        EquipmentDefinition broken = new EquipmentDefinition(SWORD_ID, "Heavy Sword", EquipmentSlotKind.MAINHAND,
            List.of(add(StatId.of("block_chance"), 1)), EquipmentLook.item("minecraft:netherite_sword"));

        assertThrows(IllegalArgumentException.class, () -> library.update(broken));
        assertSame(SWORD, library.current().get(SWORD_ID));
    }

    @Test
    @DisplayName("更新不存在的裝備時丟出寫有 id 的例外")
    void unknownPieceIsNotFound() {
        EquipmentDefinition spear = EquipmentDefinition.of("spear", EquipmentSlotKind.MAINHAND, "minecraft:trident");
        var error = assertThrows(NoSuchElementException.class, () -> library().update(spear));
        assertTrue(error.getMessage().contains("spear"));
    }

    @Test
    @DisplayName("H3、B7、B8、B9 Given 檔案裡重劍改了、短刀的檔案不見了、多了長矛、匕首…… When 重新載入 Then 分別是：已套用、維持到重開、待重開")
    void reloadSortsChanges() {
        EquipmentLibrary library = library();
        EquipmentDefinition spear = EquipmentDefinition.of("spear", EquipmentSlotKind.MAINHAND, "minecraft:trident", add(ATTACK.id(), 4));

        ReloadReport report = library.replaceAll(List.of(swordWithAttack(10), spear));

        assertEquals(List.of(SWORD_ID), report.changed());
        assertEquals(List.of(EquipmentId.of("spear")), report.pendingRestart());
        assertEquals(List.of(DAGGER_ID), report.fileMissing());
        assertEquals(swordWithAttack(10), library.current().get(SWORD_ID));
        assertSame(DAGGER, library.current().get(DAGGER_ID), "B8: the dagger keeps its last definition until a restart");
        assertTrue(library.current().find(EquipmentId.of("spear")).isEmpty(), "B7: a new piece is not an item yet");
        assertEquals(spear, library.pending().get(EquipmentId.of("spear")));
    }

    @Test
    @DisplayName("B9 Given 檔案裡重劍的欄位改成副手、攻擊力也改了 When 重新載入 Then 攻擊力套用，欄位維持主手到重開")
    void reloadKeepsSlotUntilRestart() {
        EquipmentLibrary library = library();
        EquipmentDefinition moved = swordWithAttack(10).withSlot(EquipmentSlotKind.OFFHAND);

        ReloadReport report = library.replaceAll(List.of(moved, DAGGER));

        assertEquals(List.of(SWORD_ID), report.slotWaits());
        assertEquals(swordWithAttack(10), library.current().get(SWORD_ID));
    }

    @Test
    @DisplayName("B6 Given 有一個檔案寫錯（引用不存在的數值） When 重新載入 Then 整份不換，所有裝備維持舊數值")
    void invalidReloadChangesNothing() {
        EquipmentLibrary library = library();
        EquipmentDefinition broken = EquipmentDefinition.of(
            "dagger", EquipmentSlotKind.MAINHAND, "minecraft:iron_sword", add(StatId.of("block_chance"), 1));

        assertThrows(IllegalArgumentException.class, () -> library.replaceAll(List.of(swordWithAttack(10), broken)));

        assertSame(SWORD, library.current().get(SWORD_ID));
        assertSame(DAGGER, library.current().get(DAGGER_ID));
    }

    @Test
    @DisplayName("重新載入沒有變動的檔案時，報告裡沒有已套用的")
    void unchangedReloadChangesNothing() {
        EquipmentLibrary library = library();
        ReloadReport report = library.replaceAll(List.of(SWORD, DAGGER));
        assertEquals(List.of(), report.changed());
        assertSame(SWORD, library.current().get(SWORD_ID));
    }

    @Test
    @DisplayName("H9 Given 上傳一把新武器 When 加進待重開清單 Then 還不是物品；同一個 id 不能再上傳一次，也不能用既有武器的 id")
    void uploadWaitsForRestart() {
        EquipmentLibrary library = library();
        EquipmentDefinition spear = EquipmentDefinition.of("spear", EquipmentSlotKind.MAINHAND, "minecraft:trident");

        library.addPending(spear);

        assertEquals(spear, library.pending().get(EquipmentId.of("spear")));
        assertTrue(library.current().find(EquipmentId.of("spear")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> library.addPending(spear));
        assertThrows(IllegalArgumentException.class, () -> library.addPending(DAGGER));
    }

    @Test
    @DisplayName("名稱：沒寫時由 id 產生；空白、太長、有控制字元都不行；可以用中文")
    void names() {
        assertEquals("Heavy Sword", EquipmentDefinition.defaultName(SWORD_ID));
        assertEquals("重劍", new EquipmentDefinition(SWORD_ID, " 重劍 ", EquipmentSlotKind.MAINHAND, List.of(),
            EquipmentLook.item("minecraft:netherite_sword")).name());
        for (String bad : List.of("", "   ", "x".repeat(65), "a\nb")) {
            assertThrows(IllegalArgumentException.class, () -> new EquipmentDefinition(SWORD_ID, bad,
                EquipmentSlotKind.MAINHAND, List.of(), EquipmentLook.item("minecraft:netherite_sword")), bad);
        }
    }

    @Test
    @DisplayName("外觀：原版物品要有命名空間；貼圖名稱只能用小寫英數和底線（不能帶路徑）")
    void looks() {
        assertEquals("minecraft:iron_sword", ((EquipmentLook.VanillaItem) EquipmentLook.item("minecraft:iron_sword")).item());
        assertEquals("halberd_netherite", ((EquipmentLook.Texture) EquipmentLook.texture("halberd_netherite")).texture());
        for (String bad : List.of("iron_sword", "Minecraft:Iron", "")) {
            assertThrows(IllegalArgumentException.class, () -> EquipmentLook.item(bad), bad);
        }
        for (String bad : List.of("../secret", "Halberd", "a/b", "x.png", "")) {
            assertThrows(IllegalArgumentException.class, () -> EquipmentLook.texture(bad), bad);
        }
    }
}
