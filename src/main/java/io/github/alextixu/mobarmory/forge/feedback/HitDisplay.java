package io.github.alextixu.mobarmory.forge.feedback;

import io.github.alextixu.mobarmory.core.feedback.HitFilter;
import io.github.alextixu.mobarmory.core.feedback.HitReport;
import net.minecraft.world.entity.LivingEntity;

/**
 * One way of showing a hit to people (Observer). D1 has one: {@link ActionBarDisplay}. Another one later (R6's
 * combat log on the web page, R3's combo name) is a new implementation plus one line in {@code MobArmoryMod}.
 */
public interface HitDisplay {

    /**
     * Shows one hit. Only called for hits {@link HitFilter#shows} accepts, on the server thread.
     *
     * @param report   what happened
     * @param defender who was hit
     * @param attacker who caused it; never null for a shown hit, because only damage caused by a mob is shown
     */
    void show(HitReport report, LivingEntity defender, LivingEntity attacker);
}
