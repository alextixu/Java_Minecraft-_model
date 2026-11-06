package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Module tests for the mob files (R4b): the store contract (folder and memory), the defaults, the loader and the editor
 * (22, H1, H9, B9, B15, B16, B18, B19).
 */
class MobFilesTest {
    private static final StatId ATTACK = ModStats.ATTACK_DAMAGE.id();

    @TempDir
    static Path folder;
    private static int next;

    /** Both stores, each starting empty. */
    static Stream<MobStore> stores() {
        return Stream.of(new InMemoryMobStore(), new MobFolder(folder.resolve("run" + next++)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stores")
    @DisplayName("H1 契約：第一次啟動放入兩個預設檔；之後不再放，也不覆蓋玩家的檔")
    void defaultsOnlyOnTheFirstStart(MobStore store) throws IOException {
        assertFalse(store.exists());
        assertTrue(DefaultMobs.installIfMissing(store));
        assertEquals(List.of("brute.json", "skirmisher.json"), store.mobFiles().stream().map(MobStore.MobFile::fileName).toList());

        store.writeMob(MobId.of("brute"), "{\"changed\": true}");
        assertFalse(DefaultMobs.installIfMissing(store));
        assertEquals("{\"changed\": true}", store.mobFiles().get(0).text());
    }

    @Test
    @DisplayName("B9 載入：一次列出所有壞檔，每個都寫檔名；檔名不是合法 id 也算")
    void loaderListsEveryProblem() {
        InMemoryMobStore store = new InMemoryMobStore();
        store.put("My Goblin.json", DefaultMobs.text("brute"));
        store.put("orc.json", DefaultMobs.text("brute").replace("\"zombie\"", "\"creeper\""));
        store.put("brute.json", DefaultMobs.text("brute"));

        var error = assertThrows(MobLoadException.class, () -> new MobLoader(ModStats.CATALOG).load(store));
        assertEquals(2, error.problems().size(), error.getMessage());
        assertTrue(error.problems().get(0).startsWith("My Goblin.json: the file name is the mob id"), error.problems().get(0));
        assertTrue(error.problems().get(1).startsWith("orc.json: look.body"), error.problems().get(1));
    }

    private static MobEditor editor(InMemoryMobStore store) throws Exception {
        DefaultMobs.installIfMissing(store);
        MobLoader loader = new MobLoader(ModStats.CATALOG);
        return new MobEditor(new MobLibrary(MobCatalog.builder(ModStats.CATALOG).addAll(loader.load(store)).build()), store, loader);
    }

    private static MobDefinition bruteWithAttack(MobEditor editor, double attack) throws Exception {
        String text = MobFileJson.write(editor.library().current().get(DefaultMobs.BRUTE))
            .replaceAll("\"attack_damage\": [0-9.]+", "\"attack_damage\": " + attack);
        return MobFileJson.read(DefaultMobs.BRUTE, text, ModStats.CATALOG);
    }

    @Test
    @DisplayName("H4 B19 存檔先寫檔再改遊戲；兩次存檔，後存的贏")
    void saveWritesTheFileFirst() throws Exception {
        InMemoryMobStore store = new InMemoryMobStore();
        MobEditor editor = editor(store);
        editor.update(bruteWithAttack(editor, 8));
        editor.update(bruteWithAttack(editor, 9));

        assertEquals(9.0, editor.library().current().get(DefaultMobs.BRUTE).stats().get(ATTACK));
        assertTrue(store.text("brute.json").contains("\"attack_damage\": 9"), store.text("brute.json"));
    }

    @Test
    @DisplayName("B18 寫檔失敗 → IOException，遊戲裡的定義不變")
    void failedWriteChangesNothing() throws Exception {
        InMemoryMobStore store = new InMemoryMobStore();
        MobEditor editor = editor(store);
        store.failWrites = true;

        assertThrows(IOException.class, () -> editor.update(bruteWithAttack(editor, 8)));
        assertEquals(4.0, editor.library().current().get(DefaultMobs.BRUTE).stats().get(ATTACK));
    }

    @Test
    @DisplayName("B16 新增：已有的 id → 欄位 id；新的 → 寫檔、待重開")
    void create() throws Exception {
        InMemoryMobStore store = new InMemoryMobStore();
        MobEditor editor = editor(store);
        MobDefinition orc = MobFileJson.read(MobId.of("orc"), DefaultMobs.text("brute").replace("Brute", "Orc"), ModStats.CATALOG);

        var taken = assertThrows(WeaponFormatException.class, () -> editor.create(bruteWithAttack(editor, 4)));
        assertEquals("id", taken.field());
        editor.create(orc);
        assertTrue(store.text("orc.json").contains("\"Orc\""));
        assertEquals(orc, editor.library().pending().get(MobId.of("orc")));
    }

    @Test
    @DisplayName("H9 B15 重新載入：手改的檔生效；有一個壞檔就什麼都不改")
    void reload() throws Exception {
        InMemoryMobStore store = new InMemoryMobStore();
        MobEditor editor = editor(store);
        store.put("brute.json", DefaultMobs.text("brute").replace("\"max_health\": 40", "\"max_health\": 60"));

        assertEquals(List.of(DefaultMobs.BRUTE), editor.reload().changed());
        store.put("bad.json", "[]");
        store.put("brute.json", DefaultMobs.text("brute"));
        assertThrows(MobLoadException.class, editor::reload);
        assertEquals(60.0, editor.library().current().get(DefaultMobs.BRUTE).stats().get(ModStats.MAX_HEALTH.id()));
    }

    @Test
    @DisplayName("資料夾版：檔案放在 mobs/<id>.json，內容是寫進去的文字")
    void folderLayout() throws IOException {
        MobFolder store = new MobFolder(folder.resolve("layout"));
        store.writeMob(MobId.of("goblin"), "{}\n");

        assertEquals("{}\n", Files.readString(folder.resolve("layout/mobs/goblin.json")));
        assertEquals(folder.resolve("layout/mobs"), store.mobsFolder());
    }
}
