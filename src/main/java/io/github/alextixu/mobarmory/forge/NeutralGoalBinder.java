package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.mob.NeutralBehavior;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

/**
 * Neutral (R4b, decision Q5 A): only Minecraft's own "hit back at whoever hurt me" and the melee chase, at the
 * behavior's speed ("run" above 1.0). Without a hunting goal it never starts a fight; others of its kind are not
 * called (O5), and it calms down by Minecraft's rules (O6). Same priorities as {@link AttackPlayersGoalBinder}.
 */
public final class NeutralGoalBinder implements GoalBinder<NeutralBehavior> {
    @Override
    public void bind(ArmoryMob mob, NeutralBehavior behavior, Goals goals) {
        goals.goals().addGoal(AttackPlayersGoalBinder.MELEE_PRIORITY, new MeleeAttackGoal(mob, behavior.speed(), false));
        goals.targets().addGoal(AttackPlayersGoalBinder.RETALIATE_PRIORITY, new HurtByTargetGoal(mob));
    }
}
