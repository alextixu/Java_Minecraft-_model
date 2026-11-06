package io.github.alextixu.mobarmory.content;

import io.github.alextixu.mobarmory.core.combat.CombatEffects;
import io.github.alextixu.mobarmory.core.combat.CritEffect;
import io.github.alextixu.mobarmory.core.combat.DodgeEffect;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import java.util.List;

/**
 * The built-in stats: the ones Java code relies on (the combat effects below, the default mob files in {@code mobarmory/default_mobs/}), and what
 * the first start writes to {@code config/mobarmory/stats/}.
 * <p>
 * Since section E (24-stat-files-boundary-note.md) this is no longer every stat in the game: players add stats with a
 * JSON file in {@code config/mobarmory/stats/} and a restart, and the game's full list is
 * {@code MobArmoryMod.STATS.catalog()}. Those files may change a built-in stat's numbers but not its kind.
 * The constants, {@link #CATALOG} and {@link #COMBAT_EFFECTS} stay as they were, so code that uses them (and unit
 * tests, which run without files) is unchanged.
 * <p>
 * Custom stats are attached to every living entity automatically; vanilla-backed ones reuse Minecraft's own attribute,
 * so their effect (walking speed, melee damage, armor) is Minecraft's. A stat with a combat effect still needs Java:
 * a {@code StatEffect} and a line in {@link #COMBAT_EFFECTS}. A built-in custom stat's name is in
 * {@code assets/mobarmory/lang/en_us.json}.
 */
public final class ModStats {
    public static final StatDefinition ATTACK_DAMAGE =
        StatDefinition.vanilla("attack_damage", "minecraft:attack_damage", 1.0, 0, 2048);
    /** Never below 30% of the entity's base speed, however many heavy items are stacked (Q4 A). */
    public static final StatDefinition MOVEMENT_SPEED =
        StatDefinition.vanilla("movement_speed", "minecraft:movement_speed", 0.1, 0, 1024).withFloorRatio(0.3);
    public static final StatDefinition ARMOR =
        StatDefinition.vanilla("armor", "minecraft:armor", 0, 0, 30);
    /** Swings per second; the attack bar refills in 20 / attack speed ticks. Added for N3 (heavy weapons swing slower). */
    public static final StatDefinition ATTACK_SPEED =
        StatDefinition.vanilla("attack_speed", "minecraft:attack_speed", 4.0, 0, 1024);
    /** Hit points when unhurt. Added for R4 (O9) so a mob definition can set it; 1-1024 like Minecraft's own limit. */
    public static final StatDefinition MAX_HEALTH =
        StatDefinition.vanilla("max_health", "minecraft:max_health", 20.0, 1, 1024);
    /** Chance that an attack deals 1.5x damage. Capped at 100%. */
    public static final StatDefinition CRIT_CHANCE = StatDefinition.custom("crit_chance", 0, 0, 1.0);
    /** Chance to avoid damage caused by a mob (melee or projectile). Capped at 75%. */
    public static final StatDefinition DODGE_CHANCE = StatDefinition.custom("dodge_chance", 0, 0, 0.75);

    public static final StatCatalog CATALOG = StatCatalog.builder()
        .add(ATTACK_DAMAGE)
        .add(MOVEMENT_SPEED)
        .add(ARMOR)
        .add(ATTACK_SPEED)
        .add(MAX_HEALTH)
        .add(CRIT_CHANCE)
        .add(DODGE_CHANCE)
        .build();

    /** What stats do in combat. A stat with no entry here is still tracked and shown, it just has no combat effect. */
    public static final CombatEffects COMBAT_EFFECTS = new CombatEffects(
        List.of(new DodgeEffect(DODGE_CHANCE)),   // defensive: before the defender takes damage
        List.of(new CritEffect(CRIT_CHANCE)));    // offensive: when an attack lands

    private ModStats() {
    }
}
