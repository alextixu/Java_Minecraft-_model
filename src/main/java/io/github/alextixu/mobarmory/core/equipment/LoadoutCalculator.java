package io.github.alextixu.mobarmory.core.equipment;

import io.github.alextixu.mobarmory.core.stat.StackingRules;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import io.github.alextixu.mobarmory.core.stat.StatModifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Works out what an entity's stats should be from what it is holding and wearing.
 * Stateless: the answer depends only on the current loadout, never on what was equipped before, so
 * recomputing after a respawn or a relog can never stack the same piece twice (B11, B12).
 */
public final class LoadoutCalculator {
    private final Supplier<EquipmentCatalog> equipment;

    public LoadoutCalculator(EquipmentCatalog equipment) {
        Objects.requireNonNull(equipment, "equipment");
        this.equipment = () -> equipment;
    }

    /**
     * Reads the definitions in effect at the time of each calculation, so a definition replaced while the game
     * runs applies to the next recomputation (R2b; {@link EquipmentLibrary#current()}).
     */
    public LoadoutCalculator(Supplier<EquipmentCatalog> equipment) {
        this.equipment = Objects.requireNonNull(equipment, "equipment");
    }

    /**
     * The modifiers that are active. A piece counts only in the slot its definition names (O1, B1).
     * Ids that are not in the catalog contribute nothing.
     *
     * @param loadout which of our pieces sits in each slot; slots holding nothing or a vanilla item are left out
     */
    public List<StatModifier> activeModifiers(Map<EquipmentSlotKind, EquipmentId> loadout) {
        return activeModifiers(equipment.get(), loadout);
    }

    private static List<StatModifier> activeModifiers(EquipmentCatalog equipment, Map<EquipmentSlotKind, EquipmentId> loadout) {
        List<StatModifier> active = new ArrayList<>();
        for (EquipmentSlotKind slot : EquipmentSlotKind.values()) {
            EquipmentId id = loadout.get(slot);
            if (id == null) {
                continue;
            }
            equipment.find(id)
                .filter(definition -> definition.slot() == slot)
                .ifPresent(definition -> active.addAll(definition.modifiers()));
        }
        return active;
    }

    /**
     * Final values of the stats the loadout changes. Stats missing from the result are not touched by
     * equipment and should be left at their base value (any previous equipment bonus removed).
     *
     * @param loadout which of our pieces sits in each slot
     * @param baseOf  the entity's own base value for a stat
     */
    public Map<StatId, Double> compute(Map<EquipmentSlotKind, EquipmentId> loadout, ToDoubleFunction<StatId> baseOf) {
        EquipmentCatalog catalog = equipment.get(); // one snapshot for the whole calculation
        Map<StatId, List<StatModifier>> byStat = new LinkedHashMap<>();
        for (StatModifier modifier : activeModifiers(catalog, loadout)) {
            byStat.computeIfAbsent(modifier.stat(), stat -> new ArrayList<>()).add(modifier);
        }
        Map<StatId, Double> values = new LinkedHashMap<>();
        byStat.forEach((stat, modifiers) -> {
            StatDefinition definition = catalog.stats().get(stat);
            values.put(stat, StackingRules.value(definition, baseOf.applyAsDouble(stat), modifiers));
        });
        return values;
    }
}
