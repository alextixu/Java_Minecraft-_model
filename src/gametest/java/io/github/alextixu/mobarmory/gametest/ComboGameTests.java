package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.FeedbackGameTests.checkLine;
import static io.github.alextixu.mobarmory.gametest.FeedbackGameTests.cow;
import static io.github.alextixu.mobarmory.gametest.FeedbackGameTests.ready;
import static io.github.alextixu.mobarmory.gametest.FeedbackGameTests.setBase;
import static io.github.alextixu.mobarmory.gametest.FeedbackGameTests.swing;
import static io.github.alextixu.mobarmory.gametest.FeedbackGameTests.toughCow;
import static io.github.alextixu.mobarmory.gametest.FeedbackGameTests.value;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModCombos;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.feedback.HitResult;
import io.github.alextixu.mobarmory.data.ComboFiles;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import io.github.alextixu.mobarmory.gametest.FeedbackGameTests.Recorder;
import io.github.alextixu.mobarmory.gametest.FeedbackGameTests.Sent;
import io.github.alextixu.mobarmory.gametest.FeedbackGameTests.Shown;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.listener.EventListener;

/**
 * System tests for R3, combos (18-r3-combo-given-when-then.md). A mock player really swings at cows through
 * Minecraft's attack: a walking swing is a plain swing, a running swing is the same swing after
 * {@code setSprinting(true)}, which adds Minecraft's own x1.3 speed bonus, exactly like pressing the sprint key. The
 * combos are the shipped combo files (Lunge: walk, run, x1.5; Follow-up: run, walk, x1.3; 30 ticks), loaded from
 * {@code config/mobarmory/combos/} like in a real game. D1's recorder sees each report and action bar line.
 */
final class ComboGameTests {
    private static final EquipmentId HEAVY_SWORD = EquipmentId.of("heavy_sword");
    private static final ComboFiles COMBOS = MobArmoryMod.COMBO_FILES;

    private ComboGameTests() {
    }

    static void registerAll() {
        test("combo_order_decides_the_combo", ComboGameTests::orderDecidesTheCombo);
        test("combo_window_in_real_ticks", ComboGameTests::windowInRealTicks);
        test("combo_heavy_sword_is_too_slow_to_run", ComboGameTests::heavySwordIsTooSlowToRun);
        test("combo_counts_only_landed_full_swings", ComboGameTests::countsOnlyLandedFullSwings);
        test("combo_per_player_any_target_no_chain", ComboGameTests::perPlayerAnyTargetNoChain);
        test("combo_stacks_with_crit", ComboGameTests::stacksWithCrit);
        test("combo_new_file_adds_a_combo", ComboGameTests::newFileAddsACombo);
    }

    // ---------------------------------------------------------------- helpers

    /** A full-strength swing, standing on the ground, at a target out of its damage cooldown. */
    private static double walkSwing(ServerPlayer player, LivingEntity target) {
        charge(player);
        target.damageCooldownTime = 0;
        return swing(player, target);
    }

    /** The same swing while sprinting: Minecraft adds its x1.3 movement speed bonus. */
    private static double runSwing(ServerPlayer player, LivingEntity target) {
        charge(player);
        player.setSprinting(true);
        target.damageCooldownTime = 0;
        return swing(player, target);
    }

    /** Refills the attack bar completely, also for a slow weapon. */
    private static void charge(ServerPlayer player) {
        ready(player);
        for (int i = 0; i < 100 && player.getAttackStrengthScale(0.5F) < 1; i++) {
            player.doTick();
        }
    }

    private static String comboOf(Recorder recorder) {
        Shown last = recorder.last();
        return last == null ? "no report" : last.report().combo();
    }

    // ---------------------------------------------------------------- tests

