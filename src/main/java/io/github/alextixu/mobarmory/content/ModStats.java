package io.github.alextixu.mobarmory.content;

import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;

/**
 * Every stat in the game (decision Q5 A: add a line here and restart the game).
 * Custom stats are attached to every living entity automatically; vanilla-backed ones reuse
 * Minecraft's own attribute, so their effect (walking speed, melee damage, armor) is Minecraft's.
 * A new stat also needs one line in {@code assets/mobarmory/lang/en_us.json}.
 */
public final class ModStats {
    public static final StatDefinition ATTACK_DAMAGE =
        StatDefinition.vanilla("attack_damage", "minecraft:attack_damage", 1.0, 0, 2048);
    /** Never below 30% of the entity's base speed, however many heavy items are stacked (Q4 A). */
    public static final StatDefinition MOVEMENT_SPEED =
        StatDefinition.vanilla("movement_speed", "minecraft:movement_speed", 0.1, 0, 1024).withFloorRatio(0.3);
    public static final StatDefinition ARMOR =
        StatDefinition.vanilla("armor", "minecraft:armor", 0, 0, 30);
    /** Chance that an attack deals 1.5x damage. Capped at 100%. */
    public static final StatDefinition CRIT_CHANCE = StatDefinition.custom("crit_chance", 0, 0, 1.0);
    /** Chance to avoid damage caused by a mob (melee or projectile). Capped at 75%. */
    public static final StatDefinition DODGE_CHANCE = StatDefinition.custom("dodge_chance", 0, 0, 0.75);

    public static final StatCatalog CATALOG = StatCatalog.builder()
        .add(ATTACK_DAMAGE)
        .add(MOVEMENT_SPEED)
        .add(ARMOR)
        .add(CRIT_CHANCE)
        .add(DODGE_CHANCE)
        .build();

    private ModStats() {
    }
}
