package io.github.alextixu.mobarmory.core.equipment;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * How a piece of equipment looks (decisions Q5 A and O12 in 10-weapon-data-web-open-questions.md):
 * borrow a vanilla item's look, or use a custom PNG from {@code config/mobarmory/textures/}.
 * A new kind of look is one more record here plus one more way to generate the client model.
 */
public sealed interface EquipmentLook permits EquipmentLook.VanillaItem, EquipmentLook.Texture {

    /** Borrow a vanilla item's model, for example {@code minecraft:netherite_sword}. */
    static EquipmentLook item(String itemId) {
        return new VanillaItem(itemId);
    }

    /** Use {@code textures/<name>.png} from the config folder, for example {@code halberd_netherite}. */
    static EquipmentLook texture(String name) {
        return new Texture(name);
    }

    /** @param item a namespaced item id such as {@code minecraft:iron_sword} */
    record VanillaItem(String item) implements EquipmentLook {
        private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

        public VanillaItem {
            Objects.requireNonNull(item, "item");
            if (!ITEM_ID.matcher(item).matches()) {
                throw new IllegalArgumentException(
                    "look must be a vanilla item id like 'minecraft:iron_sword', got '" + item + "'");
            }
        }
    }

    /** @param texture file name without {@code .png}: lowercase letters, digits and underscores */
    record Texture(String texture) implements EquipmentLook {
        private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");

        public Texture {
            Objects.requireNonNull(texture, "texture");
            if (!NAME.matcher(texture).matches()) {
                throw new IllegalArgumentException("texture name must use lowercase letters, digits and underscores "
                    + "(the file name without .png), got '" + texture + "'");
            }
        }
    }
}
