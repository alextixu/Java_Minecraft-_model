package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The client files that make a weapon visible (decision Q5 A), generated from the definitions instead of being
 * written by hand. Paths are relative to {@code assets/mobarmory/}:
 * <ul>
 *   <li>{@code items/<id>.json}: which model the item uses;</li>
 *   <li>for a custom texture, {@code models/item/<id>.json} (held like a sword, or flat for armor) and
 *       {@code textures/item/<texture>.png}.</li>
 * </ul>
 */
public final class WeaponAssets {
    public static final String NAMESPACE = "mobarmory";

    private WeaponAssets() {
    }

    /**
     * Every file for these weapons.
     *
     * @param textures the PNG for a texture name; a missing one is left out (Minecraft then shows its "missing" texture)
     */
    public static Map<String, byte[]> generate(Collection<EquipmentDefinition> weapons, Function<String, Optional<byte[]>> textures) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (EquipmentDefinition weapon : weapons) {
            String id = weapon.id().value();
            files.put("items/" + id + ".json", utf8(itemModelDefinition(weapon)));
            if (weapon.look() instanceof EquipmentLook.Texture texture) {
                files.put("models/item/" + id + ".json", utf8(textureModel(weapon, texture)));
                textures.apply(texture.texture()).ifPresent(png -> files.put("textures/item/" + texture.texture() + ".png", png));
            }
        }
        return files;
    }

    /** {@code items/<id>.json}: a vanilla look points at the vanilla model, a texture at our generated model. */
    public static String itemModelDefinition(EquipmentDefinition weapon) {
        String model = switch (weapon.look()) {
            case EquipmentLook.VanillaItem item -> {
                String[] parts = item.item().split(":", 2);
                yield parts[0] + ":item/" + parts[1];
            }
            case EquipmentLook.Texture _ -> NAMESPACE + ":item/" + weapon.id().value();
        };
        return "{\"model\": {\"type\": \"minecraft:model\", \"model\": \"" + model + "\"}}\n";
    }

    /** Weapons are held like a sword ({@code item/handheld}); armor icons are flat ({@code item/generated}). */
    private static String textureModel(EquipmentDefinition weapon, EquipmentLook.Texture texture) {
        String parent = weapon.slot().isArmor() ? "minecraft:item/generated" : "minecraft:item/handheld";
        return "{\"parent\": \"" + parent + "\", \"textures\": {\"layer0\": \"" + NAMESPACE + ":item/" + texture.texture() + "\"}}\n";
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
