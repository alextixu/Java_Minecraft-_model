package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.mob.AttackPlayersBehavior;
import io.github.alextixu.mobarmory.core.mob.Behavior;
import io.github.alextixu.mobarmory.core.mob.LeapBehavior;
import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.NeutralBehavior;
import io.github.alextixu.mobarmory.core.mob.WanderBehavior;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The table from behavior kind to {@link GoalBinder}. Adding a behavior is one {@link #register} line in
 * {@link #standard()}; mobs and other behaviors do not change.
 */
public final class BehaviorGoals {
    private final Map<Class<? extends Behavior>, GoalBinder<?>> binders = new LinkedHashMap<>();

    /** Every behavior the mod knows. */
    public static BehaviorGoals standard() {
        return new BehaviorGoals()
            .register(WanderBehavior.class, new WanderGoalBinder())
            .register(AttackPlayersBehavior.class, new AttackPlayersGoalBinder())
            .register(NeutralBehavior.class, new NeutralGoalBinder())   // R4b
            .register(LeapBehavior.class, new LeapGoalBinder());        // R4b
    }

    public <B extends Behavior> BehaviorGoals register(Class<B> kind, GoalBinder<B> binder) {
        if (binders.putIfAbsent(kind, binder) != null) {
            throw new IllegalArgumentException("Behavior " + kind.getSimpleName() + " has two binders");
        }
        return this;
    }

    /** The behavior kinds that have a binder. */
    public Set<Class<? extends Behavior>> kinds() {
        return Set.copyOf(binders.keySet());
    }

    /**
     * B4: every behavior of every mob has a binder, or the game stops at startup naming the mob and the behavior.
     *
     * @throws IllegalStateException on the first behavior without a binder
     */
    public void verify(MobCatalog catalog) {
        for (MobDefinition mob : catalog.all()) {
            for (Behavior behavior : mob.behaviors()) {
                if (!binders.containsKey(behavior.getClass())) {
                    throw new IllegalStateException(
                        "Mob '" + mob.id() + "' uses behavior '" + behavior.name() + "', which has no goal binder");
                }
            }
        }
    }

    /**
     * Gives the mob the goals of each behavior it has not been given yet, so binding the same definition twice
     * never adds a second copy.
     */
    public void bind(ArmoryMob mob, List<Behavior> behaviors, GoalBinder.Goals goals) {
        for (Behavior behavior : behaviors) {
            if (mob.markBound(behavior)) {
                binderFor(behavior).bind(mob, behavior, goals);
            }
        }
    }

    /** @throws IllegalStateException if this kind of behavior has no binder */
    @SuppressWarnings("unchecked") // the table only pairs a kind with a binder for that same kind (see register)
    public <B extends Behavior> GoalBinder<B> binderFor(B behavior) {
        GoalBinder<B> binder = (GoalBinder<B>) binders.get(behavior.getClass());
        if (binder == null) {
            throw new IllegalStateException("Behavior '" + behavior.name() + "' has no goal binder");
        }
        return binder;
    }
}
