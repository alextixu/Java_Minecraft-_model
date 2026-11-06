package io.github.alextixu.mobarmory.data;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Component tests: the weapon file format (decision O1). */
class WeaponJsonTest {
    private static final EquipmentId HALBERD = EquipmentId.of("halberd");

    private static EquipmentDefinition read(String json) throws WeaponFormatException {
        return WeaponJson.read(HALBERD, json, ModStats.CATALOG);
    }

    private static String halberd(String modifiers) {
        return """
            {"format": 1, "name": "Halberd", "slot": "mainhand", "look": {"texture": "halberd_netherite"},
             "modifiers": [%s]}""".formatted(modifiers);
    }

    @Test
    @DisplayName("H2 一把自訂貼圖的戟：攻擊力 +9、移動速度 −20%；百分比寫 −20 就是 −0.20")
    void readsAWeapon() throws Exception {
        EquipmentDefinition halberd = read(halberd("""
            {"stat": "attack_damage", "add": 9}, {"stat": "movement_speed", "percent": -20}"""));

        assertEquals(new EquipmentDefinition(HALBERD, "Halberd", EquipmentSlotKind.MAINHAND,
            List.of(add(ModStats.ATTACK_DAMAGE.id(), 9), percent(ModStats.MOVEMENT_SPEED.id(), -0.20)),
            EquipmentLook.texture("halberd_netherite")), halberd);
    }

    @Test
    @DisplayName("存了再讀回來一模一樣，數字寫成 6、-30、0.25（不會變成 6.0、-30.000000000000004、3E+1）")
    void writeThenReadIsTheSame() throws Exception {
        EquipmentDefinition dagger = new EquipmentDefinition(EquipmentId.of("dagger"), "短刀 Dagger", EquipmentSlotKind.MAINHAND,
            List.of(add(ModStats.ATTACK_DAMAGE.id(), 6), percent(ModStats.MOVEMENT_SPEED.id(), -0.30),
                add(ModStats.CRIT_CHANCE.id(), 0.25), percent(ModStats.ATTACK_SPEED.id(), 0.30)),
            EquipmentLook.item("minecraft:iron_sword"));

        String text = WeaponJson.write(dagger);

        assertEquals(dagger, WeaponJson.read(dagger.id(), text, ModStats.CATALOG));
        assertTrue(text.contains("\"add\": 6\n"), text);
        assertTrue(text.contains("\"percent\": -30\n"), text);
        assertTrue(text.contains("\"percent\": 30\n"), text);
        assertTrue(text.contains("\"add\": 0.25\n"), text);
        assertTrue(text.contains("短刀 Dagger"), "names are written as-is, not escaped");
    }

    @Test
    @DisplayName("沒有修飾、沒寫 format 都可以（format 預設 1）；Windows 記事本的 BOM 也讀得懂")
    void optionalParts() throws Exception {
        EquipmentDefinition plain = read("﻿{\"name\": \"Stick\", \"slot\": \"offhand\", \"look\": {\"item\": \"minecraft:stick\"}}");
        assertEquals(List.of(), plain.modifiers());
        assertEquals(EquipmentSlotKind.OFFHAND, plain.slot());
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @DisplayName("B1、B3、B10 寫錯時，錯誤指出是哪個欄位")
    @CsvSource(delimiter = '|', value = {
        "{\"name\": \"Halberd\", \"slot\": \"mainhand\", \"look\": {\"item\": \"minecraft:iron_sword\"},}|''",
        "{\"name\": \"Halberd\" \"slot\": \"mainhand\"}|''",
        "[1, 2]|''",
        "{\"format\": 2, \"name\": \"H\", \"slot\": \"mainhand\", \"look\": {\"item\": \"minecraft:iron_sword\"}}|format",
        "{\"slot\": \"mainhand\", \"look\": {\"item\": \"minecraft:iron_sword\"}}|name",
        "{\"name\": \"\", \"slot\": \"mainhand\", \"look\": {\"item\": \"minecraft:iron_sword\"}}|name",
        "{\"name\": \"H\", \"slot\": \"belt\", \"look\": {\"item\": \"minecraft:iron_sword\"}}|slot",
        "{\"name\": \"H\", \"slot\": \"mainhand\"}|look",
        "{\"name\": \"H\", \"slot\": \"mainhand\", \"look\": {\"item\": \"iron_sword\"}}|look.item",
        "{\"name\": \"H\", \"slot\": \"mainhand\", \"look\": {\"texture\": \"../x\"}}|look.texture",
        "{\"name\": \"H\", \"slot\": \"mainhand\", \"look\": {\"item\": \"minecraft:a\", \"texture\": \"b\"}}|look",
        "{\"name\": \"H\", \"slot\": \"mainhand\", \"look\": {\"item\": \"minecraft:a\"}, \"modifers\": []}|modifers",
    })
    void documentErrorsNameTheField(String json, String field) {
        var error = assertThrows(WeaponFormatException.class, () -> read(json));
        assertEquals(field, error.field(), error.getMessage());
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @DisplayName("B2、B3、B10 修飾寫錯時，錯誤指出是第幾條的哪一格")
    @CsvSource(delimiter = '|', value = {
        "{\"stat\": \"block_chance\", \"add\": 1}|modifiers[0].stat",
        "{\"stat\": \"attack_damage\", \"add\": \"abc\"}|modifiers[0].add",
        "{\"stat\": \"attack_damage\", \"add\": \"6\"}|modifiers[0].add",
        "{\"stat\": \"movement_speed\", \"percent\": -150}|modifiers[0].percent",
        "{\"stat\": \"movement_speed\", \"percent\": -100}|modifiers[0].percent",
        "{\"stat\": \"attack_damage\", \"add\": 1e400}|modifiers[0].add",
        "{\"stat\": \"attack_damage\"}|modifiers[0]",
        "{\"stat\": \"attack_damage\", \"add\": 1, \"percent\": 5}|modifiers[0]",
        "{\"stat\": \"attack_damage\", \"amount\": 1}|modifiers[0].amount",
        "{\"stat\": \"attack_damage\", \"add\": 1}, {\"stat\": \"attack_damage\", \"add\": 2}|modifiers",
    })
    void modifierErrorsNameTheField(String modifiers, String field) {
        var error = assertThrows(WeaponFormatException.class, () -> read(halberd(modifiers)));
        assertEquals(field, error.field(), error.getMessage());
    }

    @Test
    @DisplayName("B1 語法錯誤的訊息帶行號")
    void syntaxErrorHasALineNumber() {
        var error = assertThrows(WeaponFormatException.class, () -> read("{\n  \"name\": \"H\"\n  \"slot\": 1\n}"));
        assertTrue(error.getMessage().contains("line 3"), error.getMessage());
    }

    @Test
    @DisplayName("不存在的數值：訊息列出有哪些數值可以用")
    void unknownStatListsKnownOnes() {
        var error = assertThrows(WeaponFormatException.class,
            () -> read(halberd("{\"stat\": \"block_chance\", \"add\": 1}")));
        assertTrue(error.getMessage().contains("dodge_chance"), error.getMessage());
    }
}
