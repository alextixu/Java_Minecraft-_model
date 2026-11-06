package io.github.alextixu.mobarmory.data;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import io.github.alextixu.mobarmory.core.equipment.StaleVersionException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Module tests: changing weapons while the game runs, files first (09, success conditions 5 and 6). */
class WeaponEditorTest {
    private static final EquipmentId SWORD = EquipmentId.of("heavy_sword");

    private final InMemoryWeaponStore store = new InMemoryWeaponStore();
    private final EquipmentLibrary library;
    private final WeaponEditor editor;

    WeaponEditorTest() throws Exception {
        DefaultWeapons.installIfMissing(store);
        WeaponLoader loader = new WeaponLoader(ModStats.CATALOG, item -> !item.equals("minecraft:laser_sword"));
        EquipmentCatalog.Builder catalog = EquipmentCatalog.builder(ModStats.CATALOG);
        loader.load(store).forEach(catalog::add);
        library = new EquipmentLibrary(catalog.build());
        editor = new WeaponEditor(library, store, loader);
    }

    private static EquipmentDefinition sword(double attack, EquipmentLook look, EquipmentSlotKind slot) {
        return new EquipmentDefinition(SWORD, "Heavy Sword", slot, List.of(add(ModStats.ATTACK_DAMAGE.id(), attack),
            percent(ModStats.MOVEMENT_SPEED.id(), -0.30), percent(ModStats.ATTACK_SPEED.id(), -0.40)), look);
    }

    private static EquipmentDefinition sword(double attack) {
        return sword(attack, EquipmentLook.item("minecraft:netherite_sword"), EquipmentSlotKind.MAINHAND);
    }

    private String swordFile() {
        return store.file("heavy_sword.json").orElseThrow();
    }

    @Test
    @DisplayName("H5 Given 重劍 +6 When 網頁存成 +16 Then 檔案寫成 16、遊戲裡的定義也是 16、版本 2")
    void saveWritesTheFileThenApplies() throws Exception {
        long version = editor.update(sword(16), 1);

        assertEquals(2, version);
        assertEquals(sword(16), library.current().get(SWORD));
        assertTrue(swordFile().contains("\"add\": 16"), swordFile());
    }

    @Test
    @DisplayName("H7 Given 網頁存過 +16 When 重開遊戲（重新讀檔） Then 仍是 +16")
    void savedValueSurvivesARestart() throws Exception {
        editor.update(sword(16), 1);

        List<EquipmentDefinition> afterRestart = new WeaponLoader(ModStats.CATALOG, item -> true).load(store);

        assertTrue(afterRestart.contains(sword(16)));
    }

    @Test
    @DisplayName("B11 Given 別的分頁已經存過 When 用舊版本號再存 Then 409，檔案和遊戲都維持第一次存的")
    void staleSaveChangesNothing() throws Exception {
        editor.update(sword(16), 1);
        String file = swordFile();

        assertThrows(StaleVersionException.class, () -> editor.update(sword(99), 1));

        assertEquals(file, swordFile());
        assertEquals(sword(16), library.current().get(SWORD));
    }

    @Test
    @DisplayName("B9 Given 重劍在主手 When 網頁把欄位改成胸甲 Then 400，欄位是 slot，什麼都沒寫")
    void slotCannotChange() {
        String file = swordFile();
        var error = assertThrows(WeaponFormatException.class,
            () -> editor.update(sword(6, EquipmentLook.item("minecraft:netherite_sword"), EquipmentSlotKind.CHEST), 1));

        assertEquals("slot", error.field());
        assertEquals(file, swordFile());
    }

    @Test
    @DisplayName("B4 Given 外觀選了不存在的物品或沒上傳的貼圖 When 存檔 Then 400，指出 look.item／look.texture")
    void lookMustExist() {
        var item = assertThrows(WeaponFormatException.class,
            () -> editor.update(sword(6, EquipmentLook.item("minecraft:laser_sword"), EquipmentSlotKind.MAINHAND), 1));
        var texture = assertThrows(WeaponFormatException.class,
            () -> editor.update(sword(6, EquipmentLook.texture("nothing_here"), EquipmentSlotKind.MAINHAND), 1));

        assertEquals("look.item", item.field());
        assertEquals("look.texture", texture.field());
        assertEquals(1, library.version(SWORD));
    }

    @Test
    @DisplayName("B12 Given 檔案寫不進去 When 存檔 Then 丟出錯誤，遊戲裡的數值不變（先寫檔、成功才套用）")
    void failedWriteChangesNothing() {
        WeaponStore readOnly = new InMemoryWeaponStore() {
            @Override
            public void writeWeapon(EquipmentId id, String json) {
                throw new java.io.UncheckedIOException(new IOException("read-only"));
            }
        };
        WeaponEditor broken = new WeaponEditor(library, readOnly, new WeaponLoader(ModStats.CATALOG, item -> true));

        assertThrows(java.io.UncheckedIOException.class, () -> broken.update(sword(16), 1));
        assertEquals(1, library.version(SWORD));
        assertEquals(6.0, library.current().get(SWORD).modifiers().get(0).amount());
    }

    @Test
    @DisplayName("H3 Given 手動把檔案改成 +10 When 重新載入 Then 套用，報告列出重劍")
    void reloadAppliesHandEdits() throws Exception {
        store.putFile("heavy_sword.json", WeaponJson.write(sword(10)));

        ReloadReport report = editor.reload();

        assertEquals(List.of(SWORD), report.changed());
        assertEquals(sword(10), library.current().get(SWORD));
    }

    @Test
    @DisplayName("B6 Given 一個檔案寫壞、另一個改對 When 重新載入 Then 丟出錯誤列出檔名，兩個都維持舊的")
    void brokenReloadChangesNothing() {
        EquipmentDefinition before = library.current().get(SWORD);
        store.putFile("heavy_sword.json", WeaponJson.write(sword(10)));
        store.putFile("dagger.json", "{ broken");

        var error = assertThrows(WeaponLoadException.class, editor::reload);

        assertTrue(error.getMessage().contains("dagger.json"), error.getMessage());
        assertSame(before, library.current().get(SWORD));
    }

    @Test
    @DisplayName("H9 Given 網頁的新增武器表單（數值＋內建貼圖） When 存檔 Then 寫出檔案，狀態是待重開；同一個 id 不能再新增")
    void uploadWaitsForRestart() throws Exception {
        EquipmentDefinition spear = new EquipmentDefinition(EquipmentId.of("spear"), "長矛", EquipmentSlotKind.MAINHAND,
            List.of(add(ModStats.ATTACK_DAMAGE.id(), 5)), EquipmentLook.texture("spear_iron"));

        editor.create(spear);

        assertTrue(store.file("spear.json").isPresent());
        assertEquals(spear, library.pending().get(spear.id()));
        assertEquals("id", assertThrows(WeaponFormatException.class, () -> editor.create(spear)).field());
        assertEquals("id", assertThrows(WeaponFormatException.class, () -> editor.create(sword(1))).field());
    }
}
