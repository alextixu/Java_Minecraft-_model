package io.github.alextixu.mobarmory.core.mob;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A kind of mob, described as data (Q1/Q2 A): a "brute" is not a class, it is one of these. Every mob of the
 * same kind shares this one definition.
 * <p>
 * Equipment is referenced by id ({@code "heavy_sword"}), never by a Java constant, so the equipment list can
 * come from anywhere (R2b reads it from JSON). Whether the ids exist is checked by {@link MobCatalog}.
 *
 * @param id        mob id, also the entity type name
 * @param look      the borrowed vanilla body and size
 * @param stats     base value of each stat this mob sets; stats left out use the catalog default (O4, B7)
 * @param equipment which piece the mob holds or wears in each slot when it spawns
 * @param behaviors what it does; empty means it stands still (O3, B6)
 */
public record MobDefinition(
    MobId id,
    MobLook look,
    Map<StatId, Double> stats,
    Map<EquipmentSlotKind, EquipmentId> equipment,
    List<Behavior> behaviors
) {

    public MobDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(look, () -> "Mob '" + id + "' has no look");
        stats =Collections.unmodifiableMap(new LinkedHashMap<>(stats));
        equipment = equipment.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new EnumMap<>(equipment));
        behaviors = List.copyOf(behaviors);
        for (Map.Entry<StatId, Double> stat : stats.entrySet()) {
            if (stat.getValue() == null || !Double.isFinite(stat.getValue())) {
                throw new IllegalArgumentException(
                    "Mob '" + id + "': stat '" + stat.getKey() + "' must be a finite number, got " + stat.getValue());
            }
        }
        Set<String> seen = new HashSet<>();
        for (Behavior behavior : behaviors) {
            if (!seen.add(behavior.name())) {
                throw new IllegalArgumentException("Mob '" + id + "' has behavior '" + behavior.name() + "' more than once");
            }
        }
    }

    public static Builder builder(String id) {
        return new Builder(MobId.of(id));
    }

    /**
     * The base value of every stat in the catalog when this mob spawns: the definition's value where it sets one,
     * the catalog default everywhere else (O4, B7). Equipment comes on top of these, through R2's rules.
     */
    public Map<StatId, Double> spawnStats(StatCatalog catalog) {
        Map<StatId, Double> values = new LinkedHashMap<>();
        for (StatDefinition stat : catalog.all()) {
            values.put(stat.id(), stats.getOrDefault(stat.id(), stat.defaultValue()));
        }
        return values;
    }

    /** Builds a definition step by step, so content files read like a list of properties. */
    public static final class Builder {
        private final MobId id;
        private MobLook look;
        private final Map<StatId, Double> stats = new LinkedHashMap<>();
        private final Map<EquipmentSlotKind, EquipmentId> equipment = new EnumMap<>(EquipmentSlotKind.class);
        private final List<Behavior> behaviors = new ArrayList<>();

        private Builder(MobId id) {
            this.id = id;
        }

        public Builder look(MobLook newLook) {
            this.look = Objects.requireNonNull(newLook, "look");
            return this;
        }

        /** @throws IllegalArgumentException if the stat is already set */
        public Builder stat(StatDefinition stat, double value) {
            if (stats.putIfAbsent(stat.id(), value) != null) {
                throw new IllegalArgumentException("Mob '" + id + "' sets stat '" + stat.id() + "' twice");
            }
            return this;
        }

        /**
         * @param equipmentId the piece's id, e.g. {@code "heavy_sword"}
         * @throws IllegalArgumentException if the slot is already filled
         */
        public Builder equip(EquipmentSlotKind slot, String equipmentId) {
            if (equipment.putIfAbsent(slot, EquipmentId.of(equipmentId)) != null) {
                throw new IllegalArgumentException("Mob '" + id + "' fills slot " + slot + " twice");
            }
            return this;
        }

        public Builder behavior(Behavior behavior) {
            behaviors.add(Objects.requireNonNull(behavior, "behavior"));
            return this;
        }

        public MobDefinition build() {
            return new MobDefinition(id, look, stats, equipment, behaviors);
        }
    }
}
