package io.github.alextixu.mobarmory.content;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
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
 * Equipment is named by id, not by a {@code ModEquipment} constant, because R2b moves equipment to JSON files.
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

    public static final MobDefinition SENTINEL = MobDefinition.builder("sentinel")
        .look(MobLook.of(MobLook.Body.SKELETON))
        .stat(ModStats.MAX_HEALTH, 24)
        .stat(ModStats.ATTACK_DAMAGE, 3)
        .stat(ModStats.MOVEMENT_SPEED, 0.23)
        .equip(EquipmentSlotKind.MAINHAND, "dagger")
        .equip(EquipmentSlotKind.CHEST, "heavy_armor")
        .behavior(new WanderBehavior(0.8))
        .behavior(new AttackPlayersBehavior(1.2))
        .build();

    public static final List<MobDefinition> ALL = List.of(BRUTE, SKIRMISHER, SENTINEL);

    private ModMobs() {
    }

    /**
     * Every mob, checked against the stats and the given equipment (O2: a mistake stops the game at startup).
     * The equipment catalog is a parameter because where it comes from changes with R2b.
     */
    public static MobCatalog catalog(EquipmentCatalog equipment) {
        return MobCatalog.builder(ModStats.CATALOG, equipment).addAll(ALL).build();
    }
}
