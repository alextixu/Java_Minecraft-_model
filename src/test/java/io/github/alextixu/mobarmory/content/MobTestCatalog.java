package io.github.alextixu.mobarmory.content;

import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.data.DefaultWeapons;

/**
 * The real mob catalog for unit tests, checked against the weapons the mod ships (mobarmory/default_weapons/*.json),
 * read the same way {@code EquipmentContractTest} reads them.
 */
final class MobTestCatalog {
    static final MobCatalog MOBS = ModMobs.catalog(DefaultWeapons.catalog(ModStats.CATALOG));

    private MobTestCatalog() {
    }
}
