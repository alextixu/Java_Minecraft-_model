package io.github.alextixu.mobarmory.core;

import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.Map;

/**
 * Read-only view of one entity's current stats. Combat effects read stats through this, and later the
 * combo system (R3) will too, so neither needs to know about Minecraft.
 */
@FunctionalInterface
public interface StatReader {

    /** The entity's current value of the stat, after every modifier. */
    double get(StatId id);

    /** A reader backed by a map; stats not in the map read as 0. Handy in tests. */
    static StatReader of(Map<StatId, Double> values) {
        Map<StatId, Double> copy = Map.copyOf(values);
        return id -> copy.getOrDefault(id, 0.0);
    }

    /** A reader where every stat is 0, used when there is no attacker (falling, fire, drowning). */
    StatReader NONE = id -> 0.0;
}
