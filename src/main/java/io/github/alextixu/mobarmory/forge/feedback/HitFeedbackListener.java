package io.github.alextixu.mobarmory.forge.feedback;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.core.combat.CombatOutcome;
import io.github.alextixu.mobarmory.core.combo.ComboDefinition;
import io.github.alextixu.mobarmory.core.feedback.HitFilter;
import io.github.alextixu.mobarmory.core.feedback.HitReport;
import io.github.alextixu.mobarmory.core.feedback.Party;
import io.github.alextixu.mobarmory.forge.CombatEffectListener;
import io.github.alextixu.mobarmory.forge.CombatObserver;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import org.slf4j.Logger;

/**
 * Builds a {@link HitReport} for every hit and hands it to each {@link HitDisplay} (Observer, D1).
 * <ul>
 *   <li>A landed hit is reported from {@link LivingDamageEvent}, which carries the final damage after armor,
 *       resistance and absorption. It listens at the lowest priority, so another mod's change to the damage is
 *       already in (B16).</li>
 *   <li>Whether the hit was critical comes from {@link CombatEffectListener} through {@link CombatObserver}. It
 *       keeps the latest attack decision per target; every decision replaces the previous one, a dodge clears it,
 *       and a landed hit uses it once, only if it came from the same attacker in the same game tick. So a crit
 *       that was dodged (B8) or swallowed by the damage cooldown (B9) never marks a later hit.</li>
 *   <li>A dodge is reported when {@link CombatEffectListener} cancels a hit, but only if the hit could have hurt:
 *       not against creative mode, an invulnerable or dead defender, or one inside its damage cooldown (O5).</li>
 * </ul>
 * Which hits are shown and to whom is decided in {@link HitFilter}, not here.
 * <p>
 * R3: a combo is matched to its hit the same way as a crit: the latest combo per target, used once, only by the same
 * attacker in the same game tick. The report then carries the combo's name.
 */
public final class HitFeedbackListener implements CombatObserver {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Same as Minecraft: in the first half of the cooldown a weaker hit does nothing. */
    private static final int COOLDOWN_HALF = 10;

    private final List<HitDisplay> displays = new CopyOnWriteArrayList<>();
    /** Latest attack decision per target. Written and read on the server thread within one attack. */
    private final Map<Entity, Decision> decisions = new WeakHashMap<>();

    /** Latest combo per target (R3). Same thread and lifetime as {@link #decisions}. */
    private final Map<Entity, ComboNote> combos = new WeakHashMap<>();

    private record Decision(LivingEntity attacker, boolean critical, long gameTime) {
    }

    private record ComboNote(LivingEntity attacker, ComboDefinition combo, long gameTime) {
    }

    public HitFeedbackListener(List<HitDisplay> displays) {
        this.displays.addAll(displays);
    }

    /**
     * Listens at the lowest priority with a plain listener, not a monitor: in EventBus 7.0.6 an event whose only
     * listeners are monitors gets a no-op invoker (checked in its {@code CancellableEventBusImpl.invalidateInvoker}),
     * and nothing else in Mob Armory listens to {@link LivingDamageEvent}. A cancelled event stops before reaching a
     * plain listener, so a cancelled hit is never shown.
     */
    public void register() {
        LivingDamageEvent.BUS.addListener(Priority.LOWEST, (Consumer<LivingDamageEvent>) this::onDamage);
    }

    /** Adds a display while the game runs. The game tests use this to watch the same reports the action bar gets. */
    public void addDisplay(HitDisplay display) {
        displays.add(display);
    }

    public void removeDisplay(HitDisplay display) {
        displays.remove(display);
    }

    @Override
    public void attackResolved(Entity target, LivingEntity attacker, CombatOutcome outcome) {
        decisions.put(target, new Decision(attacker, outcome.critical(), target.level().getGameTime()));
    }

    @Override
    public void comboTriggered(LivingEntity target, Player attacker, ComboDefinition combo) {
        combos.put(target, new ComboNote(attacker, combo, target.level().getGameTime()));
    }

    @Override
    public void hitCancelled(LivingEntity defender, DamageSource source) {
        decisions.remove(defender);
        if (!(defender.level() instanceof ServerLevel level) || !couldBeHurt(level, defender, source)) {
            return;
        }
        LivingEntity attacker = attackerOf(source);
        show(HitReport.dodged(CombatEffectListener.kindOf(source), party(attacker), party(defender),
            defender.getHealth(), defender.getMaxHealth()), defender, attacker);
    }

    private void onDamage(LivingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        LivingEntity attacker = attackerOf(source);
        boolean critical = wasCritical(defender, attacker);
        show(HitReport.landed(CombatEffectListener.kindOf(source), party(attacker), party(defender), critical,
            event.getAmount(), defender.getHealth(), defender.getMaxHealth()).withCombo(comboOf(defender, attacker)),
            defender, attacker);
    }

    /** The name of the combo this hit completed, or null. */
    private String comboOf(LivingEntity defender, LivingEntity attacker) {
        ComboNote note = combos.remove(defender);
        return note != null && attacker != null && note.attacker() == attacker
            && note.gameTime() == defender.level().getGameTime() ? note.combo().name() : null;
    }

    private boolean wasCritical(LivingEntity defender, LivingEntity attacker) {
        Decision decision = decisions.remove(defender);
        return decision != null && attacker != null && decision.attacker() == attacker
            && decision.gameTime() == defender.level().getGameTime() && decision.critical();
    }

    /**
     * Would this hit have hurt if it had not been dodged? Our dodge runs before Minecraft checks these, so without
     * this a creative player would see MISS for hits that could never hurt them (B3).
     */
    static boolean couldBeHurt(ServerLevel level, LivingEntity defender, DamageSource source) {
        if (defender.isDeadOrDying() || defender.isInvulnerableTo(level, source)) {
            return false;
        }
        if (defender instanceof Player player && player.getAbilities().invulnerable
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FIRE) && defender.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        }
        return defender.damageCooldownTime <= COOLDOWN_HALF || source.is(DamageTypeTags.BYPASSES_COOLDOWN);
    }

    private void show(HitReport report, LivingEntity defender, LivingEntity attacker) {
        if (!HitFilter.shows(report)) {
            return;
        }
        for (HitDisplay display : displays) {
            try {
                display.show(report, defender, attacker);
            } catch (RuntimeException e) {
                // A broken display must never break combat itself.
                LOGGER.error("Damage display {} failed for {}", display, report, e);
            }
        }
    }

    private static LivingEntity attackerOf(DamageSource source) {
        return source.getEntity() instanceof LivingEntity attacker ? attacker : null;
    }

    private static Party party(LivingEntity entity) {
        if (entity == null) {
            return Party.NONE;
        }
        return entity instanceof Player ? Party.PLAYER : Party.MOB;
    }
}
