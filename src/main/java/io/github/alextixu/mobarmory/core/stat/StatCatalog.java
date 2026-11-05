package io.github.alextixu.mobarmory.core.stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * The list of every stat the game knows about. Adding a stat means adding one definition; everything else
 * (attaching it to every mob, letting equipment modify it, showing it) reads from this catalog.
 * Built once with {@link #builder()}, then read-only.
 */
public final class StatCatalog {
    private final Map<StatId, StatDefinition> byId;

    private StatCatalog(Map<StatId, StatDefinition> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<StatDefinition> find(StatId id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** @throws NoSuchElementException if the stat is not registered */
    public StatDefinition get(StatId id) {
        StatDefinition definition = byId.get(id);
        if (definition == null) {
            throw new NoSuchElementException("Unknown stat '" + id + "'");
        }
        return definition;
    }

    public boolean contains(StatId id) {
        return byId.containsKey(id);
    }

    /** Every stat, in registration order. */
    public List<StatDefinition> all() {
        return List.copyOf(byId.values());
    }

    /** Stats this mod adds itself (no vanilla attribute behind them), in registration order. */
    public List<StatDefinition> custom() {
        return byId.values().stream().filter(StatDefinition::isCustom).toList();
    }

    public int size() {
        return byId.size();
    }

    public static final class Builder {
        private final Map<StatId, StatDefinition> byId = new LinkedHashMap<>();
        private final Map<String, StatId> byVanillaAttribute = new HashMap<>();

        private Builder() {
        }

        /** @throws IllegalArgumentException if the id, or the vanilla attribute it maps to, is already taken */
        public Builder add(StatDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            if (byId.containsKey(definition.id())) {
                throw new IllegalArgumentException("Stat '" + definition.id() + "' is registered twice");
            }
            definition.vanillaAttribute().ifPresent(attribute -> {
                StatId existing = byVanillaAttribute.get(attribute);
                if (existing != null) {
                    throw new IllegalArgumentException("Stats '" + existing + "' and '" + definition.id()
                        + "' both map to " + attribute);
                }
            });
            byId.put(definition.id(), definition);
            definition.vanillaAttribute().ifPresent(attribute -> byVanillaAttribute.put(attribute, definition.id()));
            return this;
        }

        public Builder addAll(Iterable<StatDefinition> definitions) {
            definitions.forEach(this::add);
            return this;
        }

        public StatCatalog build() {
            return new StatCatalog(byId);
        }
    }

    @Override
    public String toString() {
        return "StatCatalog" + new ArrayList<>(byId.keySet());
    }
}
