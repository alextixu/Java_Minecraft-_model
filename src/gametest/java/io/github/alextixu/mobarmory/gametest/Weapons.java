package io.github.alextixu.mobarmory.gametest;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;

/**
 * Ids of the shipped default pieces (mobarmory/default_weapons/*.json). The game tests run in their own folder
 * (run-gametest/, emptied before every run), so they always see these defaults, never a player's edits (H10).
 */
final class Weapons {
    static final EquipmentId HEAVY_SWORD = EquipmentId.of("heavy_sword");
    static final EquipmentId DAGGER = EquipmentId.of("dagger");
    static final EquipmentId EVASION_CHESTPLATE = EquipmentId.of("evasion_chestplate");
    static final EquipmentId HEAVY_ARMOR = EquipmentId.of("heavy_armor");

    private Weapons() {
    }
}
