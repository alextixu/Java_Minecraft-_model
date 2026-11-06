package io.github.alextixu.mobarmory.core.equipment;

import io.github.alextixu.mobarmory.core.stat.StatId;
import io.github.alextixu.mobarmory.core.stat.StatModifier;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * A kind of weapon or armor, described as data: its name, which slot it works in, which stats it changes and
 * how it looks. A "heavy sword" is not a class; it is one of these with "movement speed -30%".
 * Every item of the same kind shares this one definition (R2 decision Q6 A), so replacing the definition
 * changes every copy at once.
 *
 * @param id        equipment id, also the item's registry name and the config file name
 * @param name      display name, shown as-is in any language
 * @param slot      the only slot where the modifiers apply
 * @param modifiers stat changes while equipped; at most one per stat
 * @param look      a borrowed vanilla look or a custom texture
 */
public record EquipmentDefinition(EquipmentId id, String name, EquipmentSlotKind slot, List<StatModifier> modifiers,
                                  EquipmentLook look) {
    /** Long enough for any real weapon name, short enough to fit a tooltip. */
    public static final int MAX_NAME_LENGTH = 64;

    public EquipmentDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(look, "look");
        name = name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Equipment '" + id + "': name must be 1 to " + MAX_NAME_LENGTH
                + " characters without control characters");
        }
        modifiers = List.copyOf(modifiers);
        Set<StatId> seen = new HashSet<>();
        for (StatModifier modifier : modifiers) {
            if (!seen.add(modifier.stat())) {
                throw new IllegalArgumentException(
                    "Equipment '" + id + "' modifies stat '" + modifier.stat() + "' more than once");
            }
        }
    }

    /** A piece with a borrowed vanilla look and a name made from its id ({@code heavy_sword} is "Heavy Sword"). */
    public static EquipmentDefinition of(String id, EquipmentSlotKind slot, String look, StatModifier... modifiers) {
        EquipmentId equipmentId = EquipmentId.of(id);
        EquipmentLook vanilla;
        try {
            vanilla = EquipmentLook.item(look);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Equipment '" + id + "': " + e.getMessage(), e);
        }
        return new EquipmentDefinition(equipmentId, defaultName(equipmentId), slot, List.of(modifiers), vanilla);
    }

    /** {@code heavy_sword} becomes "Heavy Sword". */
    public static String defaultName(EquipmentId id) {
        StringBuilder name = new StringBuilder();
        for (String word : id.value().split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return name.toString();
    }

    /** The same piece in another slot. */
    public EquipmentDefinition withSlot(EquipmentSlotKind newSlot) {
        return new EquipmentDefinition(id, name, newSlot, modifiers, look);
    }
}
