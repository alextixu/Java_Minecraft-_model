package io.github.alextixu.mobarmory.data;

import static io.github.alextixu.mobarmory.core.combo.ComboInput.RUN;
import static io.github.alextixu.mobarmory.core.combo.ComboInput.WALK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.combo.ComboCatalog;
import io.github.alextixu.mobarmory.core.combo.ComboDefinition;
import io.github.alextixu.mobarmory.core.combo.ComboTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Module tests for the combo files (19, Q2 changed to JSON): the shipped files are the decided combos, a new file is a
 * new combo with no Java change, and a wrong file is reported with its file name and field.
 */
class ComboLoaderTest {
    private static final ComboLoader LOADER = new ComboLoader(ComboEffectTypes.STANDARD);

    /** A player's own combo: walk, then walk again, within 1 second, deals double damage. */
    private static final String DOUBLE_SLASH = """
        {
          "format": 1,
          "name": "Double Slash",
          "sequence": ["walk", "walk"],
          "window_ticks": 20,
          "effect": { "type": "damage_multiplier", "value": 2 }
        }
        """;

    private static JsonFolder.DataFile file(String name, String text) {
        return new JsonFolder.DataFile(name, text);
    }

    private static String combo(String fields) {
        return "{" + fields + "}";
    }

    private static final String VALID_REST = "\"sequence\": [\"run\", \"run\"], \"window_ticks\": 30, "
        + "\"effect\": {\"type\": \"damage_multiplier\", \"value\": 1.5}";

    @Test
    @DisplayName("內附的兩個檔就是決定好的 combo：Lunge 走→跑 ×1.5、Follow-up 跑→走 ×1.3，時間窗 30 tick")
    void shippedFilesAreTheDecidedCombos() {
        ComboCatalog defaults = DefaultCombos.catalog();
        ComboDefinition lunge = defaults.find(WALK, RUN).orElseThrow();
        ComboDefinition followUp = defaults.find(RUN, WALK).orElseThrow();
        assertEquals(List.of("follow_up", "lunge"), defaults.all().stream().map(ComboDefinition::id).sorted().toList());
        assertEquals("Lunge", lunge.name());
        assertEquals("Follow-up", followUp.name());
        assertEquals(1.5, lunge.effect().apply(1.0), 1e-9);
        assertEquals(1.3, followUp.effect().apply(1.0), 1e-9);
        assertEquals(30, lunge.windowTicks());
        assertEquals(30, followUp.windowTicks());
        assertEquals(Optional.empty(), defaults.find(WALK, WALK), "O7: walk, walk is no combo in the shipped files");
    }

    @Test
    @DisplayName("可擴充：多放一個 JSON 就多一種 combo，不用改 Java")
    void oneMoreFileIsOneMoreCombo() throws Exception {
        List<JsonFolder.DataFile> files = new ArrayList<>(DefaultCombos.files());
        files.add(file("double_slash.json", DOUBLE_SLASH));

        ComboCatalog catalog = LOADER.read(files);

        assertEquals(3, catalog.all().size());
        ComboDefinition added = catalog.find(WALK, WALK).orElseThrow();
        assertEquals("double_slash", added.id());
        assertEquals("Double Slash", added.name());
        assertEquals(20, added.windowTicks());
        assertEquals(6.0, added.effect().apply(3.0), 1e-9);

        ComboTracker player = new ComboTracker();
        assertEquals(Optional.empty(), player.record(catalog, WALK, 100));
        assertEquals(Optional.of(added), player.record(catalog, WALK, 115), "the new combo triggers like the shipped ones");
        assertEquals(Optional.empty(), player.record(catalog, WALK, 200));
        assertEquals(Optional.empty(), player.record(catalog, WALK, 221), "with its own window of 20 ticks");
    }

    @Test
    @DisplayName("刪掉所有 combo 檔：沒有 combo，不是錯誤")
    void noFilesIsNoCombos() throws Exception {
        assertTrue(LOADER.read(List.of()).all().isEmpty());
    }

