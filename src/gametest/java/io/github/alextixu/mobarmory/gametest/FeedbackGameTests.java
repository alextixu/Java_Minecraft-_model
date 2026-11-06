package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.stats;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.feedback.HitReport;
import io.github.alextixu.mobarmory.core.feedback.HitResult;
import io.github.alextixu.mobarmory.core.feedback.Party;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import io.github.alextixu.mobarmory.forge.feedback.ActionBarDisplay;
import io.github.alextixu.mobarmory.forge.feedback.HitDisplay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.listener.EventListener;

/**
 * System tests for D1, the damage display (12-damage-display-given-when-then.md). Every hit goes through Minecraft's
 * real damage pipeline and Forge's real events: real player swings, real armor. Two recorders are plugged into the
 * same pipeline the game uses: one sees each {@link HitReport}, the other is an {@link ActionBarDisplay} whose
 * "send" records the line instead of putting it on a screen (a mock player has no screen).
 * <p>
 * Weapons are simulated by setting attack damage to 1, 3 and 7 (empty hand, dagger, heavy sword). That those items
 * give exactly these numbers is already proven by R2's tests; this way D1 does not depend on {@code ModEquipment},
 * which R2b removes.
 */
final class FeedbackGameTests {
    private static final int TRIES = 60;

    private FeedbackGameTests() {
    }

    static void registerAll() {
        test("hud_weapons_show_different_damage", FeedbackGameTests::weaponsShowDifferentDamage);
        test("hud_crit_and_miss", FeedbackGameTests::critAndMiss);
        test("hud_damage_is_after_armor", FeedbackGameTests::damageIsAfterArmor);
        test("hud_mob_hits_player", FeedbackGameTests::mobHitsPlayer);
        test("hud_creative_player_never_sees_miss", FeedbackGameTests::creativePlayerNeverSeesMiss);
        test("hud_player_hits_player", FeedbackGameTests::playerHitsPlayer);
        test("hud_environment_and_mob_fights_reach_nobody", FeedbackGameTests::environmentAndMobFightsReachNobody);
        test("hud_cooldown_and_stale_crit", FeedbackGameTests::cooldownAndStaleCrit);
        test("hud_reads_damage_after_other_mods", FeedbackGameTests::readsDamageAfterOtherMods);
    }

    // ---------------------------------------------------------------- recorders

    record Shown(HitReport report, LivingEntity defender, LivingEntity attacker) {
    }

    record Sent(ServerPlayer to, Component message) {
        String key() {
            return message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
        }

        /** The arguments after the name: damage, health, max health. */
        List<String> numbers() {
            Object[] args = ((TranslatableContents) message.getContents()).getArgs();
            return List.of(String.valueOf(args[1]), String.valueOf(args[2]), String.valueOf(args[3]));
        }

        String name() {
            Object[] args = ((TranslatableContents) message.getContents()).getArgs();
            return args[0] instanceof Component component ? component.getString() : String.valueOf(args[0]);
        }

        TextColor color() {
            return message.getStyle().getColor();
        }
    }

    /** Watches the hits on or by the given entities, through the same pipeline as the game. Close it when done. */
    private static final class Recorder implements AutoCloseable {
        final List<Shown> shown = new ArrayList<>();
        final List<Sent> sent = new ArrayList<>();
        private final Set<Entity> watched = Collections.newSetFromMap(new IdentityHashMap<>());
        private final HitDisplay reports = this::onShown;
        private final ActionBarDisplay actionBar = new ActionBarDisplay((to, message) -> sent.add(new Sent(to, message)));

        Recorder(Entity... watched) {
            Collections.addAll(this.watched, watched);
            MobArmoryMod.HIT_FEEDBACK.addDisplay(reports);
        }

        private void onShown(HitReport report, LivingEntity defender, LivingEntity attacker) {
            if (watched.contains(defender) || watched.contains(attacker)) {
                shown.add(new Shown(report, defender, attacker));
                actionBar.show(report, defender, attacker);
            }
        }

        Shown last() {
            return shown.isEmpty() ? null : shown.getLast();
        }

        List<Sent> sentTo(ServerPlayer player) {
            return sent.stream().filter(s -> s.to() == player).toList();
        }

