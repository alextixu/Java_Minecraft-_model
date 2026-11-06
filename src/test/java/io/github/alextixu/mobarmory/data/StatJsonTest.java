package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Component tests: one stat file, read and written (section E, 25-stat-files-given-when-then.md B1, B2). */
class StatJsonTest {
    private static final StatId LUCK = StatId.of("luck");

    @Test
    @DisplayName("H2 自訂數值：名稱、預設值、上下限、同步都讀得到；沒有 attribute 就是自己的屬性")
    void readsANewAttribute() {
        StatEntry entry = StatJson.read(LUCK, """
            { "format": 1, "name": "Luck", "default": 0.25, "min": 0, "max": 1, "sync": false }""");

        assertEquals(StatDefinition.custom("luck", 0.25, 0, 1), entry.definition());
        assertEquals(Optional.of("Luck"), entry.name());
        assertFalse(entry.sync());
    }

    @Test
    @DisplayName("H4 對應原版屬性：attribute、floor 讀得到；sync 預設 true")
    void readsAVanillaBackedStat() {
        StatEntry entry = StatJson.read(StatId.of("jump"), """
            { "format": 1, "attribute": "minecraft:jump_strength", "default": 0.42, "min": 0, "max": 32, "floor": 0.5 }""");

        assertEquals(StatDefinition.vanilla("jump", "minecraft:jump_strength", 0.42, 0, 32).withFloorRatio(0.5), entry.definition());
        assertTrue(entry.sync());
        assertEquals(Optional.empty(), entry.name());
    }

    static Stream<StatDefinition> builtIns() {
        return ModStats.CATALOG.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builtIns")
    @DisplayName("H1 每個內建數值寫成檔案再讀回來，完全一樣")
    void builtInsRoundTrip(StatDefinition builtIn) {
        String text = StatJson.write(StatEntry.of(builtIn));
        assertEquals(StatEntry.of(builtIn), StatJson.read(builtIn.id(), text), text);
    }

    @Test
    @DisplayName("O5 寫出來的檔案：整數不帶 .0，floor 是 0 和 sync 是 true 時不寫")
    void writesCleanFiles() {
        String speed = StatJson.write(StatEntry.of(ModStats.MOVEMENT_SPEED));
        assertTrue(speed.contains("\"floor\": 0.3"), speed);
        String damage = StatJson.write(StatEntry.of(ModStats.ATTACK_DAMAGE));
        assertTrue(damage.contains("\"max\": 2048") && !damage.contains("2048.0"), damage);
        assertFalse(damage.contains("floor") || damage.contains("sync") || damage.contains("name"), damage);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "不是 JSON              | { broken                                                              | file:",
        "不是物件                | [1, 2]                                                                | file:",
        "少了 format             | { \"default\": 0, \"min\": 0, \"max\": 1 }                            | format:",
        "format 不是 1           | { \"format\": 2, \"default\": 0, \"min\": 0, \"max\": 1 }             | format:",
        "少了 default            | { \"format\": 1, \"min\": 0, \"max\": 1 }                             | default: missing",
        "數字寫成文字            | { \"format\": 1, \"default\": \"0\", \"min\": 0, \"max\": 1 }         | default: must be a number",
        "不認得的欄位            | { \"format\": 1, \"default\": 0, \"min\": 0, \"max\": 1, \"color\": 1 } | color: unknown field",
        "sync 不是 true／false   | { \"format\": 1, \"default\": 0, \"min\": 0, \"max\": 1, \"sync\": 1 } | sync: must be true or false",
        "名稱是空的              | { \"format\": 1, \"name\": \" \", \"default\": 0, \"min\": 0, \"max\": 1 } | name: must be 1 to 64",
        "原版屬性設了名稱        | { \"format\": 1, \"attribute\": \"minecraft:luck\", \"name\": \"L\", \"default\": 0, \"min\": 0, \"max\": 1 } | name: only a new attribute",
        "原版屬性設了 sync       | { \"format\": 1, \"attribute\": \"minecraft:luck\", \"sync\": false, \"default\": 0, \"min\": 0, \"max\": 1 } | sync: only a new attribute",
    })
    @DisplayName("B1 格式錯誤 → 錯誤訊息以出錯的欄位開頭")
    void formatMistakesNameTheField(String what, String text, String expectedStart) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> StatJson.read(LUCK, text));
        assertTrue(e.getMessage().startsWith(expectedStart), what + ": " + e.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "預設值超出上限  | { \"format\": 1, \"default\": 2, \"min\": 0, \"max\": 1 }           | outside",
        "下限大於上限    | { \"format\": 1, \"default\": 0, \"min\": 1, \"max\": 0 }           | greater than",
        "floor 超過 1    | { \"format\": 1, \"default\": 0, \"min\": 0, \"max\": 1, \"floor\": 2 } | floor ratio",
        "原版屬性的名字不對 | { \"format\": 1, \"attribute\": \"Jump\", \"default\": 0, \"min\": 0, \"max\": 1 } | vanilla attribute must look like",
    })
    @DisplayName("B2 數字不合理 → 拒絕，訊息說出原因")
    void badNumbersAreRefused(String what, String text, String expectedPart) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> StatJson.read(LUCK, text));
        assertTrue(e.getMessage().contains(expectedPart), what + ": " + e.getMessage());
    }
}
