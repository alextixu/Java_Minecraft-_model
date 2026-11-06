package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.mob.Behavior;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/**
 * Turns one kind of {@link Behavior} into Minecraft AI goals (Strategy). One binder per behavior kind, registered in
 * {@link BehaviorGoals}; the mob class itself never checks which behaviors it has.
 *
 * @param <B> the behavior this binder understands
 */
@FunctionalInterface
public interface GoalBinder<B extends Behavior> {

    /** The two places a binder may add goals: what the mob does, and whom it targets. */
    record Goals(GoalSelector goals, GoalSelector targets) {
    }

    /** Adds the goals that make {@code mob} do {@code behavior}. Called while the mob is being created. */
    void bind(ArmoryMob mob, B behavior, Goals goals);
}
