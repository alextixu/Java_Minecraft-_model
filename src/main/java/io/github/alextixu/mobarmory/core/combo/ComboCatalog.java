package io.github.alextixu.mobarmory.core.combo;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Every combo in effect, checked when it is built: ids are unique, and no two combos have the same order of swings
 * (the second one could never trigger). An empty catalog is allowed: a player who deletes every combo file simply
 * has no combos.
 */
public final class ComboCatalog {
    public static final ComboCatalog EMPTY = new ComboCatalog(List.of());

    private final List<ComboDefinition> combos;
    private final Map<ComboInput, Map<ComboInput, ComboDefinition>> byOrder = new EnumMap<>(ComboInput.class);

    private ComboCatalog(List<ComboDefinition> combos) {
        this.combos = List.copyOf(combos);
        Set<String> ids = new HashSet<>();
        for (ComboDefinition combo : this.combos) {
            if (!ids.add(combo.id())) {
                throw new IllegalArgumentException("combo '" + combo.id() + "' is defined twice");
            }
            ComboDefinition sameOrder = byOrder.computeIfAbsent(combo.first(), first -> new EnumMap<>(ComboInput.class))
                .putIfAbsent(combo.second(), combo);
            if (sameOrder != null) {
                throw new IllegalArgumentException("combos '" + sameOrder.id() + "' and '" + combo.id()
                    + "' have the same order " + combo.first() + " -> " + combo.second());
            }
        }
    }

    public static ComboCatalog of(List<ComboDefinition> combos) {
        return new ComboCatalog(Objects.requireNonNull(combos, "combos"));
    }

    public List<ComboDefinition> all() {
        return combos;
    }

    /** The combo for this order of swings, if there is one. */
    public Optional<ComboDefinition> find(ComboInput first, ComboInput second) {
        return Optional.ofNullable(byOrder.getOrDefault(first, Map.of()).get(second));
    }
}
