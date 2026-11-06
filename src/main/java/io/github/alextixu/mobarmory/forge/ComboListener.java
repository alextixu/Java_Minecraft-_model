package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.combat.DamageKind;
import io.github.alextixu.mobarmory.core.combo.ComboDefinition;
import io.github.alextixu.mobarmory.core.combo.ComboInput;
import io.github.alextixu.mobarmory.core.combo.ComboLibrary;
import io.github.alextixu.mobarmory.core.combo.ComboTracker;
import io.github.alextixu.mobarmory.core.combo.InputClassifier;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

/**
 * Turns players' swings into combo inputs and applies the combo a swing completes (R3). Only translates Forge events;
 * the rules live in {@code core/combo} and the combos in the combo files. It never checks which combo or effect it
 * runs, so a new combo file or a new kind of effect needs no change here. R1's {@link CombatEffectListener} is not
 * touched: combos have their own listener.
 * <ol>
 *   <li>{@link AttackEntityEvent}, when the swing starts: reads the movement speed (walk or run, Q4 A) and the attack
 *       bar (Q6 A). Minecraft stops a sprint as soon as a sprinting hit lands, so this cannot wait (17, T1).</li>
 *   <li>{@link LivingHurtEvent}, when that swing really hurts its own target: the swing counts if it was full
 *       strength. The player's {@link ComboTracker} matches it with the last one; a completed combo's effect changes
 *       this hit's damage (before armor, after a crit, O3) and the {@link CombatObserver} hears about it (D1 shows the
 *       name). Listens last, so a hit another mod cancels never counts (B19). A dodged hit, a hit that does nothing in
 *       the damage cooldown, a swing at the air, a sweep's side targets and arrows never reach this point (Q6 A, B10).</li>
 * </ol>
 * Only players have combos (O1). State lives on the server, one tracker per player, and is dropped when the player
 * dies or logs out (O10, B16).
 */
public final class ComboListener {
    private final ComboLibrary combos;
    private final InputClassifier inputs;
    private final float fullCharge;
    private final StatAttributeBridge stats;
    private final CombatObserver observer;
    /** The swing each player started this tick; written and read on the server thread within one attack. */
    private final Map<Player, Swing> swings = new WeakHashMap<>();
    private final Map<Player, ComboTracker> trackers = new WeakHashMap<>();

    private record Swing(Entity target, ComboInput input, boolean fullStrength, long gameTime) {
    }

    /**
     * @param combos     the combos in effect; read on every swing, so a reload applies right away
     * @param inputs     walk or run
     * @param fullCharge the attack bar must be above this for a swing to count
     * @param stats      reads the movement speed
     * @param observer   hears every combo; {@link CombatObserver#NONE} for nobody
     */
    public ComboListener(ComboLibrary combos, InputClassifier inputs, float fullCharge, StatAttributeBridge stats,
                         CombatObserver observer) {
        this.combos = Objects.requireNonNull(combos, "combos");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.fullCharge = fullCharge;
        this.stats = Objects.requireNonNull(stats, "stats");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    public void register() {
        AttackEntityEvent.BUS.addListener((Consumer<AttackEntityEvent>) this::onSwing);
        LivingHurtEvent.BUS.addListener(Priority.LOWEST, (Consumer<LivingHurtEvent>) this::onHurt);
        LivingDeathEvent.BUS.addListener((Consumer<LivingDeathEvent>) event -> forget(event.getEntity()));
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> forget(event.getEntity()));
    }

    private void onSwing(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        ComboInput input = inputs.classify(new EntityStatReader(player, stats));
        boolean fullStrength = player.getAttackStrengthScale(0.5F) > fullCharge;
        swings.put(player, new Swing(event.getTarget(), input, fullStrength, player.level().getGameTime()));
    }

    private void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof Player player)
            || CombatEffectListener.kindOf(event.getSource()) != DamageKind.MELEE) {
            return;
        }
        long now = target.level().getGameTime();
        Swing swing = swings.get(player);
        if (swing == null || swing.target() != target || swing.gameTime() != now) {
            return; // a sweep's side target, or no swing of this player started this tick
        }
        swings.remove(player);
        if (!swing.fullStrength()) {
            return; // does not count, and does not replace the last swing (Q6 A, B5)
        }
        Optional<ComboDefinition> combo = trackers.computeIfAbsent(player, p -> new ComboTracker())
            .record(combos.current(), swing.input(), now);
        if (combo.isPresent()) {
            event.setAmount((float) combo.get().effect().apply(event.getAmount()));
            observer.comboTriggered(target, player, combo.get());
        }
    }

    private void forget(Entity entity) {
        if (entity instanceof Player player) {
            trackers.remove(player);
            swings.remove(player);
        }
    }
}
