package io.github.alextixu.mobarmory.core.equipment;

import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatModifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Every piece of equipment, checked against the stat catalog when it is built.
 * Mistakes fail at startup with a message naming the equipment and the stat (decision O2).
 */
public final class EquipmentCatalog {
    private final StatCatalog stats;
    private final Map<EquipmentId, EquipmentDefinition> byId;

    private EquipmentCatalog(StatCatalog stats, Map<EquipmentId, EquipmentDefinition> byId) {
        this.stats = stats;
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public static Builder builder(StatCatalog stats) {
        return new Builder(stats);
    }

    public StatCatalog stats() {
        return stats;
    }

    public Optional<EquipmentDefinition> find(EquipmentId id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** @throws NoSuchElementException if the equipment is not registered */
    public EquipmentDefinition get(EquipmentId id) {
        EquipmentDefinition definition = byId.get(id);
        if (definition == null) {
            throw new NoSuchElementException("Unknown equipment '" + id + "'");
        }
        return definition;
    }

    /** Every piece, in registration order. */
    public List<EquipmentDefinition> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public static final class Builder {
        private final StatCatalog stats;
        private final Map<EquipmentId, EquipmentDefinition> byId = new LinkedHashMap<>();

        private Builder(StatCatalog stats) {
            this.stats = Objects.requireNonNull(stats, "stats");
        }

        /**
         * @throws IllegalArgumentException if the id is taken or a modifier targets a stat that is not registered
         */
        public Builder add(EquipmentDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            if (byId.containsKey(definition.id())) {
                throw new IllegalArgumentException("Equipment '" + definition.id() + "' is registered twice");
            }
            for (StatModifier modifier : definition.modifiers()) {
                if (!stats.contains(modifier.stat())) {
                    throw new IllegalArgumentException("Equipment '" + definition.id()
                        + "' modifies stat '" + modifier.stat() + "', which is not registered");
                }
            }
            byId.put(definition.id(), definition);
            return this;
        }

        public EquipmentCatalog build() {
            return new EquipmentCatalog(stats, byId);
        }
    }
}
