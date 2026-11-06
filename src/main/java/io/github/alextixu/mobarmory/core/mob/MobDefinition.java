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
 * come from anywhere (R2b reads it from JSON). A missing piece only leaves the mob without it ({@link MobEquipmentCheck}).
 * Since R4b the definitions themselves are files in {@code config/mobarmory/mobs/} ({@code data/MobFileJson}).
 *
 * @param id        mob id, also the entity type name
 * @param name      display name, shown as-is in any language (R4b decision O13)
 * @param look      the borrowed vanilla body and size
 * @param stats     base value of each stat this mob sets; stats left out use the catalog default (O4, B7)
 * @param equipment which piece the mob holds or wears in each slot when it spawns
 * @param behaviors what it does; empty means it stands still (O3, B6)
 */
public record MobDefinition(
    MobId id,
    String name,
    MobLook look,
    Map<StatId, Double> stats,
    Map<EquipmentSlotKind, EquipmentId> equipment,
    List<Behavior> behaviors
) {

    /** Same limit as a weapon's name. */
    public static final int MAX_NAME_LENGTH = 64;

    public MobDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, () -> "Mob '" + id + "' has no name");
        name = name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Mob '" + id + "': name must be 1 to " + MAX_NAME_LENGTH
                + " characters without line breaks, got '" + name + "'");
        }
        Objects.requireNonNull(look, () -> "Mob '" + id + "' has no look");
        stats = Collections.unmodifiableMap(new LinkedHashMap<>(stats));
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
        List<String> targeting = new ArrayList<>();
        for (Behavior behavior : behaviors) {
            if (!seen.add(behavior.name())) {
                throw new IllegalArgumentException("Mob '" + id + "' has behavior '" + behavior.name() + "' more than once");
            }
            if (behavior.choosesTargets()) {
                targeting.add(behavior.name());
            }
        }
        if (targeting.size() > 1) {
            throw new IllegalArgumentException("Mob '" + id + "': " + String.join(" and ", targeting)
                + " both decide whom it fights; keep only one (R4b O3)");
        }
        for (Behavior behavior : behaviors) {
            if (behavior.needsTarget() && targeting.isEmpty()) {
                throw new IllegalArgumentException("Mob '" + id + "': " + behavior.name()
                    + " needs attack_players or neutral to give it a target (R4b O4)");
            }
        }
    }

    /** The same mob with another look; a reload keeps the body until a restart (R4b O9). */
    public MobDefinition withLook(MobLook newLook) {
        return new MobDefinition(id, name, newLook, stats, equipment, behaviors);
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

    /** Builds a definition step by step, so tests read like a list of properties. */
    public static final class Builder {
        private final MobId id;
        private String name;
        private MobLook look;
        private final Map<StatId, Double> stats = new LinkedHashMap<>();
        private final Map<EquipmentSlotKind, EquipmentId> equipment = new EnumMap<>(EquipmentSlotKind.class);
        private final List<Behavior> behaviors = new ArrayList<>();

        private Builder(MobId id) {
            this.id = id;
        }

        /** Without a name the id is used, e.g. "brute"; mob files always have one (O13). */
        public Builder name(String newName) {
            this.name = Objects.requireNonNull(newName, "name");
            return this;
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
            return new MobDefinition(id, name == null ? id.value() : name, look, stats, equipment, behaviors);
        }
    }
}
