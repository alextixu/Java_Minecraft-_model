package io.github.alextixu.mobarmory.content;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.data.DefaultWeapons;

/**
 * The real mob catalog for unit tests, and the weapons the mod ships (mobarmory/default_weapons/*.json), read the same
 * way {@code EquipmentContractTest} reads them. In the game, mobs take their equipment from the weapon files in effect
 * when they spawn; the shipped files are what a fresh install has.
 */
final class MobTestCatalog {
    static final MobCatalog MOBS = ModMobs.catalog();
    static final EquipmentCatalog SHIPPED_WEAPONS = DefaultWeapons.catalog(ModStats.CATALOG);

    private MobTestCatalog() {
    }
}
