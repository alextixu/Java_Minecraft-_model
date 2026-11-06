package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.mob.LeapBehavior;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLook;
import io.github.alextixu.mobarmory.core.mob.NeutralBehavior;
import io.github.alextixu.mobarmory.core.mob.WanderBehavior;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Component tests for the mob file format (R4b O1; 22, H2, B1 to B7). */
class MobFileJsonTest {
    private static final MobId GOBLIN = MobId.of("goblin");
    private static final String VALID = """
        {"format": 1, "name": "Goblin", "look": {"body": "husk", "scale": 0.8},
         "stats": {"max_health": 12, "attack_damage": 3, "movement_speed": 0.3},
         "equipment": {"mainhand": "dagger"},
         "behaviors": [{"type": "wander"}, {"type": "neutral", "speed": 1.3}, {"type": "leap"}]}""";

    private static MobDefinition read(String text) throws WeaponFormatException {
        return MobFileJson.read(GOBLIN, text, ModStats.CATALOG);
    }

    @Test
    @DisplayName("H2 讀一個玩家寫的怪物檔：名稱、外觀、數值、裝備、行為；沒寫速度就是 1.0")
    void readsAPlayersFile() throws Exception {
        MobDefinition goblin = read(VALID);

        assertEquals("Goblin", goblin.name());
        assertEquals(new MobLook(MobLook.Body.HUSK, 0.8), goblin.look());
        assertEquals(12.0, goblin.stats().get(ModStats.MAX_HEALTH.id()));
        assertEquals(EquipmentId.of("dagger"), goblin.equipment().get(EquipmentSlotKind.MAINHAND));
        assertEquals(List.of(new WanderBehavior(1.0), new NeutralBehavior(1.3), new LeapBehavior()), goblin.behaviors());
    }

    @Test
    @DisplayName("寫出來再讀回去，一模一樣；整數寫成 40 而不是 40.0")
    void writesWhatItReads() throws Exception {
        MobDefinition goblin = read(VALID);
        String written = MobFileJson.write(goblin);

        assertEquals(goblin, read(written));
        assertTrue(written.contains("\"max_health\": 12,"), written);
        assertTrue(written.contains("\"type\": \"leap\"\n"), "leap has no speed: " + written);
    }

    @Test
    @DisplayName("B1 不是 JSON → 錯誤寫出行和欄")
    void brokenJsonNamesTheLine() {
        var error = assertThrows(WeaponFormatException.class, () -> read("{\"format\": 1,\n\"name\": }"));
        assertTrue(error.getMessage().contains("line 2"), error.getMessage());
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
        "\"name\": \"Goblin\"|\"name\": \"\"|name",
        "\"husk\"|\"creeper\"|look.body",
        "\"scale\": 0.8|\"scale\": 20|look.scale",
        "\"max_health\": 12|\"block_chance\": 0.5|stats.block_chance",
        "\"max_health\": 12|\"max_health\": 0|stats.max_health",
        "\"mainhand\": \"dagger\"|\"tail\": \"dagger\"|equipment.tail",
        "{\"type\": \"wander\"}|{\"type\": \"fly\"}|behaviors[0].type",
        "\"speed\": 1.3|\"speed\": 0|behaviors[1].speed",
        "\"speed\": 1.3|\"speed\": 1.3, \"range\": 9|behaviors[1].range",
        "{\"type\": \"wander\"}|{\"type\": \"attack_players\"}|behaviors",
        "\"format\": 1|\"format\": 2|format",
        "\"format\": 1|\"format\": 1, \"color\": \"red\"|color",
    })
    @DisplayName("B2～B7 每一種錯都標出欄位")
    void everyMistakeNamesItsField(String from, String to, String field) {
        String text = VALID.replace(from, to);
        var error = assertThrows(WeaponFormatException.class, () -> read(text), text);
        assertEquals(field, error.field(), error.getMessage());
    }

    @Test
    @DisplayName("B6 只有衝刺、沒有攻擊或中立 → 欄位 behaviors，說明要搭配哪一種")
    void leapNeedsATarget() {
        var error = assertThrows(WeaponFormatException.class, () -> read(VALID.replace("{\"type\": \"neutral\", \"speed\": 1.3}, ", "")));
        assertEquals("behaviors", error.field());
        assertTrue(error.reason().contains("needs attack_players or neutral"), error.reason());
    }
}
