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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.CriticalHitEvent;

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

    /** H5 + B9 in game, player attacker: our crit and a vanilla jump crit share one 1.5x multiplier. */
    static void playerCritUsesOneMultiplier(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Cow cow = toughCow(helper);

        check(helper, ForgeHooks.getCriticalHit(player, cow, false, 1.0f) == null, "0 crit, no jump: should not crit");
        CriticalHitEvent jumpOnly = ForgeHooks.getCriticalHit(player, cow, true, 1.5f);
        check(helper, jumpOnly != null, "jump attack alone should still crit");
        checkNear(helper, 1.5, jumpOnly.getDamageModifier(), "jump attack multiplier");

        setBase(player, ModStats.CRIT_CHANCE, 1.0);
        CriticalHitEvent rolled = ForgeHooks.getCriticalHit(player, cow, false, 1.0f);
        check(helper, rolled != null, "100% crit should crit without jumping");
        checkNear(helper, 1.5, rolled.getDamageModifier(), "H5: crit multiplier");

        CriticalHitEvent both = ForgeHooks.getCriticalHit(player, cow, true, 1.5f);
        check(helper, both != null, "100% crit plus jump should crit");
        checkNear(helper, 1.5, both.getDamageModifier(), "B9: crit plus jump must not stack to 2.25");
        helper.succeed();
    }
}
