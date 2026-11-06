package io.github.alextixu.mobarmory.web;

import io.github.alextixu.mobarmory.core.mob.MobCatalog;

/**
 * What the web page may ask about mobs (R4, R6), in the same role as {@link WeaponAdmin}: the web layer knows only
 * this interface, and the Forge side plugs in the game's mobs. Read only; letting the page change mobs is a later
 * decision (review-03 A6, A7) and would add methods here.
 */
@FunctionalInterface
public interface MobAdmin {
    /**
     * Every kind of mob. Built at startup and never changed afterwards (entity types can only be registered then), so
     * any thread may read it.
     */
    MobCatalog mobs();
}
