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
 * The client files generated at runtime (paths relative to {@code assets/mobarmory/}):
 * <ul>
 *   <li>{@code items/<id>.json} for every weapon, pointing at the model of its look;</li>
 *   <li>for every uploaded texture in use (decision Q7, "要能上傳 PNG"): {@code textures/item/<name>.png},
 *       {@code models/item/look/<name>.json} and {@code items/look/<name>.json}. The seven built-in textures have the
 *       same three files in the mod jar, so both kinds work the same way.</li>
 * </ul>
 * A weapon stack names its look's item model directly ({@code mobarmory:look/<name>}, see forge/WeaponLooks), so a
 * look change shows at once; {@code items/<id>.json} only covers a stack the server has not stamped yet.
 */
public final class WeaponAssets {
    public static final String NAMESPACE = "mobarmory";

    private WeaponAssets() {
    }

    /**
     * Every generated file for these weapons.
     *
     * @param uploaded the PNG of an uploaded texture; a missing one is left out (Minecraft then shows its "missing" texture)
     */
    public static Map<String, byte[]> generate(Collection<EquipmentDefinition> weapons, Function<String, Optional<byte[]>> uploaded) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (EquipmentDefinition weapon : weapons) {
            files.put("items/" + weapon.id().value() + ".json", utf8(itemModelDefinition(weapon.look())));
            if (weapon.look() instanceof EquipmentLook.Texture texture && !BuiltInTextures.contains(texture.texture())) {
                String name = texture.texture();
                if (!files.containsKey("textures/item/" + name + ".png")) {
                    uploaded.apply(name).ifPresent(png -> {
                        files.put("textures/item/" + name + ".png", png);
                        files.put("models/item/look/" + name + ".json", utf8(textureModel(name)));
                        files.put("items/look/" + name + ".json", utf8(itemModelDefinition(texture)));
                    });
                }
            }
        }
        return files;
    }

    /** The block model a look uses: a vanilla item's model, or {@code mobarmory:item/look/<texture>}. */
    public static String modelOf(EquipmentLook look) {
        return switch (look) {
            case EquipmentLook.VanillaItem item -> {
                String[] parts = item.item().split(":", 2);
                yield parts[0] + ":item/" + parts[1];
            }
            case EquipmentLook.Texture texture -> NAMESPACE + ":item/look/" + texture.texture();
        };
    }

    private static String itemModelDefinition(EquipmentLook look) {
        return "{\"model\": {\"type\": \"minecraft:model\", \"model\": \"" + modelOf(look) + "\"}}\n";
    }

    /** Held like a sword, the same as the built-in textures. */
    private static String textureModel(String texture) {
        return "{\"parent\": \"minecraft:item/handheld\", \"textures\": {\"layer0\": \"" + NAMESPACE + ":item/" + texture + "\"}}\n";
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
