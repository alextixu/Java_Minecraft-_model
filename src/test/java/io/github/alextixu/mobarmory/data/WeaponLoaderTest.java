package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Module tests: the conditions for a weapon file to load (09, success condition 1), broken one at a time. */
class WeaponLoaderTest {
    /** Every item exists except the made-up laser sword (B4). */
    private static final WeaponLoader LOADER = new WeaponLoader(ModStats.CATALOG, item -> !item.equals("minecraft:laser_sword"));
    private static final String HALBERD = """
        {"format": 1, "name": "Halberd", "slot": "mainhand", "look": {"texture": "halberd_netherite"},
         "modifiers": [{"stat": "attack_damage", "add": 9}, {"stat": "movement_speed", "percent": -20}]}""";

    private static InMemoryWeaponStore defaults() {
        InMemoryWeaponStore store = new InMemoryWeaponStore();
        DefaultWeapons.IDS.forEach(id -> store.putFile(id + ".json", DefaultWeapons.text(id)));
        return store;
    }

    private static String problems(InMemoryWeaponStore store) {
        var error = assertThrows(WeaponLoadException.class, () -> LOADER.load(store));
        return String.join("\n", error.problems());
    }

    @Test
    @DisplayName("H2 Given 資料夾多了 halberd.json 和它的貼圖 When 載入 Then 有 5 件，戟的數值照檔案（Java 一行都沒改）")
    void loadsDefaultsAndACustomWeapon() throws Exception {
        InMemoryWeaponStore store = defaults().putFile("halberd.json", HALBERD).putTexture("halberd_netherite", TestPng.of(32, 32));

        List<EquipmentDefinition> loaded = LOADER.load(store);

        assertEquals(5, loaded.size());
        EquipmentDefinition halberd = loaded.stream().filter(d -> d.id().equals(EquipmentId.of("halberd"))).findFirst().orElseThrow();
        assertEquals("Halberd", halberd.name());
        assertEquals(2, halberd.modifiers().size());
    }

    @Test
    @DisplayName("B1 Given dagger.json 少一個逗號 When 載入 Then 失敗，訊息有檔名和行號")
    void syntaxError() {
        String text = problems(defaults().putFile("dagger.json", DefaultWeapons.text("dagger").replaceFirst("\"Dagger\",", "\"Dagger\"")));
        assertTrue(text.contains("dagger.json"), text);
        assertTrue(text.contains("line"), text);
    }

    @Test
    @DisplayName("B2 Given 修飾寫了不存在的數值 block_chance When 載入 Then 失敗，訊息有檔名和數值")
    void unknownStat() {
        String text = problems(defaults().putFile("dagger.json", DefaultWeapons.text("dagger").replace("crit_chance", "block_chance")));
        assertTrue(text.contains("dagger.json") && text.contains("block_chance"), text);
    }

    @Test
    @DisplayName("B3 Given 百分比寫 −150 When 載入 Then 失敗，訊息有檔名和欄位")
    void outOfRange() {
        String text = problems(defaults().putFile("heavy_sword.json", DefaultWeapons.text("heavy_sword").replace("-30", "-150")));
        assertTrue(text.contains("heavy_sword.json") && text.contains("modifiers[1].percent"), text);
    }

    @Test
    @DisplayName("B4 Given 外觀是不存在的原版物品、或貼圖不存在、或貼圖不是 2 的次方的正方形 When 載入 Then 失敗，每個都列出來")
    void badLooks() {
        InMemoryWeaponStore store = defaults()
            .putFile("heavy_sword.json", DefaultWeapons.text("heavy_sword").replace("minecraft:netherite_sword", "minecraft:laser_sword"))
            .putFile("halberd.json", HALBERD)
            .putFile("spear.json", HALBERD.replace("halberd_netherite", "spear_iron"))
            .putTexture("spear_iron", TestPng.of(32, 16));

        String text = problems(store);

        assertTrue(text.contains("heavy_sword.json: look.item") && text.contains("laser_sword"), text);
        assertTrue(text.contains("halberd.json: look.texture") && text.contains("halberd_netherite.png"), text);
        assertTrue(text.contains("spear.json: look.texture") && text.contains("square"), text);
    }

    @Test
    @DisplayName("B5 Given 檔名是 Heavy Sword.json When 載入 Then 失敗，提示 id 只能用小寫英數和底線")
    void badFileName() {
        String text = problems(defaults().putFile("Heavy Sword.json", DefaultWeapons.text("heavy_sword")));
        assertTrue(text.contains("Heavy Sword.json") && text.contains("lowercase"), text);
    }

    @Test
    @DisplayName("全部合法時不丟例外；不是 .json 的檔案不管")
    void otherFilesAreIgnored() throws Exception {
        assertEquals(4, LOADER.load(defaults().putFile("README.txt", "not a weapon")).size());
    }
}
