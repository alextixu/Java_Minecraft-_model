package io.github.alextixu.mobarmory.core.mob;

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
 * Every kind of mob, checked against the stat catalog when it is built. A mistake fails at startup with a message
 * naming the mob and what is wrong (O2, B1, B2, B5); nothing is clamped or skipped. Whether every behavior has a
 * binder (B4) is checked by the Forge layer, which owns the binders.
 * <p>
 * Equipment is not checked here: weapons are files that can be deleted or changed (R2b), so a missing piece only
 * leaves the mob without it, with a warning ({@link MobEquipmentCheck}; B3, decision O2 changed to B on 11/07).
 */
public final class MobCatalog {
    private final StatCatalog stats;
    private final Map<MobId, MobDefinition> byId;

    private MobCatalog(StatCatalog stats, Map<MobId, MobDefinition> byId) {
        this.stats = stats;
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public static Builder builder(StatCatalog stats) {
        return new Builder(stats);
    }

    public StatCatalog stats() {
        return stats;
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
        private final Map<MobId, MobDefinition> byId = new LinkedHashMap<>();

        private Builder(StatCatalog stats) {
            this.stats = Objects.requireNonNull(stats, "stats");
        }

        /**
         * @throws IllegalArgumentException if the id is taken (B5), or a stat is unknown (B1) or out of range (B2)
         */
        public Builder add(MobDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            if (byId.containsKey(definition.id())) {
                throw new IllegalArgumentException("Mob '" + definition.id() + "' is registered twice");
            }
            definition.stats().forEach((id, value) -> checkStat(definition.id(), id, value));
            byId.put(definition.id(), definition);
            return this;
        }

        public Builder addAll(Iterable<MobDefinition> definitions) {
            definitions.forEach(this::add);
            return this;
        }

        public MobCatalog build() {
            return new MobCatalog(stats, byId);
        }

        private void checkStat(MobId mob, StatId id, double value) {
            StatDefinition stat = stats.find(id).orElseThrow(() -> new IllegalArgumentException(
                "Mob '" + mob + "' sets stat '" + id + "', which is not registered"));
            if (value < stat.min() || value > stat.max()) {
                throw new IllegalArgumentException("Mob '" + mob + "' sets " + id + " to " + value
                    + ", outside its limits [" + stat.min() + ", " + stat.max() + "]");
            }
        }
    }
}