        void clear() {
            shown.clear();
            sent.clear();
        }

        @Override
        public void close() {
            MobArmoryMod.HIT_FEEDBACK.removeDisplay(reports);
        }
    }

    // ---------------------------------------------------------------- helpers (same moves as CombatGameTests)

    private static void setBase(LivingEntity entity, StatDefinition stat, double value) {
        entity.getAttribute(stats().holder(stat.id())).setBaseValue(value);
    }

    private static void setAttack(ServerPlayer player, double damage) {
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
    }

    private static Cow cow(GameTestHelper helper) {
        return helper.spawn(EntityTypes.COW, BlockPos.ZERO);
    }

    private static Cow toughCow(GameTestHelper helper) {
        Cow cow = cow(helper);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        cow.setHealth(1000);
        return cow;
    }

    /** A mock player that can be hurt: its client counts as loaded and it is not invulnerable. */
    private static ServerPlayer hurtablePlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.getAbilities().invulnerable = false;
        return player;
    }

    /** Refills the attack bar and stands on the ground, so the swing is full strength but not a jump crit. */
    private static void ready(ServerPlayer player) {
        for (int i = 0; i < 10; i++) {
            player.doTick();
        }
        player.setOnGround(true);
        player.fallDistance = 0.0;
        player.setSprinting(false);
    }

    /** One real swing; returns the health the target lost. */
    private static double swing(ServerPlayer player, LivingEntity target) {
        float before = target.getHealth();
        player.attack(target);
        return before - target.getHealth();
    }

    private static boolean hit(GameTestHelper helper, LivingEntity target, DamageSource source, float damage) {
        target.damageCooldownTime = 0;
        return target.hurtServer(helper.getLevel(), source, damage);
    }

    private static void checkColor(GameTestHelper helper, Sent sent, ChatFormatting expected) {
        check(helper, TextColor.fromLegacyFormat(expected).equals(sent.color()),
            sent.key() + " should be " + expected + " but was " + sent.color());
    }

    private static void checkLine(GameTestHelper helper, Sent sent, String key, List<String> numbers, String what) {
        check(helper, key.equals(sent.key()), what + ": key " + sent.key() + ", expected " + key);
        check(helper, numbers.equals(sent.numbers()), what + ": numbers " + sent.numbers() + ", expected " + numbers);
    }

    // ---------------------------------------------------------------- tests

    /**
     * H1, H2, H3, H10: attack damage 1, 3, 7 and 5 (empty hand, dagger, heavy sword, a value changed on the web
     * page) against fresh cows (10 health, no armor) give 1.0, 3.0, 7.0 and 5.0 on the attacker's action bar.
     */
    static void weaponsShowDifferentDamage(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String[][] expected = {{"1", "1.0", "9.0"}, {"3", "3.0", "7.0"}, {"7", "7.0", "3.0"}, {"5", "5.0", "5.0"}};
        for (String[] row : expected) {
            Cow cow = cow(helper);
            try (Recorder recorder = new Recorder(cow)) {
                setAttack(player, Double.parseDouble(row[0]));
                ready(player);
                double lost = swing(player, cow);

                Shown shown = recorder.last();
                check(helper, shown != null, "attack " + row[0] + ": no report");
                checkNear(helper, Double.parseDouble(row[0]), shown.report().damage(), "attack " + row[0] + " damage");
                checkNear(helper, lost, shown.report().damage(), "attack " + row[0] + ": report = health the cow lost");
                check(helper, shown.report().result() == HitResult.HIT, "attack " + row[0] + " should be a plain hit");

                List<Sent> lines = recorder.sentTo(player);
                check(helper, lines.size() == 1, "attack " + row[0] + ": expected one line, got " + lines.size());
                checkLine(helper, lines.getFirst(), "mobarmory.hit.dealt", List.of(row[1], row[2], "10"), "attack " + row[0]);
                check(helper, lines.getFirst().name().equals(cow.getDisplayName().getString()), "the line names the cow");
                checkColor(helper, lines.getFirst(), ChatFormatting.WHITE);
            }
        }
        helper.succeed();
    }

    /**
     * H4: attack 3 with 100% crit chance gives a gold "CRIT" line of 4.5. O10: a vanilla jump crit looks the same.
     * H5 + B8: against a cow with dodge at its cap, a crit that gets dodged shows MISS (gray), never CRIT, and the
     * cow keeps its health.
     */
    static void critAndMiss(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        setAttack(player, 3);

        Cow cow = cow(helper);
        try (Recorder recorder = new Recorder(cow)) {
            setBase(player, ModStats.CRIT_CHANCE, 1.0);
            ready(player);
            swing(player, cow);
            check(helper, recorder.last().report().result() == HitResult.CRITICAL, "H4: 100% crit should report CRITICAL");
            Sent line = recorder.sentTo(player).getLast();
            checkLine(helper, line, "mobarmory.hit.dealt.crit", List.of("4.5", "5.5", "10"), "H4");
            checkColor(helper, line, ChatFormatting.GOLD);
        }

        Cow jumped = cow(helper);
        try (Recorder recorder = new Recorder(jumped)) {
            setBase(player, ModStats.CRIT_CHANCE, 0.0);
            ready(player);
            player.setOnGround(false);
            player.fallDistance = 1.0;
            swing(player, jumped);
            check(helper, recorder.last().report().result() == HitResult.CRITICAL, "O10: a jump crit is shown as CRITICAL too");
            checkLine(helper, recorder.sentTo(player).getLast(), "mobarmory.hit.dealt.crit", List.of("4.5", "5.5", "10"), "O10");
        }

        Cow dodger = toughCow(helper);
        setBase(dodger, ModStats.DODGE_CHANCE, 0.75);
        setBase(player, ModStats.CRIT_CHANCE, 1.0);
        try (Recorder recorder = new Recorder(dodger)) {
            boolean sawMiss = false;
            for (int i = 0; i < TRIES && !sawMiss; i++) {
                recorder.clear();
                ready(player);
                dodger.damageCooldownTime = 0;
                double lost = swing(player, dodger);
                Shown shown = recorder.last();
                check(helper, shown != null, "swing " + i + ": every swing at a cow that can be hurt is reported");
                if (shown.report().result() == HitResult.DODGED) {
                    sawMiss = true;
                    checkNear(helper, 0, lost, "B8: a dodged swing takes no health");
                    Sent line = recorder.sentTo(player).getLast();
                    check(helper, line.key().equals("mobarmory.hit.dealt.miss"), "B8: MISS, not CRIT, got " + line.key());
                    checkColor(helper, line, ChatFormatting.GRAY);
                } else {
                    check(helper, shown.report().result() == HitResult.CRITICAL, "a swing that lands at 100% crit is CRITICAL");
                }
            }
            check(helper, sawMiss, "with 75% dodge, " + TRIES + " swings should include a MISS");
        }
        helper.succeed();
    }

    /** H6: attack 7 against a zombie with 10 armor deals 7 x 0.74 = 5.18 after armor: "Zombie -5.2 ❤ 14.8/20". */
    static void damageIsAfterArmor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        setAttack(player, 7);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(10);
        try (Recorder recorder = new Recorder(zombie)) {
            ready(player);
            double lost = swing(player, zombie);

            double reported = recorder.last().report().damage();
            checkNear(helper, lost, reported, "H6: report = health the zombie lost");
            check(helper, Math.abs(reported - 5.18) < 1e-4, "H6: 7 x (1 - 6.5 / 25) = 5.18, got " + reported);
            checkLine(helper, recorder.sentTo(player).getLast(), "mobarmory.hit.dealt", List.of("5.2", "14.8", "20"), "H6");
        }
        helper.succeed();
    }

    /**
     * H8: a zombie hits a player; the player gets a red "Zombie hit you" line with the health they really lost.
     * H7: when the player dodges, a gray "Dodged Zombie" line. B7: a hit fully soaked up by absorption shows nothing.
     * The zombie is not a player, so nobody else gets a line.
     */
    static void mobHitsPlayer(GameTestHelper helper) {
        ServerPlayer player = hurtablePlayer(helper);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        DamageSource bite = helper.getLevel().damageSources().mobAttack(zombie);
        try (Recorder recorder = new Recorder(player)) {
            float before = player.getHealth();
            check(helper, hit(helper, player, bite, 4f), "H8: the zombie's hit should land");
            double lost = before - player.getHealth();
            Shown shown = recorder.last();
            check(helper, shown != null && shown.report().attacker() == Party.MOB, "H8: hit reported with a mob attacker");
            checkNear(helper, lost, shown.report().damage(), "H8: report = health the player lost");
            check(helper, recorder.sent.size() == 1, "H8: only the player gets a line, got " + recorder.sent.size());
            Sent line = recorder.sentTo(player).getFirst();
            check(helper, line.key().equals("mobarmory.hit.taken"), "H8: key " + line.key());
            check(helper, line.name().equals(zombie.getDisplayName().getString()), "H8: the line names the zombie");
            checkColor(helper, line, ChatFormatting.RED);

            recorder.clear();
            player.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100);
            player.setAbsorptionAmount(100);
            before = player.getHealth();
            hit(helper, player, bite, 4f);
            checkNear(helper, 0, before - player.getHealth(), "B7: absorption took the whole hit");
            check(helper, recorder.shown.isEmpty() && recorder.sent.isEmpty(), "B7: nothing is shown when no health was lost");
            player.setAbsorptionAmount(0);

            setBase(player, ModStats.DODGE_CHANCE, 0.75);
            boolean sawDodge = false;
            for (int i = 0; i < TRIES && !sawDodge; i++) {
                recorder.clear();
                player.setHealth(player.getMaxHealth());
                if (!hit(helper, player, bite, 1f)) {
                    sawDodge = true;
                    check(helper, recorder.last() != null && recorder.last().report().result() == HitResult.DODGED, "H7: dodge reported");
                    Sent dodged = recorder.sentTo(player).getLast();
                    check(helper, dodged.key().equals("mobarmory.hit.taken.miss"), "H7: key " + dodged.key());
                    checkColor(helper, dodged, ChatFormatting.GRAY);
                }
            }
            check(helper, sawDodge, "with 75% dodge, " + TRIES + " zombie hits should include a dodge");
        }
        helper.succeed();
    }

    /** B3 (O5): a creative player can never be hurt, so even a successful dodge roll shows nothing. */
    static void creativePlayerNeverSeesMiss(GameTestHelper helper) {
        ServerPlayer player = hurtablePlayer(helper);
        player.getAbilities().invulnerable = true;
        setBase(player, ModStats.DODGE_CHANCE, 0.75);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        DamageSource bite = helper.getLevel().damageSources().mobAttack(zombie);
        try (Recorder recorder = new Recorder(player)) {
            for (int i = 0; i < TRIES; i++) {
                hit(helper, player, bite, 4f);
            }
            checkNear(helper, player.getMaxHealth(), player.getHealth(), "creative player took no damage");
            check(helper, recorder.shown.isEmpty() && recorder.sent.isEmpty(),
                "B3: nothing shown to a creative player, got " + recorder.shown.size() + " reports");
        }
        helper.succeed();
    }

    /** B15 + B14: player A hits player B; A and B each get their own line with the same number, bystander C gets none. */
    static void playerHitsPlayer(GameTestHelper helper) {
        ServerPlayer a = helper.makeMockServerPlayerInLevel();
        ServerPlayer b = hurtablePlayer(helper);
        ServerPlayer c = helper.makeMockServerPlayerInLevel();
        try (Recorder recorder = new Recorder(a, b, c)) {
            ready(a);
            double lost = swing(a, b);
            check(helper, lost > 0, "B15: A's swing should hurt B (PvP on), lost " + lost);

            List<Sent> toA = recorder.sentTo(a);
            List<Sent> toB = recorder.sentTo(b);
            check(helper, toA.size() == 1 && toB.size() == 1, "B15: A and B get one line each, got " + toA.size() + " and " + toB.size());
            checkLine(helper, toA.getFirst(), "mobarmory.hit.dealt", List.of("1.0", "19.0", "20"), "B15 attacker");
            checkLine(helper, toB.getFirst(), "mobarmory.hit.taken", List.of("1.0", "19.0", "20"), "B15 defender");
            check(helper, recorder.sentTo(c).isEmpty(), "B14: a bystander gets nothing");
        }
        helper.succeed();
    }

    /**
     * B5: falling and generic damage are never shown. B6: a zombie hitting a cow is a valid hit, but no player takes
     * part, so nobody gets a line.
     */
    static void environmentAndMobFightsReachNobody(GameTestHelper helper) {
        Cow cow = toughCow(helper);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        var sources = helper.getLevel().damageSources();
        try (Recorder recorder = new Recorder(cow)) {
            check(helper, hit(helper, cow, sources.fall(), 2f), "fall damage lands");
            check(helper, hit(helper, cow, sources.generic(), 2f), "generic damage lands");
            check(helper, recorder.shown.isEmpty(), "B5: environment damage is not shown, got " + recorder.shown.size());

            check(helper, hit(helper, cow, sources.mobAttack(zombie), 2f), "the zombie's hit lands");
            check(helper, recorder.shown.size() == 1 && recorder.last().report().attacker() == Party.MOB,
                "B6: the zombie's hit is reported");
            check(helper, recorder.sent.isEmpty(), "B6: no player takes part, so nobody gets a line");
        }
        helper.succeed();
    }

    /**
     * B1, B2 (O4): inside the damage cooldown, a weaker hit takes nothing and shows nothing; a stronger one takes
     * only the difference and shows it (1 then 7 shows 6.0).
     * B9: a crit swallowed by the cooldown must not mark the next hit as CRIT.
     */
    static void cooldownAndStaleCrit(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Cow cow = toughCow(helper);
        try (Recorder recorder = new Recorder(cow)) {
            setAttack(player, 1);
            ready(player);
            cow.damageCooldownTime = 0;
            swing(player, cow);
            checkNear(helper, 1.0, recorder.last().report().damage(), "first hit");

            recorder.clear();
            setAttack(player, 7);
            ready(player); // the cow does not tick here, so it is still in its cooldown
            swing(player, cow);
            checkNear(helper, 6.0, recorder.last().report().damage(), "B2: only the difference 7 - 1");
            checkLine(helper, recorder.sentTo(player).getLast(), "mobarmory.hit.dealt", List.of("6.0", "993.0", "1000"), "B2");

            recorder.clear();
            setAttack(player, 1);
            ready(player);
            double lost = swing(player, cow);
            checkNear(helper, 0, lost, "B1: a weaker hit inside the cooldown takes nothing");
            check(helper, recorder.shown.isEmpty() && recorder.sent.isEmpty(), "B1: and shows nothing");

            setBase(player, ModStats.CRIT_CHANCE, 1.0);
            ready(player);
            lost = swing(player, cow); // a crit (1.5), still weaker than the 7 inside the cooldown
            checkNear(helper, 0, lost, "B9: the crit was swallowed by the cooldown");
            check(helper, recorder.shown.isEmpty(), "B9: the swallowed crit shows nothing");

            setBase(player, ModStats.CRIT_CHANCE, 0.0);
            ready(player);
            cow.damageCooldownTime = 0;
            swing(player, cow);
            check(helper, recorder.last() != null && recorder.last().report().result() == HitResult.HIT,
                "B9: the next hit is not marked CRIT");
        }
        helper.succeed();
    }

    /** B16: another mod halves the damage in LivingDamageEvent; the display shows the halved value the cow lost. */
    static void readsDamageAfterOtherMods(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        setAttack(player, 7);
        Cow cow = cow(helper);
        EventListener halving = LivingDamageEvent.BUS.addListener((Consumer<LivingDamageEvent>) event -> {
            if (event.getEntity() == cow) {
                event.setAmount(event.getAmount() / 2);
            }
        });
        try (Recorder recorder = new Recorder(cow)) {
            ready(player);
            double lost = swing(player, cow);
            checkNear(helper, 3.5, lost, "the other mod halved 7");
            checkNear(helper, 3.5, recorder.last().report().damage(), "B16: the report has the halved damage");
            checkLine(helper, recorder.sentTo(player).getLast(), "mobarmory.hit.dealt", List.of("3.5", "6.5", "10"), "B16");
        } finally {
            LivingDamageEvent.BUS.removeListener(halving);
        }
        helper.succeed();
    }
}
