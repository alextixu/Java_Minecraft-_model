package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Module tests: the stat folder becomes the stats the game runs with (section E). The conditions for it to work
 * (25-stat-files-given-when-then.md): files readable and valid, built-in stats present and of the same kind, no two
 * stats on one vanilla attribute. Each is broken in turn.
 */
class StatLoaderTest {
    private static final StatId LUCK = StatId.of("luck");
    private static final StatId JUMP = StatId.of("jump");
    private static final String LUCK_FILE = "{ \"format\": 1, \"name\": \"Luck\", \"default\": 0.25, \"min\": 0, \"max\": 1 }";
    private static final String JUMP_FILE =
        "{ \"format\": 1, \"attribute\": \"minecraft:jump_strength\", \"default\": 0.42, \"min\": 0, \"max\": 32 }";

    private final StatLoader loader = new StatLoader(ModStats.CATALOG);

    private InMemoryStatStore installed() throws Exception {
        InMemoryStatStore store = new InMemoryStatStore();
        loader.installIfMissing(store);
        return store;
    }

    @Test
    @DisplayName("H1 第一次啟動：寫出 7 個檔案，讀回來和 ModStats 一樣，沒有用到內建值、沒有名稱")
    void firstStartIsTheSameAsBefore() throws Exception {
        StatSetup setup = loader.load(installed());

        assertEquals(ModStats.CATALOG.all(), setup.catalog().all());
        assertEquals(List.of(), setup.builtInsFromCode());
        assertEquals(Map.of(), setup.names());
        assertTrue(setup.synced(ModStats.CRIT_CHANCE.id()));
    }

    @Test
    @DisplayName("H2、H4 多放兩個檔：自訂的 luck 和對應原版跳躍力的 jump，排在內建數值後面，照檔名排序")
    void extraFilesAddStats() throws Exception {
        InMemoryStatStore store = installed().putFile("luck.json", LUCK_FILE).putFile("jump.json", JUMP_FILE);

        StatSetup setup = loader.load(store);

        assertEquals(ModStats.CATALOG.size() + 2, setup.catalog().size());
        List<StatDefinition> all = setup.catalog().all();
        assertEquals(JUMP, all.get(ModStats.CATALOG.size()).id());
        assertEquals(LUCK, all.get(ModStats.CATALOG.size() + 1).id());
        assertTrue(setup.catalog().get(LUCK).isCustom());
        assertEquals("minecraft:jump_strength", setup.catalog().get(JUMP).vanillaAttribute().orElseThrow());
        assertEquals(Map.of(LUCK, "Luck"), setup.names());
    }

    @Test
    @DisplayName("H5 內建的 crit_chance.json 預設值改成 0.05 → 用檔案的數字")
    void builtInFilesCanChangeTheNumbers() throws Exception {
        InMemoryStatStore store = installed()
            .putFile("crit_chance.json", "{ \"format\": 1, \"default\": 0.05, \"min\": 0, \"max\": 1 }");

        assertEquals(0.05, loader.load(store).catalog().get(ModStats.CRIT_CHANCE.id()).defaultValue());
    }

    @Test
    @DisplayName("B4 刪掉了 crit_chance.json → 用內建值、記下來（啟動時寫警告），不寫回檔案")
    void missingBuiltInUsesTheBuiltInValue() throws Exception {
        InMemoryStatStore store = installed();
        store.deleteFile("crit_chance.json");

        StatSetup setup = loader.load(store);

        assertEquals(ModStats.CRIT_CHANCE, setup.catalog().get(ModStats.CRIT_CHANCE.id()));
        assertEquals(List.of(ModStats.CRIT_CHANCE.id()), setup.builtInsFromCode());
        assertTrue(store.file("crit_chance.json").isEmpty(), "not written back (Q5 A)");
    }

    @Test
    @DisplayName("B4 資料夾存在但是空的 → 7 個都用內建值")
    void emptyFolderUsesEveryBuiltIn() throws Exception {
        InMemoryStatStore store = new InMemoryStatStore().putFile("readme.txt", "my notes");

        StatSetup setup = loader.load(store);

        assertEquals(ModStats.CATALOG.all(), setup.catalog().all());
        assertEquals(ModStats.CATALOG.size(), setup.builtInsFromCode().size());
    }

