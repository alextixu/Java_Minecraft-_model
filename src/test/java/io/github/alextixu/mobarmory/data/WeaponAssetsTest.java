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

/** Component tests: the client files generated at runtime (decision Q7, "要能上傳 PNG"). */
class WeaponAssetsTest {
    private static final byte[] UPLOADED = TestPng.of(32, 32);

    private static EquipmentDefinition weapon(String id, EquipmentLook look) {
        return new EquipmentDefinition(EquipmentId.of(id), "Test", EquipmentSlotKind.MAINHAND, List.of(add(ModStats.ATTACK_DAMAGE.id(), 1)), look);
    }

    private static String text(Map<String, byte[]> files, String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("上傳的貼圖：武器的物品模型指向 look/<名稱>，貼圖、模型、物品模型定義都產生，PNG 原封不動")
    void uploadedTexture() {
        Map<String, byte[]> files = WeaponAssets.generate(List.of(weapon("my_blade", EquipmentLook.texture("blade_art"))),
            name -> name.equals("blade_art") ? Optional.of(UPLOADED) : Optional.empty());

        assertEquals(List.of("items/my_blade.json", "textures/item/blade_art.png", "models/item/look/blade_art.json",
            "items/look/blade_art.json"), List.copyOf(files.keySet()));
        assertTrue(text(files, "items/my_blade.json").contains("\"mobarmory:item/look/blade_art\""));
        String model = text(files, "models/item/look/blade_art.json");
        assertTrue(model.contains("\"minecraft:item/handheld\"") && model.contains("\"mobarmory:item/blade_art\""), model);
        assertArrayEquals(UPLOADED, files.get("textures/item/blade_art.png"));
    }

    @Test
    @DisplayName("內建貼圖和原版外觀：只產生武器自己的物品模型定義（其他檔案在 jar 或原版裡）")
    void builtInAndVanillaLooks() {
        Map<String, byte[]> files = WeaponAssets.generate(List.of(
                weapon("halberd", EquipmentLook.texture("halberd_netherite")),
                weapon("heavy_sword", EquipmentLook.item("minecraft:netherite_sword"))),
            name -> Optional.of(UPLOADED));

        assertEquals(List.of("items/halberd.json", "items/heavy_sword.json"), List.copyOf(files.keySet()));
        assertTrue(text(files, "items/halberd.json").contains("\"mobarmory:item/look/halberd_netherite\""));
        assertTrue(text(files, "items/heavy_sword.json").contains("\"minecraft:item/netherite_sword\""));
    }

    @Test
    @DisplayName("兩把武器共用一張上傳的貼圖時，貼圖只放一次；找不到的貼圖就不放")
    void sharedAndMissingTextures() {
        Map<String, byte[]> shared = WeaponAssets.generate(List.of(
                weapon("a", EquipmentLook.texture("shared")), weapon("b", EquipmentLook.texture("shared"))),
            name -> Optional.of(UPLOADED));
        Map<String, byte[]> missing = WeaponAssets.generate(List.of(weapon("a", EquipmentLook.texture("gone"))), name -> Optional.empty());

        assertEquals(5, shared.size());
        assertEquals(List.of("items/a.json"), List.copyOf(missing.keySet()));
    }
}
