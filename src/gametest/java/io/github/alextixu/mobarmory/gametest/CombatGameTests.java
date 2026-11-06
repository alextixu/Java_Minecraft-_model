package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.stats;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.server.level.ServerPlayer;

/**
 * System tests for crit and dodge going through Minecraft's real damage pipeline and Forge's events.
 * A cow is the target because it has no armor, so damage numbers stay exact.
 */
final class CombatGameTests {
    private static final int HITS = 200;

    private CombatGameTests() {
    }

    static void registerAll() {
        test("dodge_avoids_some_mob_hits", CombatGameTests::dodgeAvoidsSomeMobHits);
        test("environment_damage_is_never_dodged", CombatGameTests::environmentDamageIsNeverDodged);
        test("mob_crit_multiplies_damage", CombatGameTests::mobCritMultipliesDamage);
        test("player_crit_uses_one_multiplier", CombatGameTests::playerCritUsesOneMultiplier);
        test("player_crit_needs_full_charge", CombatGameTests::playerCritNeedsFullCharge);
    }

    private static void setBase(LivingEntity entity, StatDefinition stat, double value) {
        entity.getAttribute(stats().holder(stat.id())).setBaseValue(value);
    }

    private static Cow toughCow(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityTypes.COW, BlockPos.ZERO);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        cow.setHealth(1000);
        return cow;
    }

    /** Hits the target once, ignoring the one-second damage cooldown after a hit. True if it landed. */
    private static boolean hit(GameTestHelper helper, LivingEntity target, DamageSource source, float damage) {
        target.damageCooldownTime = 0;
        return target.hurtServer(helper.getLevel(), source, damage);
    }

    /** H6 in game: with dodge at its 75% cap, some zombie hits are avoided and some land; with 0, all land. */
    static void dodgeAvoidsSomeMobHits(GameTestHelper helper) {
        Cow cow = toughCow(helper);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        DamageSource bite = helper.getLevel().damageSources().mobAttack(zombie);

        for (int i = 0; i < 50; i++) {
            check(helper, hit(helper, cow, bite, 1f), "with 0 dodge, hit " + i + " should land");
        }

        setBase(cow, ModStats.DODGE_CHANCE, 0.75);
        int dodged = 0;
        for (int i = 0; i < HITS; i++) {
            if (!hit(helper, cow, bite, 1f)) {
                dodged++;
            }
        }
        // Expected 150 of 200; the bounds are more than 7 standard deviations away.
        check(helper, dodged > 100 && dodged < 190, "with 75% dodge, dodged " + dodged + " of " + HITS);
        helper.succeed();
    }

    /** B8 in game: falling and generic damage are never dodged, even at 75%. */
    static void environmentDamageIsNeverDodged(GameTestHelper helper) {
        Cow cow = toughCow(helper);
        setBase(cow, ModStats.DODGE_CHANCE, 0.75);
        var sources = helper.getLevel().damageSources();
        for (int i = 0; i < 50; i++) {
            check(helper, hit(helper, cow, sources.fall(), 1f), "fall damage " + i + " was dodged");
            check(helper, hit(helper, cow, sources.generic(), 1f), "generic damage " + i + " was dodged");
        }
        helper.succeed();
    }

    /** H5 in game, mob attacker: crit 0 deals 4, crit 100% deals 4 x 1.5 = 6. */
    static void mobCritMultipliesDamage(GameTestHelper helper) {
        Cow cow = toughCow(helper);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        DamageSource bite = helper.getLevel().damageSources().mobAttack(zombie);

        float before = cow.getHealth();
        hit(helper, cow, bite, 4f);
        checkNear(helper, 4.0, before - cow.getHealth(), "damage with 0 crit");

        setBase(zombie, ModStats.CRIT_CHANCE, 1.0);
        before = cow.getHealth();
        hit(helper, cow, bite, 4f);
        checkNear(helper, 6.0, before - cow.getHealth(), "damage with 100% crit");
        helper.succeed();
    }

    /** Lets the player's attack bar refill completely (5 ticks for an empty hand; 10 to be safe). */
    private static void charge(ServerPlayer player) {
        for (int i = 0; i < 10; i++) {
            player.doTick();
        }
    }

    /** Puts the player mid-fall (a jump attack) or on the ground. Vanilla only jump-crits while falling. */
    private static void airborne(ServerPlayer player, boolean falling) {
        player.setOnGround(!falling);
        player.fallDistance = falling ? 1.0 : 0.0;
        player.setSprinting(false);
    }

    /** One real swing through Player.attack, returning the damage the cow took. */
    private static double swing(ServerPlayer player, Cow cow) {
        cow.damageCooldownTime = 0;
        float before = cow.getHealth();
        player.attack(cow);
        return before - cow.getHealth();
    }

    /**
     * H5 + B9 in game, with real player swings (empty hand, attack damage 1.0): a jump attack alone, a 100% crit
     * alone, and both together all deal exactly 1.5. The two crits share one multiplier and never make 2.25.
     */
    static void playerCritUsesOneMultiplier(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Cow cow = toughCow(helper);

        charge(player);
        airborne(player, false);
        checkNear(helper, 1.0, swing(player, cow), "0 crit, no jump: plain hit");

        charge(player);
        airborne(player, true);
        checkNear(helper, 1.5, swing(player, cow), "jump attack alone");

        setBase(player, ModStats.CRIT_CHANCE, 1.0);
        charge(player);
        airborne(player, false);
        checkNear(helper, 1.5, swing(player, cow), "H5: 100% crit without jumping");

        charge(player);
        airborne(player, true);
        checkNear(helper, 1.5, swing(player, cow), "B9: 100% crit plus jump must not stack to 2.25");
        helper.succeed();
    }

    /**
     * N2: a swing that is not fully charged never rolls a crit, even at 100%. Right after a swing the bar is
     * empty, so the next hit is weak: 1.0 x (0.2 + 0.1^2 x 0.8) = 0.208, and a crit would have made it 0.312.
     */
    static void playerCritNeedsFullCharge(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Cow cow = toughCow(helper);
        setBase(player, ModStats.CRIT_CHANCE, 1.0);
        airborne(player, false);

        charge(player);
        checkNear(helper, 1.5, swing(player, cow), "charged swing crits");

        double weak = swing(player, cow); // no ticks in between: the bar is empty
        check(helper, weak < 0.25, "N2: weak swing must not crit (0.208 expected, a crit would be 0.312), got " + weak);
        check(helper, weak > 0.15, "N2: weak swing should still deal its reduced damage, got " + weak);
        helper.succeed();
    }
}