    @Test
    @DisplayName("B5 內建數值改種類（攻擊力改成自訂、暴擊率改成原版屬性、攻擊力改指向護甲）→ 全部列出，拒絕")
    void builtInsKeepTheirKind() throws Exception {
        InMemoryStatStore store = installed()
            .putFile("attack_damage.json", "{ \"format\": 1, \"default\": 1, \"min\": 0, \"max\": 2048 }")
            .putFile("crit_chance.json", "{ \"format\": 1, \"attribute\": \"minecraft:luck\", \"default\": 0, \"min\": 0, \"max\": 1 }")
            .putFile("armor.json", "{ \"format\": 1, \"attribute\": \"minecraft:armor_toughness\", \"default\": 0, \"min\": 0, \"max\": 30 }");

        StatLoadException e = assertThrows(StatLoadException.class, () -> loader.load(store));

        assertEquals(3, e.problems().size(), e.getMessage());
        assertTrue(e.problems().get(0).startsWith("armor.json: attribute:"), e.getMessage());
        assertTrue(e.getMessage().contains("must keep \"attribute\": \"minecraft:attack_damage\""), e.getMessage());
        assertTrue(e.getMessage().contains("crit_chance.json: attribute:"), e.getMessage());
    }

    @Test
    @DisplayName("B6 兩個數值指向同一個原版屬性 → 拒絕")
    void twoStatsOnOneAttributeAreRefused() throws Exception {
        InMemoryStatStore store = installed()
            .putFile("speed2.json", "{ \"format\": 1, \"attribute\": \"minecraft:movement_speed\", \"default\": 0.1, \"min\": 0, \"max\": 1 }");

        StatLoadException e = assertThrows(StatLoadException.class, () -> loader.load(store));
        assertTrue(e.getMessage().contains("speed2.json"), e.getMessage());
    }

    @Test
    @DisplayName("B1、B3、Q9 A 檔名不是 id、內容寫錯、數字不合理 → 所有問題一次列出，每一個都有檔名")
    void everyProblemIsListedWithItsFile() throws Exception {
        InMemoryStatStore store = installed()
            .putFile("Jump.json", JUMP_FILE)
            .putFile("2x.json", LUCK_FILE)
            .putFile("luck.json", "{ \"format\": 1, \"default\": \"high\", \"min\": 0, \"max\": 1 }")
            .putFile("power.json", "{ \"format\": 1, \"default\": 5, \"min\": 0, \"max\": 1 }");

        StatLoadException e = assertThrows(StatLoadException.class, () -> loader.load(store));

        assertEquals(4, e.problems().size(), e.getMessage());
        assertTrue(e.problems().contains("2x.json: the file name must be a stat id (lower-case letters, digits and _, starting with a letter)"));
        assertTrue(e.getMessage().contains("Jump.json: the file name"), e.getMessage());
        assertTrue(e.getMessage().contains("luck.json: default: must be a number"), e.getMessage());
        assertTrue(e.getMessage().contains("power.json: Stat 'power': default value 5.0 is outside"), e.getMessage());
    }

    @Test
    @DisplayName("O2 sync: false 的自訂數值 → 不同步；其他都同步")
    void syncCanBeTurnedOff() throws Exception {
        InMemoryStatStore store = installed()
            .putFile("luck.json", "{ \"format\": 1, \"default\": 0, \"min\": 0, \"max\": 1, \"sync\": false }");

        StatSetup setup = loader.load(store);

        assertFalse(setup.synced(LUCK));
        assertTrue(setup.synced(ModStats.DODGE_CHANCE.id()));
    }

    @Test
    @DisplayName("Q7 A 名稱變成語系檔：attribute.name.mobarmory.<id> → 名稱；沒設名稱的不出現")
    void namesBecomeALanguageFile() throws Exception {
        StatSetup setup = loader.load(installed().putFile("luck.json", LUCK_FILE).putFile("jump.json", JUMP_FILE));

        String lang = setup.languageFile("mobarmory");

        assertTrue(lang.contains("\"attribute.name.mobarmory.luck\": \"Luck\""), lang);
        assertFalse(lang.contains("jump"), lang);
        assertEquals("attribute.name.mobarmory.luck", StatSetup.translationKey("mobarmory", LUCK));
    }
}
