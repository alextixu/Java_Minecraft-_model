package io.github.alextixu.mobarmory.data;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

/**
 * Migration guard (08, section 5, contract 3): the shipped JSON files are exactly the four pieces that used to be
 * written in Java ({@code content/ModEquipment}, R2 decision O7 and N3). The expected values below are a copy of
 * that Java code, so the guard keeps working after the Java version is deleted.
 */
class DefaultWeaponsTest {
    static final List<EquipmentDefinition> FORMER_JAVA_DEFINITIONS = List.of(
        EquipmentDefinition.of("heavy_sword", EquipmentSlotKind.MAINHAND, "minecraft:netherite_sword",
            add(ModStats.ATTACK_DAMAGE.id(), 6),
            percent(ModStats.MOVEMENT_SPEED.id(), -0.30),
            percent(ModStats.ATTACK_SPEED.id(), -0.40)),
        EquipmentDefinition.of("dagger", EquipmentSlotKind.MAINHAND, "minecraft:iron_sword",
            add(ModStats.ATTACK_DAMAGE.id(), 2),
            add(ModStats.CRIT_CHANCE.id(), 0.25),
            percent(ModStats.MOVEMENT_SPEED.id(), 0.10)),
        EquipmentDefinition.of("evasion_chestplate", EquipmentSlotKind.CHEST, "minecraft:chainmail_chestplate",
            add(ModStats.DODGE_CHANCE.id(), 0.15)),
        EquipmentDefinition.of("heavy_armor", EquipmentSlotKind.CHEST, "minecraft:iron_chestplate",
            add(ModStats.ARMOR.id(), 8),
            percent(ModStats.MOVEMENT_SPEED.id(), -0.15)));

    @ParameterizedTest(name = "{0}")
    @FieldSource("FORMER_JAVA_DEFINITIONS")
    @DisplayName("遷移：JSON 讀出來的和原本的 Java 定義完全相同（名稱、欄位、外觀、每一條修飾）")
    void jsonEqualsTheFormerJavaDefinition(EquipmentDefinition expected) {
        assertEquals(expected, DefaultWeapons.catalog(ModStats.CATALOG).get(expected.id()));
    }

    @Test
    @DisplayName("遷移：件數和順序也一樣")
    void sameSet() {
        assertEquals(FORMER_JAVA_DEFINITIONS, DefaultWeapons.catalog(ModStats.CATALOG).all());
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("io.github.alextixu.mobarmory.data.DefaultWeapons#IDS")
    @DisplayName("內建檔案存了再讀回來，內容一字不差（網頁存檔不會把格式弄亂）")
    void shippedFilesAreInCanonicalForm(String id) throws Exception {
        String shipped = DefaultWeapons.text(id).replace("\r\n", "\n");
        assertEquals(shipped, WeaponJson.write(WeaponJson.read(EquipmentId.of(id), shipped, ModStats.CATALOG)));
    }

    @Test
    @DisplayName("H1 Given 沒有設定資料夾 When 第一次啟動 Then 寫入 4 件預設；之後就算玩家刪光檔案，也不會再寫")
    void installsOnlyOnTheFirstStart() throws Exception {
        InMemoryWeaponStore store = new InMemoryWeaponStore();

        assertTrue(DefaultWeapons.installIfMissing(store));
        assertEquals(4, store.weaponFiles().size());

        DefaultWeapons.IDS.forEach(id -> store.deleteFile(id + ".json"));
        assertFalse(DefaultWeapons.installIfMissing(store));
        assertEquals(0, store.weaponFiles().size());
    }
}
