package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.mob.Behavior;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Every custom mob is this one class; what makes a brute a brute is its shared {@link MobDefinition}. Nothing here
 * is specific to any mob: behaviors come from {@link BehaviorGoals}, stats from the entity type's attributes
 * (built by {@link MobEntityFactory}) and equipment effects from R2's {@link EquipmentStatApplier}.
 * <p>
 * As a plain {@link Monster} it does not burn in daylight (O5), is removed on peaceful (its type is
 * {@code notInPeaceful}, O6), and drops 5 experience but no loot (O7).
 */
public final class ArmoryMob extends Monster {
    /** How far it looks for players and how far it chases them (O8, B15). */
    public static final double FOLLOW_RANGE = 16;

    private final MobDefinition definition;
    private final BehaviorGoals behaviors;
    private final Function<EquipmentId, Item> equipmentItems;
    private final Set<String> boundBehaviors;

    ArmoryMob(EntityType<? extends ArmoryMob> type, Level level, MobDefinition definition, BehaviorGoals behaviors,
              Function<EquipmentId, Item> equipmentItems) {
        // Set before super(): Mob's constructor calls registerGoals(), which reads them (Java 25 flexible constructor bodies).
        this.definition = definition;
        this.behaviors = behaviors;
        this.equipmentItems = equipmentItems;
        this.boundBehaviors = new HashSet<>();
        super(type, level);
    }

    public MobDefinition definition() {
        return definition;
    }

    /** Both goal selectors, for {@link GoalBinder}s. */
    public GoalBinder.Goals goals() {
        return new GoalBinder.Goals(goalSelector, targetSelector);
    }

    /**
     * Q6 A: the behaviors are fixed when the mob is created (spawned or loaded), because Minecraft's AI goals are
     * meant to be set up once. A changed definition reaches a mob the next time it is created.
     */
    @Override
    protected void registerGoals() {
        behaviors.bind(this, definition.behaviors(), goals());
    }

    /** Records that a behavior's goals were added; false if they already were. */
    boolean markBound(Behavior behavior) {
        return boundBehaviors.add(behavior.name());
    }

    /**
     * Spawn eggs and {@code /summon} (without NBT, like vanilla mobs) come through here: put on the definition's
     * equipment. A mob loaded from a save keeps what it was saved with instead.
     */
    @Override
    @SuppressWarnings("deprecation") // overriding (not calling) finalizeSpawn is how mobs set themselves up
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        // Minecraft gives each mob a random +-11% follow range; ours is exactly 16 blocks (O8).
        getAttribute(Attributes.FOLLOW_RANGE).removeModifier(RANDOM_SPAWN_BONUS_ID);
        equipFromDefinition();
        return result;
    }

    /** Exactly 5 experience (O7). Vanilla adds 1-3 per held item, which would make armed mobs worth more. */
    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return xpReward;
    }

    /**
     * Holds and wears what the definition lists. The stats follow through R2's equipment-change event on the next
     * tick, exactly as for a player. Drop chance 0: killing the mob never drops its equipment (O7, B12).
     */
    private void equipFromDefinition() {
        definition.equipment().forEach((kind, id) -> {
            EquipmentSlot slot = Slots.toMinecraft(kind);
            setItemSlot(slot, new ItemStack(equipmentItems.apply(id)));
            setDropChance(slot, 0.0F);
        });
    }
}
