package io.github.alextixu.mobarmory.core.mob;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Every kind of mob, checked against the stat and equipment catalogs when it is built. A mistake fails at
 * startup with a message naming the mob and what is wrong (O2, B1-B3, B5); nothing is clamped or skipped.
 * Whether every behavior has a binder (B4) is checked by the Forge layer, which owns the binders.
 */
public final class MobCatalog {
    private final StatCatalog stats;
    private final EquipmentCatalog equipment;
    private final Map<MobId, MobDefinition> byId;

    private MobCatalog(StatCatalog stats, EquipmentCatalog equipment, Map<MobId, MobDefinition> byId) {
        this.stats = stats;
        this.equipment = equipment;
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public static Builder builder(StatCatalog stats, EquipmentCatalog equipment) {
        return new Builder(stats, equipment);
    }

    public StatCatalog stats() {
        return stats;
    }

    public EquipmentCatalog equipment() {
        return equipment;
    }

    public Optional<MobDefinition> find(MobId id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** @throws NoSuchElementException if the mob is not registered */
    public MobDefinition get(MobId id) {
        MobDefinition definition = byId.get(id);
        if (definition == null) {
            throw new NoSuchElementException("Unknown mob '" + id + "'");
        }
        return definition;
    }

    /** Every mob, in registration order. */
    public List<MobDefinition> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public static final class Builder {
        private final StatCatalog stats;
        private final EquipmentCatalog equipment;
        private final Map<MobId, MobDefinition> byId = new LinkedHashMap<>();

        private Builder(StatCatalog stats, EquipmentCatalog equipment) {
            this.stats = Objects.requireNonNull(stats, "stats");
            this.equipment = Objects.requireNonNull(equipment, "equipment");
        }

        /**
         * @throws IllegalArgumentException if the id is taken (B5), a stat is unknown (B1) or out of range (B2),
         *                                  or a piece of equipment is unknown or in a slot where it does nothing (B3)
         */
        public Builder add(MobDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            if (byId.containsKey(definition.id())) {
                throw new IllegalArgumentException("Mob '" + definition.id() + "' is registered twice");
            }
            definition.stats().forEach((id, value) -> checkStat(definition.id(), id, value));
            definition.equipment().forEach((slot, id) -> checkEquipment(definition.id(), slot, id));
            byId.put(definition.id(), definition);
            return this;
        }

        public Builder addAll(Iterable<MobDefinition> definitions) {
            definitions.forEach(this::add);
            return this;
        }

        public MobCatalog build() {
            return new MobCatalog(stats, equipment, byId);
        }

        private void checkStat(MobId mob, StatId id, double value) {
            StatDefinition stat = stats.find(id).orElseThrow(() -> new IllegalArgumentException(
                "Mob '" + mob + "' sets stat '" + id + "', which is not registered"));
            if (value < stat.min() || value > stat.max()) {
                throw new IllegalArgumentException("Mob '" + mob + "' sets " + id + " to " + value
                    + ", outside its limits [" + stat.min() + ", " + stat.max() + "]");
            }
        }

        private void checkEquipment(MobId mob, EquipmentSlotKind slot, EquipmentId id) {
            EquipmentDefinition piece = equipment.find(id).orElseThrow(() -> new IllegalArgumentException(
                "Mob '" + mob + "' equips '" + id + "', which is not registered"));
            if (piece.slot() != slot) {
                throw new IllegalArgumentException("Mob '" + mob + "' puts '" + id + "' in slot " + slot
                    + ", but it only works in " + piece.slot());
            }
        }
    }
}
