package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.combo.ComboCatalog;
import io.github.alextixu.mobarmory.core.combo.ComboInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Module tests: the combo files while the game runs, with the weapon files' rules: defaults on the first start, a wrong
 * file stops the start, {@code /mobarmory reload} is all or nothing.
 */
class ComboFilesTest {
    private static final ComboLoader LOADER = new ComboLoader(ComboEffectTypes.STANDARD);

    private static ComboFiles start(Path root) {
        return ComboFiles.load(new JsonFolder(root.resolve("combos")), LOADER);
    }

    @Test
    @DisplayName("第一次啟動：裝好預設檔，Lunge 和 Follow-up 生效")
    void firstStart(@TempDir Path root) {
        ComboFiles files = start(root);
        assertEquals(2, files.library().current().all().size());
        assertTrue(Files.isRegularFile(root.resolve("combos/lunge.json")));
    }

    @Test
    @DisplayName("啟動時有一個檔寫錯：遊戲開不起來，訊息寫出檔名（和武器檔的 O7 一樣）")
    void aWrongFileStopsTheStart(@TempDir Path root) throws Exception {
        new JsonFolder(root.resolve("combos")).write("broken", "{");
        var e = assertThrows(IllegalStateException.class, () -> start(root));
        assertTrue(e.getMessage().contains("broken.json"), e.getMessage());
    }

    @Test
    @DisplayName("reload：新放的檔立刻生效，刪掉的檔立刻消失（combo 不用註冊任何東西）")
    void reloadAppliesNewAndDeletedFiles(@TempDir Path root) throws Exception {
        ComboFiles files = start(root);
        files.folder().write("double_slash", """
            {"name": "Double Slash", "sequence": ["walk", "walk"], "window_ticks": 20,
             "effect": {"type": "damage_multiplier", "value": 2}}
            """);
        Files.delete(root.resolve("combos/follow_up.json"));

        assertEquals(List.of("double_slash", "lunge"), files.reload());

        ComboCatalog now = files.library().current();
        assertTrue(now.find(ComboInput.WALK, ComboInput.WALK).isPresent());
        assertTrue(now.find(ComboInput.RUN, ComboInput.WALK).isEmpty());
    }

    @Test
    @DisplayName("reload 時有一個檔寫錯：全部不變，舊的 combo 照樣生效（和武器檔的 O8 一樣）")
    void aWrongFileOnReloadChangesNothing(@TempDir Path root) throws Exception {
        ComboFiles files = start(root);
        ComboCatalog before = files.library().current();
        files.folder().write("double_slash", "{\"name\": \"Double Slash\"}");

        var e = assertThrows(WeaponLoadException.class, files::reload);

        assertTrue(e.problems().getFirst().startsWith("double_slash.json: "), e.getMessage());
        assertSame(before, files.library().current());
    }
}