    /**
     * H1, H2, H7: walk then run is Lunge (1.0 then 1.5); run then walk is Follow-up (1.0 then 1.3). Same two kinds of
     * swing, opposite order, a different combo and a different multiplier. The running swing still counts as running
     * although Minecraft ends the sprint as soon as it lands (H7).
     */
    static void orderDecidesTheCombo(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Cow first = cow(helper);
        Cow second = cow(helper);
        try (Recorder recorder = new Recorder(first, second)) {
            checkNear(helper, 1.0, walkSwing(player, first), "H1 walking swing");
            check(helper, comboOf(recorder) == null, "H3: one swing is no combo, got " + comboOf(recorder));
            checkNear(helper, 1.5, runSwing(player, first), "H1 walk then run: 1.0 x 1.5");
            check(helper, !player.isSprinting(), "Minecraft ended the sprint when the running swing landed");
            check(helper, "Lunge".equals(comboOf(recorder)), "H1 + H7: Lunge, got " + comboOf(recorder));
            Sent lunge = recorder.sentTo(player).getLast();
            checkLine(helper, lunge, "mobarmory.hit.dealt", List.of("1.5", "7.5", "10"), "H1 action bar");
            check(helper, "Lunge".equals(lunge.combo()), "H1: the action bar ends with Lunge, got " + lunge.combo());

            checkNear(helper, 1.0, runSwing(player, second), "H2 running swing");
            check(helper, comboOf(recorder) == null, "Lunge started over, so this run is a first swing");
            checkNear(helper, 1.3, walkSwing(player, second), "H2 run then walk: 1.0 x 1.3");
            check(helper, "Follow-up".equals(comboOf(recorder)), "H2: Follow-up, got " + comboOf(recorder));
            Sent followUp = recorder.sentTo(player).getLast();
            checkLine(helper, followUp, "mobarmory.hit.dealt", List.of("1.3", "7.7", "10"), "H2 action bar");
            check(helper, "Follow-up".equals(followUp.combo()), "H2: the action bar ends with Follow-up, got " + followUp.combo());
        }
        helper.succeed();
    }

