package io.github.alextixu.mobarmory.core.mob;

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
 * The mob definitions in effect right now, which can be replaced while the game runs (R4b). The same shape as the
 * weapons' {@code EquipmentLibrary} (21-r4b-mob-admin-boundary-note.md, decision Q2 A); two things are kept apart:
 * <ul>
 *   <li>which mobs <em>exist</em> as entity types: fixed when the game starts, because Minecraft freezes its entity
 *       type registry, so a new mob waits for a restart ({@link #pending()});</li>
 *   <li>what each mob <em>is</em> (name, look, stats, equipment, behaviors): replaced as a whole, immutable snapshot.
 *       Readers on any thread see either the old snapshot or the new one, never half of each.</li>
 * </ul>
 * Every change is checked by {@link MobCatalog} and {@link MobDefinition} first; a change that fails leaves everything
 * as it was. Two saves of the same mob: the later one wins (O17, like the weapons' O10). The body can only change at
 * a restart, because the client picks the renderer then (O9).
 */
public final class MobLibrary {
    private final StatCatalog stats;
    private final List<MobId> registered;
    private final Set<MobId> registeredSet;
    private volatile State state;

    private record State(MobCatalog catalog, Map<MobId, MobDefinition> pending, Set<MobId> fileMissing) {
    }

    /** Everything at one moment: definitions, waiting and file-missing mobs, all from the same snapshot. */
    public record View(MobCatalog catalog, Map<MobId, MobDefinition> pending, Set<MobId> fileMissing) {
    }

    /** Starts with the mobs loaded at startup; exactly these become entity types. */
    public MobLibrary(MobCatalog initial) {
        this.stats = initial.stats();
        List<MobId> ids = new ArrayList<>();
        for (MobDefinition definition : initial.all()) {
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
    public MobCatalog current() {
        return state.catalog();
    }

    /** The mobs that exist as entity types, in startup order. */
    public List<MobId> registered() {
        return registered;
    }

    public boolean isRegistered(MobId id) {
        return registeredSet.contains(id);
    }

    /** Mobs found in the mobs folder after startup; they can be spawned after a restart (B14). */
    public Map<MobId, MobDefinition> pending() {
        return state.pending();
    }

    /** Registered mobs whose file is gone; they keep their last definition until a restart (B12). */
    public Set<MobId> fileMissing() {
        return state.fileMissing();
    }

    /**
     * Replaces one registered mob (the web page's "save"). Its file was just written, so it no longer counts as missing.
     *
     * @throws NoSuchElementException   if the mob is not registered
     * @throws IllegalArgumentException if the definition is not valid, or the body changed (B11, O9)
     */
    public synchronized void update(MobDefinition changed) {
        State now = state;
        MobId id = changed.id();
        if (!isRegistered(id)) {
            throw new NoSuchElementException("Unknown mob '" + id + "'");
        }
        MobDefinition old = now.catalog().get(id);
        if (old.look().body() != changed.look().body()) {
            throw new IllegalArgumentException("Mob '" + id + "': the body can only change after a restart (was "
                + old.look().body() + ", got " + changed.look().body() + ")");
        }
        Map<MobId, MobDefinition> next = new LinkedHashMap<>();
        for (MobDefinition definition : now.catalog().all()) {
            next.put(definition.id(), definition.id().equals(id) ? changed : definition);
        }
        Set<MobId> fileMissing = new LinkedHashSet<>(now.fileMissing());
        fileMissing.remove(id);
        state = new State(build(next.values()), now.pending(), Collections.unmodifiableSet(fileMissing));
    }

    /**
     * Records a new mob that can be spawned after a restart (the web page's "new mob").
     *
     * @throws IllegalArgumentException if the id is already a mob or already waiting, or the definition is not valid
     */
    public synchronized void addPending(MobDefinition definition) {
        State now = state;
        if (isRegistered(definition.id()) || now.pending().containsKey(definition.id())) {
            throw new IllegalArgumentException("Mob '" + definition.id() + "' already exists");
        }
        build(List.of(definition)); // checks the stats it sets
        Map<MobId, MobDefinition> pending = new LinkedHashMap<>(now.pending());
        pending.put(definition.id(), definition);
        state = new State(now.catalog(), Collections.unmodifiableMap(pending), now.fileMissing());
    }

    /**
     * Replaces everything with what was loaded from the files (the reload command and button). Either the whole set is
     * valid and applied, or nothing changes (B15).
     *
     * @param loaded every definition found in the mobs folder
     * @throws IllegalArgumentException if any definition is not valid; nothing changes
     */
    public synchronized MobReloadReport replaceAll(List<MobDefinition> loaded) {
        State now = state;
        MobCatalog all = build(loaded); // duplicate ids and unknown or out-of-range stats fail here
        Map<MobId, MobDefinition> next = new LinkedHashMap<>();
        List<MobId> changed = new ArrayList<>();
        List<MobId> bodyWaits = new ArrayList<>();
        Set<MobId> fileMissing = new LinkedHashSet<>();
        for (MobId id : registered) {
            MobDefinition old = now.catalog().get(id);
            MobDefinition fresh = all.find(id).orElse(null);
            if (fresh == null) {
                next.put(id, old);
                fileMissing.add(id);
                continue;
            }
            if (fresh.look().body() != old.look().body()) {
                fresh = fresh.withLook(new MobLook(old.look().body(), fresh.look().scale()));
                bodyWaits.add(id);
            }
            if (!fresh.equals(old)) {
                changed.add(id);
            }
            next.put(id, fresh);
        }
        Map<MobId, MobDefinition> pending = new LinkedHashMap<>();
        for (MobDefinition definition : all.all()) {
            if (!next.containsKey(definition.id())) {
                pending.put(definition.id(), definition);
            }
        }
        state = new State(build(next.values()), Collections.unmodifiableMap(pending), Collections.unmodifiableSet(fileMissing));
        return new MobReloadReport(changed, List.copyOf(pending.keySet()), List.copyOf(fileMissing), bodyWaits);
    }

    private MobCatalog build(Iterable<MobDefinition> definitions) {
        return MobCatalog.builder(stats).addAll(definitions).build();
    }
}