    @Test
    @DisplayName("格式欄位可以省略（和武器檔一樣）")
    void formatIsOptional() throws Exception {
        String text = combo("\"name\": \"Spin\", " + VALID_REST);
        assertEquals("Spin", LOADER.read(List.of(file("spin.json", text))).all().getFirst().name());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
        not json                 | {"name": "Spin",                                      | spin.json: not valid JSON
        unknown field            | {"name": "Spin", "power": 3, %REST%}                  | spin.json: power: unknown field
        missing name             | {%REST%}                                              | spin.json: name: missing
        empty name               | {"name": "  ", %REST%}                                | spin.json: name:
        format 2                 | {"format": 2, "name": "Spin", %REST%}                 | spin.json: format: this version of the mod reads format 1
        one swing                | {"name": "Spin", "sequence": ["run"], "window_ticks": 30, "effect": {"type": "damage_multiplier", "value": 1.5}} | spin.json: sequence: must be a list of two swings
        unknown swing            | {"name": "Spin", "sequence": ["run", "jump"], "window_ticks": 30, "effect": {"type": "damage_multiplier", "value": 1.5}} | spin.json: sequence[1]: unknown swing 'jump'
        window 0                 | {"name": "Spin", "sequence": ["run", "run"], "window_ticks": 0, "effect": {"type": "damage_multiplier", "value": 1.5}} | spin.json: window_ticks: must be a whole number of ticks from 1 to 1200
        window 1.5               | {"name": "Spin", "sequence": ["run", "run"], "window_ticks": 1.5, "effect": {"type": "damage_multiplier", "value": 1.5}} | spin.json: window_ticks: must be a whole number
        window as text           | {"name": "Spin", "sequence": ["run", "run"], "window_ticks": "30", "effect": {"type": "damage_multiplier", "value": 1.5}} | spin.json: window_ticks: must be a number
        missing effect           | {"name": "Spin", "sequence": ["run", "run"], "window_ticks": 30}                       | spin.json: effect: missing
        unknown effect           | {"name": "Spin", "sequence": ["run", "run"], "window_ticks": 30, "effect": {"type": "knockback"}} | spin.json: effect.type: unknown effect 'knockback' (known: damage_multiplier)
        multiplier 0             | {"name": "Spin", "sequence": ["run", "run"], "window_ticks": 30, "effect": {"type": "damage_multiplier", "value": 0}} | spin.json: effect.value: must be a number above 0
        typo in effect           | {"name": "Spin", "sequence": ["run", "run"], "window_ticks": 30, "effect": {"type": "damage_multiplier", "valeu": 2}} | spin.json: effect.valeu: unknown field
        """)
    @DisplayName("寫錯的檔：訊息寫出檔名和欄位")
    void wrongFileNamesFileAndField(String what, String text, String expectedStart) {
        String json = text.replace("%REST%", VALID_REST);
        var e = assertThrows(WeaponLoadException.class, () -> LOADER.read(List.of(file("spin.json", json))), what);
        assertEquals(1, e.problems().size(), e.getMessage());
        assertTrue(e.problems().getFirst().startsWith(expectedStart), what + ": " + e.problems().getFirst());
        assertTrue(e.getMessage().startsWith("Mob Armory combo files have 1 problem"), e.getMessage());
    }

    @Test
    @DisplayName("檔名就是 id：檔名不合法時，訊息說要改檔名")
    void badFileName() {
        String text = combo("\"name\": \"Spin\", " + VALID_REST);
        var e = assertThrows(WeaponLoadException.class, () -> LOADER.read(List.of(file("Spin Move.json", text))));
        assertTrue(e.problems().getFirst().startsWith("Spin Move.json: the file name is the combo id"), e.getMessage());
    }

    @Test
    @DisplayName("兩個檔同一個出招順序：拒絕，訊息寫出兩個 id")
    void twoFilesWithTheSameOrder() {
        List<JsonFolder.DataFile> files = new ArrayList<>(DefaultCombos.files());
        files.add(file("dash.json", DefaultCombos.text("lunge").replace("Lunge", "Dash")));
        var e = assertThrows(WeaponLoadException.class, () -> LOADER.read(files));
        assertTrue(e.getMessage().contains("dash") && e.getMessage().contains("lunge"), e.getMessage());
    }

    @Test
    @DisplayName("好幾個檔都錯：一次列出全部，每條寫自己的檔名（和武器檔一樣）")
    void everyProblemAtOnce() {
        var e = assertThrows(WeaponLoadException.class, () -> LOADER.read(List.of(
            file("a.json", "{"),
            file("b.json", combo(VALID_REST)),
            file("c.json", DefaultCombos.text("lunge")))));
        assertEquals(2, e.problems().size(), e.getMessage());
        assertTrue(e.problems().get(0).startsWith("a.json: ") && e.problems().get(1).startsWith("b.json: "), e.getMessage());
    }
}
