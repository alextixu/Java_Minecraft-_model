package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.mob.LeapBehavior;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;

/**
 * Leap ("衝刺", R4b decision Q5 A): Minecraft's own {@link LeapAtTargetGoal} with the spider's and wolf's jump height.
 * It leaps when its target is 2 to 4 blocks away and it stands on the ground; the target comes from attacking players
 * or neutral (O4). Ahead of the melee chase, so a leap can start while the mob closes in.
 */
public final class LeapGoalBinder implements GoalBinder<LeapBehavior> {
    /** Vanilla spiders and wolves leap 0.4 blocks per tick upwards. */
    static final float LEAP_HEIGHT = 0.4F;
    static final int LEAP_PRIORITY = 2;

    @Override
    public void bind(ArmoryMob mob, LeapBehavior behavior, Goals goals) {
        goals.goals().addGoal(LEAP_PRIORITY, new LeapAtTargetGoal(mob, LEAP_HEIGHT));
    }
}
