package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Component tests for the shared data folder (weapons and combos use the same one) and the default combo files:
 * installed on the very first start only, exactly like the default weapons.
 */
class JsonFolderTest {

    @Test
    @DisplayName("第一次啟動：combos 資料夾不存在 → 建立兩個預設檔；之後讀得到")
    void firstStartInstallsTheDefaults(@TempDir Path root) throws Exception {
        JsonFolder combos = new JsonFolder(root.resolve("combos"));
        assertFalse(combos.exists());

        assertTrue(DefaultCombos.installIfMissing(combos));

        assertEquals(List.of("follow_up.json", "lunge.json"),
            combos.files().stream().map(JsonFolder.DataFile::fileName).toList(), "sorted by file name");
        assertEquals(DefaultCombos.text("lunge"), Files.readString(root.resolve("combos/lunge.json"), StandardCharsets.UTF_8));
        assertEquals(2, new ComboLoader(ComboEffectTypes.STANDARD).load(combos).all().size());
    }

    @Test
    @DisplayName("資料夾已經在：不裝預設檔，玩家刪光了也不補回來")
    void anExistingFolderIsNeverTouched(@TempDir Path root) throws Exception {
        JsonFolder combos = new JsonFolder(root.resolve("combos"));
        Files.createDirectories(combos.path());

        assertFalse(DefaultCombos.installIfMissing(combos));
        assertTrue(combos.files().isEmpty());
    }

    @Test
    @DisplayName("只讀 .json，id 是檔名去掉 .json；寫入的 id 不能跳出資料夾")
    void readsJsonOnlyAndKeepsWritesInside(@TempDir Path root) throws Exception {
        JsonFolder folder = new JsonFolder(root.resolve("combos"));
        folder.write("spin", "{}");
        Files.writeString(folder.path().resolve("notes.txt"), "not a combo");

        List<JsonFolder.DataFile> files = folder.files();
        assertEquals(1, files.size());
        assertEquals("spin", files.getFirst().id());
        for (String bad : List.of("../spin", "Spin", "a/b", "")) {
            assertThrows(IllegalArgumentException.class, () -> folder.write(bad, "{}"), bad);
        }
    }

    @Test
    @DisplayName("寫入是整份換掉：舊內容被新內容取代，不留暫存檔")
    void writesReplaceTheWholeFile(@TempDir Path root) throws Exception {
        JsonFolder folder = new JsonFolder(root.resolve("combos"));
        folder.write("spin", "{\"old\": true}");
        folder.write("spin", "{\"new\": true}");
        assertEquals("{\"new\": true}", folder.files().getFirst().text());
        try (var listing = Files.list(folder.path())) {
            assertEquals(1, listing.count(), "no temporary file left behind");
        }
    }
}
