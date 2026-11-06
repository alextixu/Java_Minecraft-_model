package io.github.alextixu.mobarmory.content;

import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.mob.AttackPlayersBehavior;
import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobLook;
import io.github.alextixu.mobarmory.core.mob.WanderBehavior;
import java.util.List;

/**
 * The prototype's mobs (decision O1). Adding a mob means one definition here (and in {@link #ALL}), its name in
 * {@code assets/mobarmory/lang/en_us.json} and its spawn egg's item model in {@code assets/mobarmory/items/}.
 * Entity type, attributes, AI, renderer and spawn egg are all built from the definition.
 * <p>
 * Equipment is named by id, because weapons are JSON files in {@code config/mobarmory/weapons/} (R2b). A mob whose
 * weapon is missing or works in another slot spawns without it (O2 changed to B on 11/07).
 */
public final class ModMobs {
    public static final MobDefinition BRUTE = MobDefinition.builder("brute")
        .look(MobLook.of(MobLook.Body.ZOMBIE).scaled(1.2))
        .stat(ModStats.MAX_HEALTH, 40)
        .stat(ModStats.ATTACK_DAMAGE, 4)
        .stat(ModStats.MOVEMENT_SPEED, 0.25)
        .equip(EquipmentSlotKind.MAINHAND, "heavy_sword")
        .behavior(new WanderBehavior(1.0))
        .behavior(new AttackPlayersBehavior(1.0))
        .build();

    public static final MobDefinition SKIRMISHER = MobDefinition.builder("skirmisher")
        .look(MobLook.of(MobLook.Body.HUSK))
        .stat(ModStats.MAX_HEALTH, 16)
        .stat(ModStats.ATTACK_DAMAGE, 2)
        .stat(ModStats.MOVEMENT_SPEED, 0.32)
        .stat(ModStats.CRIT_CHANCE, 0.25)
        .stat(ModStats.DODGE_CHANCE, 0.30)
        .equip(EquipmentSlotKind.MAINHAND, "dagger")
        .behavior(new WanderBehavior(1.0))
        .behavior(new AttackPlayersBehavior(1.0))
        .build();

    public static final List<MobDefinition> ALL = List.of(BRUTE, SKIRMISHER);

    private ModMobs() {
    }

    /**
     * Every mob, checked against the stats (O2: a mistake stops the game at startup). Equipment is looked up when a
     * mob spawns, from the weapon files in effect then; a missing piece is left out with a warning (O2 changed to B).
     */
    public static MobCatalog catalog() {
        return MobCatalog.builder(ModStats.CATALOG).addAll(ALL).build();
    }
}
