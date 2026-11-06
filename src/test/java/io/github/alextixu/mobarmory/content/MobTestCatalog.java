package io.github.alextixu.mobarmory.content;

import io.github.alextixu.mobarmory.core.mob.MobCatalog;

/**
 * The real mob catalog for unit tests, checked against the real equipment.
 * <p>
 * R2b merge point (tests): the only place unit tests decide where the equipment list comes from. When R2b
 * deletes {@code ModEquipment}, pass the equipment catalog R2b loads from its default JSON files instead.
 */
final class MobTestCatalog {
    static final MobCatalog MOBS = ModMobs.catalog(ModEquipment.CATALOG);

    private MobTestCatalog() {
    }
}
