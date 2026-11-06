package io.github.alextixu.mobarmory.core.equipment;

import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * The equipment definitions in effect right now, which can be replaced while the game runs (R2b, R6).
 * <p>
 * Two things are kept apart (08-weapon-data-web-boundary-note.md, section 1):
 * <ul>
 *   <li>which pieces <em>exist</em> as items: fixed when the game starts, because Minecraft freezes its item
 *       registry, so a new piece waits for a restart ({@link #pending()});</li>
 *   <li>what each piece <em>is</em> (name, stats, look): replaced as a whole, immutable snapshot. Readers on any
 *       thread see either the old snapshot or the new one, never half of each.</li>
 * </ul>
 * Every change is validated by {@link EquipmentCatalog} first; a change that fails leaves everything as it was.
 * Two saves of the same piece: the later one wins (decision O10).
 */
public final class EquipmentLibrary {
    private final StatCatalog stats;
    private final List<EquipmentId> registered;
    private final Set<EquipmentId> registeredSet;
    private volatile State state;

    private record State(EquipmentCatalog catalog, Map<EquipmentId, EquipmentDefinition> pending, Set<EquipmentId> fileMissing) {
    }

    /** Everything at one moment: definitions, waiting and file-missing pieces, all from the same snapshot. */
    public record View(EquipmentCatalog catalog, Map<EquipmentId, EquipmentDefinition> pending, Set<EquipmentId> fileMissing) {
    }

    /** Starts with the pieces loaded at startup; exactly these become items. */
    public EquipmentLibrary(EquipmentCatalog initial) {
        this.stats = initial.stats();
        List<EquipmentId> ids = new ArrayList<>();
        for (EquipmentDefinition definition : initial.all()) {
            ids.add(definition.id());
        }
        this.registered = List.copyOf(ids);
        this.registeredSet = Set.copyOf(ids);
        this.state = new State(initial, Map.of(), Set.of());
    }

    public StatCatalog stats() {
        return stats;
    }

    public View view() {
        State now = state;
        return new View(now.catalog(), now.pending(), now.fileMissing());
    }

    /** The definitions in effect now. Take one snapshot per calculation. */
    public EquipmentCatalog current() {
        return state.catalog();
    }

    /** The pieces that exist as items, in startup order. */
    public List<EquipmentId> registered() {
        return registered;
    }

    public boolean isRegistered(EquipmentId id) {
        return registeredSet.contains(id);
    }

    /** Pieces found in the config folder after startup; they become items after a restart (B7, H9). */
    public Map<EquipmentId, EquipmentDefinition> pending() {
        return state.pending();
    }

    /** Registered pieces whose file is gone; they keep their last definition until a restart (B8). */
    public Set<EquipmentId> fileMissing() {
        return state.fileMissing();
    }

    /**
     * Replaces one registered piece (the web page's "save"). The later of two saves wins (decision O10).
     *
     * @param changed the new definition; its slot must stay the same (B9)
     * @throws NoSuchElementException   if the piece is not registered
     * @throws IllegalArgumentException if the definition is not valid (B10), or the slot changed (B9)
     */
    public synchronized void update(EquipmentDefinition changed) {
        State now = state;
        EquipmentId id = changed.id();
        if (!isRegistered(id)) {
            throw new NoSuchElementException("Unknown equipment '" + id + "'");
        }
        EquipmentDefinition old = now.catalog().get(id);
        if (old.slot() != changed.slot()) {
            throw new IllegalArgumentException("Equipment '" + id + "': the slot can only change after a restart (was "
                + old.slot() + ", got " + changed.slot() + ")");
        }
        Map<EquipmentId, EquipmentDefinition> next = new LinkedHashMap<>();
        for (EquipmentDefinition definition : now.catalog().all()) {
            next.put(definition.id(), definition.id().equals(id) ? changed : definition);
        }
        state = new State(build(next.values()), now.pending(), now.fileMissing());
    }

    /**
     * Records a new piece that becomes an item after a restart (the web page's "new weapon").
     *
     * @throws IllegalArgumentException if the id is already an item or already waiting, or the definition is
     *                                  not valid
     */
    public synchronized void addPending(EquipmentDefinition definition) {
        State now = state;
        if (isRegistered(definition.id()) || now.pending().containsKey(definition.id())) {
            throw new IllegalArgumentException("Equipment '" + definition.id() + "' already exists");
        }
        build(List.of(definition)); // validates the stats it refers to
        Map<EquipmentId, EquipmentDefinition> pending = new LinkedHashMap<>(now.pending());
        pending.put(definition.id(), definition);
        state = new State(now.catalog(), Collections.unmodifiableMap(pending), now.fileMissing());
    }

    /**
     * Replaces everything with what was loaded from the files (the reload command and button).
     * Either the whole set is valid and applied, or nothing changes (B6).
     *
     * @param loaded every definition found in the config folder
     * @return what changed, what waits for a restart and what lost its file
     * @throws IllegalArgumentException if any definition is not valid; nothing changes
     */
    public synchronized ReloadReport replaceAll(List<EquipmentDefinition> loaded) {
        State now = state;
        EquipmentCatalog all = build(loaded); // duplicate ids and unknown stats fail here, before anything changes
        Map<EquipmentId, EquipmentDefinition> next = new LinkedHashMap<>();
        List<EquipmentId> changed = new ArrayList<>();
        List<EquipmentId> slotWaits = new ArrayList<>();
        Set<EquipmentId> fileMissing = new LinkedHashSet<>();
        for (EquipmentId id : registered) {
            EquipmentDefinition old = now.catalog().get(id);
            EquipmentDefinition fresh = all.find(id).orElse(null);
            if (fresh == null) {
                next.put(id, old);
                fileMissing.add(id);
                continue;
            }
            if (fresh.slot() != old.slot()) {
                fresh = fresh.withSlot(old.slot());
                slotWaits.add(id);
            }
            if (!fresh.equals(old)) {
                changed.add(id);
            }
            next.put(id, fresh);
        }
        Map<EquipmentId, EquipmentDefinition> pending = new LinkedHashMap<>();
        for (EquipmentDefinition definition : all.all()) {
            if (!next.containsKey(definition.id())) {
                pending.put(definition.id(), definition);
            }
        }
        state = new State(build(next.values()), Collections.unmodifiableMap(pending), Collections.unmodifiableSet(fileMissing));
        return new ReloadReport(changed, List.copyOf(pending.keySet()), List.copyOf(fileMissing), slotWaits);
    }

    private EquipmentCatalog build(Iterable<EquipmentDefinition> definitions) {
        EquipmentCatalog.Builder builder = EquipmentCatalog.builder(stats);
        for (EquipmentDefinition definition : definitions) {
            builder.add(definition);
        }
        return builder.build();
    }
}
