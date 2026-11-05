package io.github.alextixu.mobarmory.core.equipment;

import io.github.alextixu.mobarmory.core.stat.StatId;
import io.github.alextixu.mobarmory.core.stat.StatModifier;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A kind of weapon or armor, described as data: which slot it works in and which stats it changes.
 * A "heavy sword" is not a class; it is one of these with "movement speed -30%".
 * Every item of the same kind shares this one definition (decision Q6 A).
 *
 * @param id        equipment id, also the item's registry name
 * @param slot      the only slot where the modifiers apply
 * @param modifiers stat changes while equipped; at most one per stat
 * @param look      vanilla item whose appearance is borrowed (O8), e.g. {@code minecraft:iron_chestplate}
 */
public record EquipmentDefinition(EquipmentId id, EquipmentSlotKind slot, List<StatModifier> modifiers, String look) {

    public EquipmentDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(look, "look");
        modifiers = List.copyOf(modifiers);
        Set<StatId> seen = new HashSet<>();
        for (StatModifier modifier : modifiers) {
            if (!seen.add(modifier.stat())) {
                throw new IllegalArgumentException(
                    "Equipment '" + id + "' modifies stat '" + modifier.stat() + "' more than once");
            }
        }
        if (!look.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException(
                "Equipment '" + id + "': look must be a vanilla item id like 'minecraft:iron_sword', got '" + look + "'");
        }
    }

    public static EquipmentDefinition of(String id, EquipmentSlotKind slot, String look, StatModifier... modifiers) {
        return new EquipmentDefinition(EquipmentId.of(id), slot, List.of(modifiers), look);
    }
}
