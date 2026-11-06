package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.mob.WanderBehavior;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;

/** Wandering, the way a vanilla zombie idles: stroll around (avoiding water) and look at nearby players. */
public final class WanderGoalBinder implements GoalBinder<WanderBehavior> {
    /** Lower than attacking, so a mob with a target stops wandering. Same priorities as the vanilla zombie. */
    static final int STROLL_PRIORITY = 7;
    static final int LOOK_PRIORITY = 8;

    @Override
    public void bind(ArmoryMob mob, WanderBehavior behavior, Goals goals) {
        goals.goals().addGoal(STROLL_PRIORITY, new WaterAvoidingRandomStrollGoal(mob, behavior.speed()));
        goals.goals().addGoal(LOOK_PRIORITY, new LookAtPlayerGoal(mob, Player.class, 8.0F));
        goals.goals().addGoal(LOOK_PRIORITY, new RandomLookAroundGoal(mob));
    }
}
