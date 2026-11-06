package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.combat.CombatOutcome;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hears what {@link CombatEffectListener} decided, without being able to change it (Observer). The damage display
 * (D1) uses it to know whether a hit was critical or dodged; the rules themselves stay in the combat effects.
 * Both methods run on the server thread, inside the attack, before Minecraft applies the damage.
 */
public interface CombatObserver {
    /** Hears nothing; {@link CombatEffectListener} behaves exactly as before D1 with this. */
    CombatObserver NONE = new CombatObserver() {
    };

    /**
     * The offensive effects (crit) decided an attack caused by a mob. Called for every such attack, critical or
     * not, so a later hit never inherits an earlier decision.
     *
     * @param target   who is being attacked (for a player's swing this may be something that is not alive)
     * @param attacker who attacks
     * @param outcome  what the effects decided; {@code outcome.critical()} tells whether the hit is critical
     */
    default void attackResolved(Entity target, LivingEntity attacker, CombatOutcome outcome) {
    }

    /**
     * The defensive effects (dodge) cancelled a hit. Minecraft has not checked invulnerability, creative mode or
     * the damage cooldown yet, so the hit might not have hurt anyway.
     */
    default void hitCancelled(LivingEntity defender, DamageSource source) {
    }
}
