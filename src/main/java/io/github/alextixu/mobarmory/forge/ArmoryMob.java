package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.mob.Behavior;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Every custom mob is this one class; what makes a brute a brute is its definition, which it looks up by id in the
 * {@link MobLibrary} every time (R4b), so a definition changed on the web page or by a reload reaches it. Nothing here
 * is specific to any mob: behaviors come from {@link BehaviorGoals}, stats from the definition, equipment effects from
 * R2's {@link EquipmentStatApplier}.
 * <p>
 * When the definition changes (R4b decision Q6 A): name, stats and size apply at once ({@link #applyDefinitionStats});
 * behaviors and equipment when the mob is next created (spawned, or loaded from a save); the body after a restart.
 * <p>
 * As a plain {@link Monster} it does not burn in daylight (O5), is removed on peaceful (its type is
 * {@code notInPeaceful}, O6), and drops 5 experience but no loot (O7).
 */
public final class ArmoryMob extends Monster {
    /** How far it looks for players and how far it chases them (O8, B15). */
    public static final double FOLLOW_RANGE = 16;

    /** What every custom mob needs from the rest of the mod; one object so the factory passes it along unchanged. */
    record Parts(MobLibrary library, StatAttributeBridge stats, BehaviorGoals behaviors,
                 Function<MobDefinition, Map<EquipmentSlotKind, ItemStack>> outfit, Consumer<LivingEntity> statsChanged) {
    }

    private final MobId id;
    private final Parts parts;
    /** The definition when this mob was created; its behaviors stay until the mob is created again (Q6 A). */
    private final MobDefinition created;
    private final Set<String> boundBehaviors;

    ArmoryMob(EntityType<? extends ArmoryMob> type, Level level, MobId id, Parts parts) {
        // Set before super(): Mob's constructor calls registerGoals(), which reads them (Java 25 flexible constructor bodies).
        this.id = id;
        this.parts = parts;
        this.created = parts.library().current().get(id);
        this.boundBehaviors = new HashSet<>();
        super(type, level);
    }

    public MobId mobId() {
        return id;
    }

    /** The definition in effect now (R4b): stats and name follow it at once. */
    public MobDefinition definition() {
        return parts.library().current().get(id);
    }

    /** The definition this mob was created with; its behaviors are the ones this mob has. */
    public MobDefinition createdWith() {
        return created;
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
        parts.behaviors().bind(this, created.behaviors(), goals());
    }

    /** Records that a behavior's goals were added; false if they already were. */
    boolean markBound(Behavior behavior) {
        return boundBehaviors.add(behavior.name());
    }

    /** The name in the mob file (O13); on a remote client, the name the server sent (Q7 A). */
    @Override
    protected Component getTypeName() {
        return Component.literal(MobNames.of(id, parts.library()));
    }

    /**
     * Spawn eggs and {@code /summon} (without NBT, like vanilla mobs) come through here: take the stats and equipment
     * of the definition in effect now, at full health. A mob loaded from a save keeps its equipment and health instead.
     */
    @Override
    @SuppressWarnings("deprecation") // overriding (not calling) finalizeSpawn is how mobs set themselves up
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        // Minecraft gives each mob a random +-11% follow range; ours is exactly 16 blocks (O8).
        getAttribute(Attributes.FOLLOW_RANGE).removeModifier(RANDOM_SPAWN_BONUS_ID);
        takeDefinitionStats();
        setHealth(getMaxHealth());
        equipFromDefinition();
        return result;
    }

    /**
     * Spawned or loaded from a save: the base stats follow the definition in effect now (H12), never stacked. The
     * equipment comes on top on the first tick, when Minecraft notices what the mob holds (R2's equipment-change event),
     * exactly as for a freshly spawned mob in R4.
     */
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (level() instanceof ServerLevel) {
            takeDefinitionStats();
        }
    }

    /**
     * A mob already in the world after its definition changed (Q6 A, H4, H11): new base stats and size, then R2
     * recomputes the equipment on top at once (B22: the speed floor follows the new base). Minecraft lowers the current
     * health if it is above a lower maximum and never heals (O10, B17).
     */
    public void applyDefinitionStats() {
        takeDefinitionStats();
        parts.statsChanged().accept(this);
    }

    /** Every stat's base value and the size, from the definition in effect now. */
    private void takeDefinitionStats() {
        MobDefinition now = definition();
        now.spawnStats(parts.stats().catalog()).forEach((stat, value) -> {
            AttributeInstance attribute = getAttribute(parts.stats().holder(stat));
            if (attribute != null) {
                attribute.setBaseValue(value);
            }
        });
        getAttribute(Attributes.SCALE).setBaseValue(now.look().scale());
    }

    /** Exactly 5 experience (O7). Vanilla adds 1-3 per held item, which would make armed mobs worth more. */
    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return xpReward;
    }

    /**
     * Holds and wears what the definition lists, as far as those weapons exist now (O2 changed to B: a missing piece
     * is left out). The stats follow through R2's equipment-change event on the next tick, exactly as for a player.
     * Drop chance 0: killing the mob never drops its equipment (O7, B12).
     */
    private void equipFromDefinition() {
        parts.outfit().apply(definition()).forEach((kind, stack) -> {
            EquipmentSlot slot = Slots.toMinecraft(kind);
            setItemSlot(slot, stack);
            setDropChance(slot, 0.0F);
        });
    }
}
