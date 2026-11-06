package io.github.alextixu.mobarmory.content;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;

/**
 * The prototype's equipment (decision O7). Adding a piece means one line here, one item model file
 * ({@code assets/mobarmory/items/<id>.json}) and one name in {@code assets/mobarmory/lang/en_us.json}.
 */
public final class ModEquipment {
    public static final EquipmentDefinition HEAVY_SWORD = EquipmentDefinition.of(
        "heavy_sword", EquipmentSlotKind.MAINHAND, "minecraft:netherite_sword",
        add(ModStats.ATTACK_DAMAGE.id(), 6),
        percent(ModStats.MOVEMENT_SPEED.id(), -0.30),
        percent(ModStats.ATTACK_SPEED.id(), -0.40));

    public static final EquipmentDefinition DAGGER = EquipmentDefinition.of(
        "dagger", EquipmentSlotKind.MAINHAND, "minecraft:iron_sword",
        add(ModStats.ATTACK_DAMAGE.id(), 2),
        add(ModStats.CRIT_CHANCE.id(), 0.25),
        percent(ModStats.MOVEMENT_SPEED.id(), 0.10));

    public static final EquipmentDefinition EVASION_CHESTPLATE = EquipmentDefinition.of(
        "evasion_chestplate", EquipmentSlotKind.CHEST, "minecraft:chainmail_chestplate",
        add(ModStats.DODGE_CHANCE.id(), 0.15));

    public static final EquipmentDefinition HEAVY_ARMOR = EquipmentDefinition.of(
        "heavy_armor", EquipmentSlotKind.CHEST, "minecraft:iron_chestplate",
        add(ModStats.ARMOR.id(), 8),
        percent(ModStats.MOVEMENT_SPEED.id(), -0.15));

    public static final EquipmentCatalog CATALOG = EquipmentCatalog.builder(ModStats.CATALOG)
        .add(HEAVY_SWORD)
        .add(DAGGER)
        .add(EVASION_CHESTPLATE)
        .add(HEAVY_ARMOR)
        .build();

    private ModEquipment() {
    }
}