    /**
     * O5 and B1 with real game ticks: 20 ticks after a walking swing a running swing is still Lunge; 40 ticks after,
     * it is too late and becomes the first swing of a new combo (a walk right after it is Follow-up).
     */
    static void windowInRealTicks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Cow cow = cow(helper); // 10 health, so 1.3 is exact enough in Minecraft's float health; 5.8 is dealt in all
        Recorder recorder = new Recorder(cow);
        long[] start = new long[1];
        helper.startSequence()
            .thenExecute(() -> {
                walkSwing(player, cow);
                start[0] = helper.getLevel().getGameTime();
            })
            .thenIdle(20)
            .thenExecute(() -> {
                long elapsed = helper.getLevel().getGameTime() - start[0];
                check(helper, elapsed >= 20 && elapsed <= 30, "within the window, " + elapsed + " ticks");
                checkNear(helper, 1.5, runSwing(player, cow), "a run " + elapsed + " ticks after the walk is Lunge");
                check(helper, "Lunge".equals(comboOf(recorder)), "Lunge after " + elapsed + " ticks, got " + comboOf(recorder));
                walkSwing(player, cow);
                start[0] = helper.getLevel().getGameTime();
            })
            .thenIdle(40)
            .thenExecute(() -> {
                long elapsed = helper.getLevel().getGameTime() - start[0];
                check(helper, elapsed > 30, "past the window, " + elapsed + " ticks");
                checkNear(helper, 1.0, runSwing(player, cow), "B1: a run " + elapsed + " ticks after the walk is plain");
                check(helper, comboOf(recorder) == null, "B1: no combo after " + elapsed + " ticks, got " + comboOf(recorder));
                checkNear(helper, 1.3, walkSwing(player, cow), "B1: the late run started a new combo");
                check(helper, "Follow-up".equals(comboOf(recorder)), "B1: Follow-up, got " + comboOf(recorder));
                recorder.close();
            })
            .thenSucceed();
    }

    /**
     * B12 (Q4 A, "combo 要和數值結合"): with the heavy sword (-30% speed) sprinting reads 0.091, below 0.12, so both
     * swings are walking swings and walk then "run" is no combo. Without the sword the same moves are Lunge.
     */
    static void heavySwordIsTooSlowToRun(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Cow cow = toughCow(helper);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MobArmoryMod.EQUIPMENT.item(HEAVY_SWORD)));
        try (Recorder recorder = new Recorder(cow)) {
            charge(player); // equipment stats are applied when the player ticks
            checkNear(helper, 0.07, value(player, ModStats.MOVEMENT_SPEED), "heavy sword walking speed");
            player.setSprinting(true);
            double sprinting = value(player, ModStats.MOVEMENT_SPEED);
            checkNear(helper, 0.091, sprinting, "heavy sword sprinting speed");
            check(helper, sprinting < ModCombos.RUN_SPEED, "sprinting with the heavy sword is below the run speed");
            player.setSprinting(false);

            double walk = walkSwing(player, cow);
            double run = runSwing(player, cow);
            checkNear(helper, walk, run, "B12: the second swing deals the same damage, no multiplier");
            check(helper, comboOf(recorder) == null, "B12: no combo with the heavy sword, got " + comboOf(recorder));

            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            charge(player);
            checkNear(helper, 0.1, value(player, ModStats.MOVEMENT_SPEED), "empty hand walking speed");
            walkSwing(player, cow);
            checkNear(helper, 1.5, runSwing(player, cow), "empty hand: the same moves are Lunge");
            check(helper, "Lunge".equals(comboOf(recorder)), "empty hand: Lunge, got " + comboOf(recorder));
        }
        helper.succeed();
    }

    /**
     * Q6 A: only a full-strength swing that really hurts its own target counts, and a swing that does not count
     * neither triggers nor replaces the first one.
     * <ul>
     *   <li>B5: a running swing before the attack bar refilled is no combo; the first walk stays.</li>
     *   <li>B8: a running swing cancelled before it lands (like a dodge) is no combo; the first walk stays.</li>
     *   <li>B19: a running swing another mod cancels in {@code LivingHurtEvent} is no combo; the first walk stays.</li>
     *   <li>Then a full running swing that lands: Lunge, so none of the above used up the walk.</li>
     *   <li>B10: a sweep's side target in the same tick is not a swing: the walk after Lunge is a first swing.</li>
     * </ul>
     */
    static void countsOnlyLandedFullSwings(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Cow cow = toughCow(helper);
        Cow beside = toughCow(helper);
        boolean[] cancelAttack = {false};
        boolean[] cancelHurt = {false};
        EventListener attackCanceller = LivingAttackEvent.BUS.addListener(
            (Predicate<LivingAttackEvent>) event -> cancelAttack[0] && event.getEntity() == cow);
        EventListener hurtCanceller = LivingHurtEvent.BUS.addListener(
            (Predicate<LivingHurtEvent>) event -> cancelHurt[0] && event.getEntity() == cow);
        try (Recorder recorder = new Recorder(cow, beside)) {
            walkSwing(player, cow);

            player.setSprinting(true); // the attack bar is empty right after a swing, so this one is weak
            cow.damageCooldownTime = 0;
            double weak = swing(player, cow);
            check(helper, weak > 0 && weak < 1, "B5: a swing before the bar refilled is weak, dealt " + weak);
            check(helper, comboOf(recorder) == null, "B5: a weak swing is no combo, got " + comboOf(recorder));
            recorder.clear();

            cancelAttack[0] = true;
            checkNear(helper, 0, runSwing(player, cow), "B8: the cancelled swing took nothing");
            cancelAttack[0] = false;
            check(helper, recorder.shown.isEmpty(), "B8: nothing landed");

            cancelHurt[0] = true;
            checkNear(helper, 0, runSwing(player, cow), "B19: the swing another mod cancelled took nothing");
            cancelHurt[0] = false;

            checkNear(helper, 1.5, runSwing(player, cow), "the walk was still there: Lunge");
            check(helper, "Lunge".equals(comboOf(recorder)), "Lunge, got " + comboOf(recorder));
            beside.damageCooldownTime = 0;
            beside.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1f);

            checkNear(helper, 1.0, walkSwing(player, cow), "B10: the walk after Lunge is plain");
            check(helper, comboOf(recorder) == null, "B10: the sweep's side target was no running swing, got " + comboOf(recorder));
        } finally {
            LivingAttackEvent.BUS.removeListener(attackCanceller);
            LivingHurtEvent.BUS.removeListener(hurtCanceller);
        }
        helper.succeed();
    }

    /**
     * B15: each player has their own swings. B14: the second swing may hit another target. B11 (O6): after a combo
     * the swing that completed it does not start the next one.
     */
    static void perPlayerAnyTargetNoChain(GameTestHelper helper) {
        ServerPlayer a = helper.makeMockServerPlayerInLevel();
        ServerPlayer b = helper.makeMockServerPlayerInLevel();
        Cow first = toughCow(helper);
        Cow second = toughCow(helper);
        try (Recorder recorder = new Recorder(first, second)) {
            walkSwing(a, first);
            checkNear(helper, 1.0, runSwing(b, first), "B15: B's run is B's first swing, A's walk is not B's");
            check(helper, comboOf(recorder) == null, "B15: no combo for B, got " + comboOf(recorder));

            checkNear(helper, 1.5, runSwing(a, second), "B14: A's run on another cow completes A's walk");
            check(helper, "Lunge".equals(comboOf(recorder)), "B14: Lunge on the other cow, got " + comboOf(recorder));

            checkNear(helper, 1.0, walkSwing(a, second), "B11: no Follow-up right after Lunge");
            check(helper, comboOf(recorder) == null, "B11: no chaining, got " + comboOf(recorder));
        }
        helper.succeed();
    }

    /** B13 (O3): with 100% crit, the walk is a crit (1.5) and the run is crit x Lunge = 2.25, shown as CRIT with Lunge. */
    static void stacksWithCrit(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        setBase(player, ModStats.CRIT_CHANCE, 1.0);
        Cow cow = cow(helper);
        try (Recorder recorder = new Recorder(cow)) {
            checkNear(helper, 1.5, walkSwing(player, cow), "a crit walk");
            checkNear(helper, 2.25, runSwing(player, cow), "B13: 1 x 1.5 (crit) x 1.5 (Lunge)");
            Shown shown = recorder.last();
            check(helper, shown.report().result() == HitResult.CRITICAL, "still a crit, got " + shown.report().result());
            check(helper, "Lunge".equals(shown.report().combo()), "and Lunge, got " + shown.report().combo());
            Sent line = recorder.sentTo(player).getLast();
            checkLine(helper, line, "mobarmory.hit.dealt.crit", List.of("2.3", "6.3", "10"), "B13 action bar");
            check(helper, "Lunge".equals(line.combo()), "B13: CRIT line ends with Lunge, got " + line.combo());
        }
        helper.succeed();
    }

    /**
     * The extensibility proof (19, Q2 changed to JSON): a player drops one more file into
     * {@code config/mobarmory/combos/} and runs {@code /mobarmory reload}; walk then walk, which is no combo in the
     * shipped files (O7), is now "Double Slash" x2. Deleting the file and reloading takes it away again. Runs in a
     * batch of its own and in one tick, so no other test sees the extra combo.
     */
    static void newFileAddsACombo(GameTestHelper helper) {
        ServerPlayer before = helper.makeMockServerPlayerInLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Cow cow = toughCow(helper);
        try (Recorder recorder = new Recorder(cow)) {
            walkSwing(before, cow);
            walkSwing(before, cow);
            check(helper, comboOf(recorder) == null, "O7: walk, walk is no combo in the shipped files");

            COMBOS.folder().write("double_slash", """
                {
                  "format": 1,
                  "name": "Double Slash",
                  "sequence": ["walk", "walk"],
                  "window_ticks": 20,
                  "effect": { "type": "damage_multiplier", "value": 2 }
                }
                """);
            reload(helper);
            check(helper, COMBOS.library().current().all().size() == 3, "three combos after the reload");

            checkNear(helper, 1.0, walkSwing(player, cow), "another player's first walk");
            checkNear(helper, 2.0, walkSwing(player, cow), "the new file's combo: 1.0 x 2");
            check(helper, "Double Slash".equals(comboOf(recorder)), "Double Slash, got " + comboOf(recorder));
            check(helper, "Double Slash".equals(recorder.sentTo(player).getLast().combo()), "shown on the action bar");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            removeExtraCombo(helper);
        }
        check(helper, COMBOS.library().current().all().size() == 2, "back to the two shipped combos");
        helper.succeed();
    }

    private static void removeExtraCombo(GameTestHelper helper) {
        try {
            Files.deleteIfExists(COMBOS.folder().path().resolve("double_slash.json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        reload(helper);
    }

    /** Runs the real command, as an operator would. */
    private static void reload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "mobarmory reload");
    }
}
