package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.StatReader;
import io.github.alextixu.mobarmory.core.combat.CombatContext;
import io.github.alextixu.mobarmory.core.combat.CombatEffects;
import io.github.alextixu.mobarmory.core.combat.CombatOutcome;
import io.github.alextixu.mobarmory.core.combat.CombatResolver;
import io.github.alextixu.mobarmory.core.combat.DamageKind;
import io.github.alextixu.mobarmory.core.combat.RandomSource;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;

/**
 * Translates Forge combat events into core combat effects and applies the result. It never checks which
 * stat or effect it is running, so a new effect needs no change here (open/closed).
 * <ul>
 *   <li>Defensive effects (dodge) run on {@link LivingAttackEvent}, the very start of taking damage:
 *       cancelling it means the hit never landed (no knockback, no hurt animation).</li>
 *   <li>Offensive effects (crit) for a player's melee hit run on {@link CriticalHitEvent}, so a vanilla jump
 *       crit and our crit share Minecraft's one critical multiplier and never stack (O4).</li>
 *   <li>Offensive effects for everything else (mob melee, any projectile) run on {@link LivingHurtEvent}.</li>
 * </ul>
 * Everything runs on the server only; the server is the authority on damage.
 */
public final class CombatEffectListener {
    private final CombatEffects effects;
    private final StatAttributeBridge stats;

    public CombatEffectListener(CombatEffects effects, StatAttributeBridge stats) {
        this.effects = effects;
        this.stats = stats;
    }

    public void register() {
        LivingAttackEvent.BUS.addListener((Predicate<LivingAttackEvent>) this::onAttack);
        CriticalHitEvent.BUS.addListener(this::onCriticalHit);
        LivingHurtEvent.BUS.addListener((Consumer<LivingHurtEvent>) this::onHurt);
    }

    /** @return true to cancel the attack (dodged) */
    private boolean onAttack(LivingAttackEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide() || effects.defensive().isEmpty()) {
            return false;
        }
        DamageSource source = event.getSource();
        CombatContext ctx = new CombatContext(
            attackerOf(source), reader(defender), Math.max(0, event.getAmount()), kindOf(source), false);
        return CombatResolver.resolve(effects.defensive(), ctx, rng(defender)).cancelled();
    }

    private void onCriticalHit(CriticalHitEvent event) {
        Player attacker = event.getEntity();
        if (attacker.level().isClientSide() || effects.offensive().isEmpty()) {
            return;
        }
        StatReader defender = event.getTarget() instanceof LivingEntity target ? reader(target) : StatReader.NONE;
        // Damage 1.0 so the resulting "damage" is exactly the multiplier Minecraft should apply.
        CombatContext ctx = new CombatContext(reader(attacker), defender, 1.0, DamageKind.MELEE, event.isVanillaCritical());
        CombatOutcome outcome = CombatResolver.resolve(effects.offensive(), ctx, rng(attacker));
        if (outcome.critical()) {
            event.setResult(Result.ALLOW);
            event.setDamageModifier((float) outcome.damage());
        }
    }

    private void onHurt(LivingHurtEvent event) {
        LivingEntity defender = event.getEntity();
        DamageSource source = event.getSource();
        if (defender.level().isClientSide() || effects.offensive().isEmpty()
            || !(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        DamageKind kind = kindOf(source);
        if (attacker instanceof Player && kind == DamageKind.MELEE) {
            return; // already handled by onCriticalHit
        }
        CombatContext ctx = new CombatContext(reader(attacker), reader(defender), Math.max(0, event.getAmount()), kind, false);
        CombatOutcome outcome = CombatResolver.resolve(effects.offensive(), ctx, rng(attacker));
        event.setAmount((float) outcome.damage());
    }

    /** Melee if a living attacker hit directly, projectile if it shot or threw something, otherwise environment (O3). */
    static DamageKind kindOf(DamageSource source) {
        Entity cause = source.getEntity();
        if (!(cause instanceof LivingEntity)) {
            return DamageKind.ENVIRONMENT;
        }
        return source.getDirectEntity() == cause ? DamageKind.MELEE : DamageKind.PROJECTILE;
    }

    private StatReader attackerOf(DamageSource source) {
        return source.getEntity() instanceof LivingEntity attacker ? reader(attacker) : StatReader.NONE;
    }

    private StatReader reader(LivingEntity entity) {
        return new EntityStatReader(entity, stats);
    }

    private static RandomSource rng(LivingEntity entity) {
        return entity.getRandom()::nextDouble;
    }
}
