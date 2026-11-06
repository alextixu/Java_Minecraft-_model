package io.github.alextixu.mobarmory.web;

import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import io.github.alextixu.mobarmory.core.mob.MobReloadReport;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.data.MobLoadException;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Everything the web page can do to mobs (R6's read-only list, extended in R4b: decisions Q4 A, Q8 A), in the same role
 * as {@link WeaponAdmin}: the web layer knows only this interface, never Minecraft. The game's adapter
 * ({@code forge/ServerMobAdmin}) runs each change on the server thread and updates the mobs in the world; tests use a
 * plain implementation without the game.
 */
public interface MobAdmin {

    /** Every stat in the game; a mob may set these. */
    StatCatalog stats();

    /** Definitions, waiting and file-missing mobs at one moment. */
    MobLibrary.View view();

    /**
     * Saves a mob; the later of two saves wins (B19). Name, stats and size apply at once; behaviors and equipment the
     * next time a mob is created; the body cannot change before a restart (Q6 A, O9).
     *
     * @throws NoSuchElementException if there is no such mob (404)
     * @throws WeaponFormatException  if the change is not valid (400)
     */
    void update(MobDefinition changed) throws IOException, WeaponFormatException;

    /** A new mob; it can be spawned after the next restart (H3). */
    void create(MobDefinition definition) throws IOException, WeaponFormatException;

    /** Reads the mob files again; all or nothing (B15). */
    MobReloadReport reload() throws IOException, MobLoadException;
}
