package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.mob.AttackPlayersBehavior;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Attacking players (Q7 A): hit back at whoever hurts the mob, hunt the nearest player it can see, and chase the
 * target in melee. Minecraft's own targeting skips creative and spectator players (B8); the search and chase
 * distance is the mob's follow range, 16 blocks (O8, B15). Other mobs are only ever attacked in return (B9).
 */
public final class AttackPlayersGoalBinder implements GoalBinder<AttackPlayersBehavior> {
    static final int MELEE_PRIORITY = 3;
    static final int RETALIATE_PRIORITY = 1;
    static final int HUNT_PLAYERS_PRIORITY = 2;

    @Override
    public void bind(ArmoryMob mob, AttackPlayersBehavior behavior, Goals goals) {
        goals.goals().addGoal(MELEE_PRIORITY, new MeleeAttackGoal(mob, behavior.speed(), false));
        goals.targets().addGoal(RETALIATE_PRIORITY, new HurtByTargetGoal(mob));
        goals.targets().addGoal(HUNT_PLAYERS_PRIORITY, new NearestAttackableTargetGoal<>(mob, Player.class, true));
    }
}
