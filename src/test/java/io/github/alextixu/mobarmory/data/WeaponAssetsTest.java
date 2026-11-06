package io.github.alextixu.mobarmory.data;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: the client files generated for each weapon (decision Q5 A). */
class WeaponAssetsTest {
    private static final byte[] HALBERD_PNG = TestPng.of(32, 32);

    private static EquipmentDefinition weapon(String id, EquipmentSlotKind slot, EquipmentLook look) {
        return new EquipmentDefinition(EquipmentId.of(id), "Test", slot, List.of(add(ModStats.ATTACK_DAMAGE.id(), 1)), look);
    }

    private static String text(Map<String, byte[]> files, String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("H2 自訂貼圖的戟：物品模型定義指向自己的模型，模型像劍一樣拿（handheld），貼圖原封不動放進去")
    void customTextureWeapon() {
        Map<String, byte[]> files = WeaponAssets.generate(
            List.of(weapon("halberd", EquipmentSlotKind.MAINHAND, EquipmentLook.texture("halberd_netherite"))),
            name -> name.equals("halberd_netherite") ? Optional.of(HALBERD_PNG) : Optional.empty());

        assertEquals(List.of("items/halberd.json", "models/item/halberd.json", "textures/item/halberd_netherite.png"),
            List.copyOf(files.keySet()));
        assertTrue(text(files, "items/halberd.json").contains("\"mobarmory:item/halberd\""));
        String model = text(files, "models/item/halberd.json");
        assertTrue(model.contains("\"minecraft:item/handheld\"") && model.contains("\"mobarmory:item/halberd_netherite\""), model);
        assertArrayEquals(HALBERD_PNG, files.get("textures/item/halberd_netherite.png"));
    }

    @Test
    @DisplayName("借用原版外觀：只產生物品模型定義，指向原版模型")
    void vanillaLook() {
        Map<String, byte[]> files = WeaponAssets.generate(
            List.of(weapon("heavy_sword", EquipmentSlotKind.MAINHAND, EquipmentLook.item("minecraft:netherite_sword"))),
            name -> Optional.empty());

        assertEquals(List.of("items/heavy_sword.json"), List.copyOf(files.keySet()));
        assertTrue(text(files, "items/heavy_sword.json").contains("\"minecraft:item/netherite_sword\""));
    }

    @Test
    @DisplayName("O12 盔甲用自訂貼圖：圖示是平的（item/generated）")
    void armorIconIsFlat() {
        Map<String, byte[]> files = WeaponAssets.generate(
            List.of(weapon("ruby_plate", EquipmentSlotKind.CHEST, EquipmentLook.texture("ruby_plate"))),
            name -> Optional.of(TestPng.of(16, 16)));

        assertTrue(text(files, "models/item/ruby_plate.json").contains("\"minecraft:item/generated\""));
    }

    @Test
    @DisplayName("兩把武器共用一張貼圖時，貼圖只放一次")
    void sharedTexture() {
        Map<String, byte[]> files = WeaponAssets.generate(List.of(
                weapon("a", EquipmentSlotKind.MAINHAND, EquipmentLook.texture("shared")),
                weapon("b", EquipmentSlotKind.MAINHAND, EquipmentLook.texture("shared"))),
            name -> Optional.of(HALBERD_PNG));

        assertEquals(5, files.size());
    }
}
