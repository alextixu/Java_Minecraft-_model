package io.github.alextixu.mobarmory.core.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * The combat effects in play, in two groups because they hook into different moments of a hit.
 *
 * @param defensive run before the defender takes damage; may cancel the hit (dodge)
 * @param offensive run when an attack lands; may change damage or make it critical (crit)
 */
public record CombatEffects(List<StatEffect> defensive, List<StatEffect> offensive) {

    public CombatEffects {
        defensive = List.copyOf(defensive);
        offensive = List.copyOf(offensive);
    }

    /** Every effect, defensive first. */
    public List<StatEffect> all() {
        List<StatEffect> all = new ArrayList<>(defensive);
        all.addAll(offensive);
        return List.copyOf(all);
    }
}
