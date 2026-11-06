package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.stats;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import com.mojang.authlib.GameProfile;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.LoadoutCalculator;
import io.github.alextixu.mobarmory.core.mob.AttackPlayersBehavior;
import io.github.alextixu.mobarmory.core.mob.Behavior;
import io.github.alextixu.mobarmory.core.mob.LeapBehavior;
import io.github.alextixu.mobarmory.core.mob.NeutralBehavior;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.WanderBehavior;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import io.github.alextixu.mobarmory.forge.ArmoryMob;
import io.github.alextixu.mobarmory.forge.BehaviorGoals;
import io.github.alextixu.mobarmory.forge.EntityStatReader;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import io.github.alextixu.mobarmory.forge.MobEntityFactory;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * System tests for R4 custom mobs (T2), in a real server: real entity types, attributes, AI goals, spawn eggs and
 * {@code /summon}.
 * <p>
 * GameTest walls every test in with barriers around its structure, so these tests use {@code mobarmory:arena}, an empty
 * 24 x 4 x 24 structure: room for a 1.2x brute to stand and walk, and neighbouring tests more than 16 blocks away.
 * Tests that add a real player run in their own test environment (a batch of their own, see {@code test_environment/})
 * and remove the player when they pass, so no other mob ever sees it.
 */
final class MobGameTests {
    private static final MobEntityFactory MOBS = MobArmoryMod.MOBS;
    /** The middle of the arena. */
    private static final BlockPos HERE = new BlockPos(12, 0, 12);

    private MobGameTests() {
    }

    static void registerAll() {
        test("t2_every_mob_spawns_as_defined", MobGameTests::t2EveryMobSpawnsAsDefined);
        test("h7_custom_mobs_have_every_stat", MobGameTests::h7CustomMobsHaveEveryStat);
        test("h1_h2_prototype_mob_stats", MobGameTests::h1h2PrototypeMobStats);
        test("brute_heavy_sword_slows_it", MobGameTests::bruteHeavySwordSlowsIt);
        test("mob_spawns_from_egg_and_summon", MobGameTests::mobSpawnsFromEggAndSummon);
        test("mob_attacks_survival_player", MobGameTests::mobAttacksSurvivalPlayer);
        test("mob_ignores_creative_player_and_animals", MobGameTests::mobIgnoresCreativePlayerAndAnimals);
        test("mob_retaliates_against_attacker", MobGameTests::mobRetaliatesAgainstAttacker);
        test("mob_gives_up_beyond_16_blocks", MobGameTests::mobGivesUpBeyond16Blocks);
        test("mob_wanders_without_players", MobGameTests::mobWandersWithoutPlayers);
        test("mob_reload_keeps_stats_once", MobGameTests::mobReloadKeepsStatsOnce);
        test("mob_drops_no_equipment_and_5_xp", MobGameTests::mobDropsNoEquipmentAnd5Xp);
        test("mob_does_not_burn_in_daylight", MobGameTests::mobDoesNotBurnInDaylight);
        test("mob_removed_on_peaceful", MobGameTests::mobRemovedOnPeaceful);
        test("mob_behaviors_all_have_binders", MobGameTests::mobBehaviorsAllHaveBinders);
        test("skirmisher_dodges_and_crits", MobGameTests::skirmisherDodgesAndCrits);
        MobWeaponGameTests.registerAll();
        test("neutral_mob_waits_until_hit", MobGameTests::neutralMobWaitsUntilHit);
        MobAdminGameTests.registerAll();
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static EntityType<ArmoryMob> type(MobDefinition mob) {
        return MOBS.type(mob.id());
    }

    /** Spawns like a spawn egg would: through finalizeSpawn, which puts on the definition's equipment. */
    private static ArmoryMob spawn(GameTestHelper helper, MobDefinition mob, BlockPos pos) {
        return helper.spawn(type(mob), pos, EntitySpawnReason.SPAWN_ITEM_USE);
    }

    private static double value(LivingEntity entity, StatDefinition stat) {
        return entity.getAttributeValue(stats().holder(stat.id()));
    }

    private static double base(LivingEntity entity, StatDefinition stat) {
        return entity.getAttributeBaseValue(stats().holder(stat.id()));
    }

    private static Item equipment(String id) {
        return MobArmoryMod.EQUIPMENT.item(EquipmentId.of(id));
    }

    /**
     * Every stat the definition says the mob should end up with: its own value, then its equipment through R2, using
     * the weapon definitions in effect now (they can change while the game runs, R2b).
     */
    private static Map<StatId, Double> expectedStats(MobDefinition mob) {
        Map<StatId, Double> base = mob.spawnStats(ModStats.CATALOG);
        Map<StatId, Double> expected = new LinkedHashMap<>(base);
        expected.putAll(new LoadoutCalculator(MobArmoryMod.EQUIPMENT_LIBRARY.current()).compute(mob.equipment(), base::get));
        return expected;
    }

    /**
     * A real player in the level, like {@code makeMockServerPlayerInLevel} but in the given game mode and standing
     * at the given spot. The test world's default mode is creative, so the mode is really set (abilities included),
     * not just reported. Remove it with {@link #remove} when the test passes, so later tests never see it.
     */
    private static ServerPlayer playerInLevel(GameTestHelper helper, GameType mode, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket()); // players can't be hurt until loaded
        player.setGameMode(mode);
        moveTo(helper, player, relative);
        return player;
    }

    private static void moveTo(GameTestHelper helper, Entity entity, BlockPos relative) {
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        entity.snapTo(pos.x, pos.y, pos.z, 0, 0);
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** Mobs of a type within half a block of a spot, right after spawning them there. */
    private static <T extends Entity> List<T> entitiesAt(GameTestHelper helper, EntityType<T> type, BlockPos relative) {
        Vec3 center = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        return helper.getLevel().getEntities(type, AABB.ofSize(center.add(0, 1, 0), 1, 2, 1), entity -> true);
    }

    // ---- T2 / H: a mob is one definition, and it is complete -------------------------------------------------

    /**
     * T2 (conditions 2-5, 8): every mob in the catalog, whatever is in it, has a spawn egg for its type and spawns with
     * exactly the stats its definition and equipment give, at full health, holding its equipment, with goals for its
     * behaviors. A new mob is covered as soon as it is defined; this test does not change.
     */
    static void t2EveryMobSpawnsAsDefined(GameTestHelper helper) {
        List<ArmoryMob> mobs = new ArrayList<>();
        for (MobDefinition definition : MOBS.catalog().all()) {
            check(helper, SpawnEggItem.getType(new ItemStack(MOBS.spawnEgg(definition.id()))) == type(definition),
                definition.id() + "'s spawn egg should spawn a " + definition.id());
            mobs.add(spawn(helper, definition, HERE));
        }
        helper.succeedWhen(() -> {
            for (ArmoryMob mob : mobs) {
                MobDefinition definition = mob.definition();
                String name = definition.id().value();
                expectedStats(definition).forEach((stat, expected) -> checkNear(helper, expected,
                    mob.getAttributeValue(stats().holder(stat)), name + " " + stat));
                checkNear(helper, mob.getMaxHealth(), mob.getHealth(), name + " spawns at full health");
                definition.equipment().forEach((slot, id) -> check(helper,
                    mob.getItemBySlot(EquipmentSlot.valueOf(slot.name())).is(equipment(id.value())), name + " should hold " + id));
                check(helper, mob.goalSelector.getAvailableGoals().isEmpty() == definition.behaviors().isEmpty(),
                    name + ": goals " + mob.goalSelector.getAvailableGoals().size() + " for behaviors " + definition.behaviors());
            }
        });
    }

    /**
     * H7: a vanilla zombie and every custom mob are in the same stat system: both carry every custom stat as an
     * attribute, and every stat reads (as {@code /mobarmory stats} does). Custom mobs also carry every vanilla-backed
     * stat themselves; a zombie has no attack speed attribute of its own and reads the catalog default, as in R1.
     */
    static void h7CustomMobsHaveEveryStat(GameTestHelper helper) {
        List<LivingEntity> entities = new ArrayList<>();
        entities.add(helper.spawn(EntityTypes.ZOMBIE, HERE));
        MOBS.catalog().all().forEach(definition -> entities.add(spawn(helper, definition, HERE)));
        for (LivingEntity entity : entities) {
            EntityStatReader reader = new EntityStatReader(entity, stats());
            for (StatDefinition stat : ModStats.CATALOG.all()) {
                String what = entity.getType().toShortString() + " " + stat.id();
                boolean mustHaveAttribute = stat.isCustom() || entity instanceof ArmoryMob;
                check(helper, !mustHaveAttribute || entity.getAttribute(stats().holder(stat.id())) != null, what + " is missing");
                check(helper, Double.isFinite(reader.get(stat.id())), what + " does not read");
            }
        }
        helper.succeed();
    }

    /** H1 + H2 with 06's numbers: brute 40 hp, attack 4 + 6 = 10, speed 0.25 x 0.7 = 0.175; skirmisher with dagger. */
    static void h1h2PrototypeMobStats(GameTestHelper helper) {
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        ArmoryMob skirmisher = spawn(helper, Mobs.skirmisher(), HERE);
        helper.succeedWhen(() -> {
            checkNear(helper, 40, value(brute, ModStats.MAX_HEALTH), "H1: brute max health");
            checkNear(helper, 40, brute.getHealth(), "H1: brute health");
            checkNear(helper, 1.2, brute.getScale(), "brute is 1.2x the size");
            checkNear(helper, 10, value(brute, ModStats.ATTACK_DAMAGE), "H2: brute attack damage");
            checkNear(helper, 0.175, value(brute, ModStats.MOVEMENT_SPEED), "H2: brute speed");
            checkNear(helper, 2.4, value(brute, ModStats.ATTACK_SPEED), "brute attack speed (N3)");
            checkNear(helper, 0, value(brute, ModStats.CRIT_CHANCE), "H1/B7: brute crit chance (default)");
            checkNear(helper, 0, value(brute, ModStats.DODGE_CHANCE), "H1/B7: brute dodge chance (default)");

            checkNear(helper, 16, value(skirmisher, ModStats.MAX_HEALTH), "skirmisher max health");
            checkNear(helper, 4, value(skirmisher, ModStats.ATTACK_DAMAGE), "skirmisher attack damage");
            checkNear(helper, 0.5, value(skirmisher, ModStats.CRIT_CHANCE), "skirmisher crit chance");
            checkNear(helper, 0.30, value(skirmisher, ModStats.DODGE_CHANCE), "skirmisher dodge chance");
            checkNear(helper, 0.352, value(skirmisher, ModStats.MOVEMENT_SPEED), "skirmisher speed");
        });
    }

    /** "Holding the heavy sword makes it slower": 0.175 with the sword, back to its own 0.25 without it. */
    static void bruteHeavySwordSlowsIt(GameTestHelper helper) {
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        helper.startSequence()
            .thenWaitUntil(() -> {
                checkNear(helper, 0.25, base(brute, ModStats.MOVEMENT_SPEED), "brute's own speed");
                checkNear(helper, 0.175, value(brute, ModStats.MOVEMENT_SPEED), "brute speed with the heavy sword");
            })
            .thenExecute(() -> brute.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY))
            .thenWaitUntil(() -> checkNear(helper, 0.25, value(brute, ModStats.MOVEMENT_SPEED), "brute speed without the sword"))
            .thenSucceed();
    }

    /**
     * Condition 8: a spawn egg and /summon both make a complete mob (with its equipment). The egg is used by a creative
     * player standing next to the spot, as in real play: mobs from eggs and /summon are not persistent, and Minecraft
     * removes such a monster at once when no player is within 128 blocks. Own environment, because of the player.
     */
    static void mobSpawnsFromEggAndSummon(GameTestHelper helper) {
        ServerPlayer user = playerInLevel(helper, GameType.CREATIVE, HERE.north(3));
        user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MOBS.spawnEgg(Mobs.brute().id())));
        helper.useBlock(HERE, user);
        List<ArmoryMob> fromEgg = entitiesAt(helper, type(Mobs.brute()), HERE);
        check(helper, fromEgg.size() == 1, "the brute spawn egg should spawn one brute, got " + fromEgg.size());

        BlockPos summonAt = HERE.east(2);
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(summonAt));
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
            String.format(Locale.ROOT, "summon mobarmory:skirmisher %.2f %.2f %.2f", at.x, at.y, at.z));
        List<ArmoryMob> summoned = entitiesAt(helper, type(Mobs.skirmisher()), summonAt);
        check(helper, summoned.size() == 1, "/summon mobarmory:skirmisher should make one skirmisher, got " + summoned.size());

        ArmoryMob brute = fromEgg.getFirst();
        ArmoryMob skirmisher = summoned.getFirst();
        helper.succeedWhen(() -> {
            check(helper, !brute.isRemoved() && !skirmisher.isRemoved(), "the spawned mobs should stay in the world");
            check(helper, brute.getMainHandItem().is(equipment("heavy_sword")), "egg brute should hold the heavy sword");
            checkNear(helper, 0.175, value(brute, ModStats.MOVEMENT_SPEED), "egg brute speed");
            check(helper, skirmisher.getMainHandItem().is(equipment("dagger")), "summoned skirmisher should hold the dagger");
            checkNear(helper, 4, value(skirmisher, ModStats.ATTACK_DAMAGE), "summoned skirmisher attack");
            remove(helper, user);
        });
    }

    // ---- behaviors -----------------------------------------------------------------------------------------------

    /** H3: a survival player 3 blocks away is targeted, chased and hit. Own environment: no other test sees the player. */
    static void mobAttacksSurvivalPlayer(GameTestHelper helper) {
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        ServerPlayer player = playerInLevel(helper, GameType.SURVIVAL, HERE.east(3));
        helper.succeedWhen(() -> {
            check(helper, brute.getTarget() == player, "H3: brute should target the survival player, target " + brute.getTarget());
            check(helper, player.getHealth() < player.getMaxHealth(), "H3: the player should have been hit, health " + player.getHealth());
            remove(helper, player);
        });
    }

    /** B8 + Q7: with a creative player and a cow right next to it, the brute targets no one and hurts no one. */
    static void mobIgnoresCreativePlayerAndAnimals(GameTestHelper helper) {
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        ServerPlayer player = playerInLevel(helper, GameType.CREATIVE, HERE.east(2));
        Cow cow = helper.spawn(EntityTypes.COW, HERE.west(2));
        helper.runAfterDelay(100, () -> {
            check(helper, brute.getTarget() == null, "B8: brute should not target anyone, target " + brute.getTarget());
            checkNear(helper, player.getMaxHealth(), player.getHealth(), "B8: creative player health");
            checkNear(helper, cow.getMaxHealth(), cow.getHealth(), "Q7: cow health (no attacks on other mobs)");
            remove(helper, player);
            helper.succeed();
        });
    }

    /**
     * B9: a cow that hurts the brute becomes its target. The hit comes a few ticks after spawning: Minecraft's
     * retaliation goal ignores a hit taken on the mob's very first tick.
     */
    static void mobRetaliatesAgainstAttacker(GameTestHelper helper) {
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        Cow cow = helper.spawn(EntityTypes.COW, HERE.east(2));
        helper.startSequence()
            .thenIdle(5)
            .thenExecute(() -> brute.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(cow), 1))
            .thenWaitUntil(() -> check(helper, brute.getTarget() == cow, "B9: brute should hit back at the cow, target " + brute.getTarget()))
            .thenSucceed();
    }

    /**
     * H6 (R4b): the goblin is neutral (goblin.json, no Java). A survival player 3 blocks away is left alone; once the
     * player hits it, it goes after that player. The hit comes a few ticks after spawning, because Minecraft's
     * retaliation goal ignores a hit on the mob's first tick. Own environment, because of the player.
     */
    static void neutralMobWaitsUntilHit(GameTestHelper helper) {
        ArmoryMob goblin = spawn(helper, Mobs.goblin(), HERE);
        ServerPlayer player = playerInLevel(helper, GameType.SURVIVAL, HERE.east(3));
        helper.startSequence()
            .thenIdle(60)
            .thenExecute(() -> {
                check(helper, goblin.getTarget() == null, "H6: neutral goblin should leave the player alone, target " + goblin.getTarget());
                checkNear(helper, player.getMaxHealth(), player.getHealth(), "H6: the player is not hurt");
                goblin.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1);
            })
            .thenWaitUntil(() -> check(helper, goblin.getTarget() == player, "H6: after the hit the goblin goes after the player, target " + goblin.getTarget()))
            .thenExecute(() -> remove(helper, player))
            .thenSucceed();
    }

    /** O8 + B15: follow range is exactly 16; a chased player who gets more than 16 blocks away is given up. */
    static void mobGivesUpBeyond16Blocks(GameTestHelper helper) {
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        ServerPlayer player = playerInLevel(helper, GameType.SURVIVAL, HERE.east(4));
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); // survives a hit or two while being chased
        player.setHealth(1000);
        helper.startSequence()
            .thenExecute(() -> checkNear(helper, 16, brute.getAttributeValue(Attributes.FOLLOW_RANGE), "O8: follow range"))
            .thenWaitUntil(() -> check(helper, brute.getTarget() == player, "brute should first target the near player"))
            .thenExecute(() -> player.snapTo(brute.getX() + 24, player.getY(), brute.getZ(), 0, 0))
            .thenWaitUntil(() -> check(helper, brute.getTarget() == null, "B15: brute should give up at 24 blocks, target " + brute.getTarget()))
            .thenExecute(() -> remove(helper, player))
            .thenSucceed();
    }

    /**
     * H4: with no player it can attack, the brute walks somewhere on its own. A creative player watches from 10 blocks:
     * like every vanilla mob, it only strolls while some player is within 32 blocks (Minecraft's idle rule), and a
     * creative player is never attacked. Own environment, because of the player.
     */
    static void mobWandersWithoutPlayers(GameTestHelper helper) {
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        ServerPlayer watcher = playerInLevel(helper, GameType.CREATIVE, HERE.north(10));
        Vec3 start = brute.position();
        helper.succeedWhen(() -> {
            check(helper, brute.getTarget() == null, "H4: nothing to attack, target " + brute.getTarget());
            check(helper, brute.position().subtract(start).horizontalDistanceSqr() > 1.0,
                "H4: brute should wander away from " + start + ", now at " + brute.position());
            remove(helper, watcher);
        });
    }

    // ---- saving, dying, environment ----------------------------------------------------------------------------

    /** B13 + condition 9: save and load a brute with its sword; speed stays 0.175 (not 0.1225), health and drops kept. */
    static void mobReloadKeepsStatsOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        Supplier<ArmoryMob> reloaded = new Supplier<>() {
            private ArmoryMob copy;

            @Override
            public ArmoryMob get() {
                if (copy == null) {
                    brute.setHealth(30);
                    TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                    brute.save(out);
                    CompoundTag saved = out.buildResult();
                    brute.discard();
                    Entity loaded = EntityType.loadEntityRecursive(type(Mobs.brute()), saved, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
                    check(helper, loaded instanceof ArmoryMob, "the save should load back as a brute, got " + loaded);
                    copy = (ArmoryMob) loaded;
                    copy.setUUID(UUID.randomUUID());
                    level.addFreshEntity(copy);
                }
                return copy;
            }
        };
        helper.startSequence()
            .thenWaitUntil(() -> checkNear(helper, 0.175, value(brute, ModStats.MOVEMENT_SPEED), "speed before saving"))
            .thenExecute(reloaded::get)
            .thenIdle(2)
            .thenExecute(() -> {
                ArmoryMob copy = reloaded.get();
                checkNear(helper, 0.175, value(copy, ModStats.MOVEMENT_SPEED), "B13: speed after loading (0.1225 would mean stacked)");
                checkNear(helper, 0.25, base(copy, ModStats.MOVEMENT_SPEED), "B13: base speed after loading");
                checkNear(helper, 10, value(copy, ModStats.ATTACK_DAMAGE), "attack after loading");
                checkNear(helper, 40, value(copy, ModStats.MAX_HEALTH), "max health after loading");
                checkNear(helper, 30, copy.getHealth(), "health after loading");
                check(helper, copy.getMainHandItem().is(equipment("heavy_sword")), "still holds the heavy sword");
                checkNear(helper, 0, copy.getDropChances().byEquipment(EquipmentSlot.MAINHAND), "drop chance after loading");
            })
            .thenSucceed();
    }

    /** B12 + O7: the brute is worth exactly 5 experience and never drops its sword. */
    static void mobDropsNoEquipmentAnd5Xp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        check(helper, brute.getExperienceReward(level, null) == 5,
            "O7: experience should be 5, got " + brute.getExperienceReward(level, null));
        checkNear(helper, 0, brute.getDropChances().byEquipment(EquipmentSlot.MAINHAND), "B12: sword drop chance");
        Vec3 where = brute.position();
        brute.hurtServer(level, level.damageSources().generic(), 1000);
        helper.succeedWhen(() -> {
            check(helper, brute.isDeadOrDying(), "brute should be dead");
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(where, 4, 4, 4));
            check(helper, drops.isEmpty(), "B12: the brute dropped " + drops);
        });
    }

    /** B10, in its own environment at noon: a zombie next to it catches fire, the brute does not. */
    static void mobDoesNotBurnInDaylight(GameTestHelper helper) {
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, HERE.east(2));
        zombie.setBaby(false);
        helper.succeedWhen(() -> {
            check(helper, zombie.isOnFire(), "the zombie should burn first (proves the sun is out)");
            check(helper, !brute.isOnFire(), "B10: the brute must not burn in daylight");
        });
    }

    /**
     * B11: switching to peaceful removes a brute already in the world, and no new one can be made, like a vanilla
     * monster. Runs in an environment that puts the difficulty back afterwards, even if the test fails.
     */
    static void mobRemovedOnPeaceful(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
        helper.succeedWhen(() -> {
            check(helper, brute.isRemoved(), "B11: the brute already in the world should be removed on peaceful");
            check(helper, type(Mobs.brute()).create(level, EntitySpawnReason.SPAWN_ITEM_USE) == null,
                "B11: no new brute can be made on peaceful");
        });
    }

    // ---- behavior contract ---------------------------------------------------------------------------------------

    /** One sample of each behavior kind for the binder contract. A new behavior adds one line here. */
    private static final Map<Class<? extends Behavior>, Behavior> SAMPLES = Map.of(
        WanderBehavior.class, new WanderBehavior(1.0),
        AttackPlayersBehavior.class, new AttackPlayersBehavior(1.0),
        NeutralBehavior.class, new NeutralBehavior(1.0),   // R4b
        LeapBehavior.class, new LeapBehavior());           // R4b

    /**
     * Contract for behaviors (B4 and 05's binder contract): every behavior of every mob has a binder; a missing binder
     * stops startup naming the mob; each binder adds at least one goal; binding a definition twice adds nothing.
     */
    static void mobBehaviorsAllHaveBinders(GameTestHelper helper) {
        BehaviorGoals standard = BehaviorGoals.standard();
        for (MobDefinition mob : MOBS.catalog().all()) {
            for (Behavior behavior : mob.behaviors()) {
                check(helper, standard.kinds().contains(behavior.getClass()), mob.id() + " uses " + behavior.name() + " without a binder");
            }
        }
        check(helper, SAMPLES.keySet().equals(standard.kinds()), "binder contract needs one sample per behavior kind: " + standard.kinds());

        try {
            new BehaviorGoals().verify(MOBS.catalog());
            check(helper, false, "B4: verify should fail when behaviors have no binder");
        } catch (IllegalStateException expected) {
            check(helper, expected.getMessage().contains("brute"), "B4: message should name the mob: " + expected.getMessage());
        }

        ArmoryMob brute = spawn(helper, Mobs.brute(), HERE);
        int goals = goalCount(brute);
        standard.bind(brute, brute.definition().behaviors(), brute.goals());
        check(helper, goals == goalCount(brute), "binding the same definition twice must not add goals");

        SAMPLES.forEach((kind, sample) -> {
            ArmoryMob mob = spawn(helper, Mobs.brute(), HERE);
            int before = goalCount(mob);
            standard.binderFor(sample).bind(mob, sample, mob.goals());
            check(helper, goalCount(mob) > before, kind.getSimpleName() + "'s binder should add at least one goal");
        });
        helper.succeed();
    }

    private static int goalCount(ArmoryMob mob) {
        return mob.goalSelector.getAvailableGoals().size() + mob.targetSelector.getAvailableGoals().size();
    }

    // ---- combat with the mob's own stats -----------------------------------------------------------------------------

    /**
     * H5 + H6: the skirmisher's own stats work in combat. Dodge 0.30: some of 200 zombie hits are avoided.
     * Crit 0.5: some of its 100 hits on a cow deal 4 x 1.5 = 6 instead of 4.
     */
    static void skirmisherDodgesAndCrits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ArmoryMob skirmisher = spawn(helper, Mobs.skirmisher(), HERE);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, HERE.east(3));
        Cow cow = helper.spawn(EntityTypes.COW, HERE.west(3));
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
        cow.setHealth(10000);
        helper.startSequence()
            .thenWaitUntil(() -> checkNear(helper, 4, value(skirmisher, ModStats.ATTACK_DAMAGE), "skirmisher attack with dagger"))
            .thenExecute(() -> {
                skirmisher.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                skirmisher.setHealth(1000);
                DamageSource bite = level.damageSources().mobAttack(zombie);
                int dodged = 0;
                for (int i = 0; i < 200; i++) {
                    skirmisher.damageCooldownTime = 0;
                    if (!skirmisher.hurtServer(level, bite, 1f)) {
                        dodged++;
                    }
                }
                // Expected 60 of 200; the bounds are more than 5 standard deviations away.
                check(helper, dodged > 25 && dodged < 100, "H5: with 30% dodge, dodged " + dodged + " of 200");

                int crits = 0;
                for (int i = 0; i < 100; i++) {
                    cow.damageCooldownTime = 0;
                    float before = cow.getHealth();
                    skirmisher.doHurtTarget(level, cow);
                    double dealt = before - cow.getHealth();
                    check(helper, Math.abs(dealt - 4) < 1e-4 || Math.abs(dealt - 6) < 1e-4, "H6: hit dealt " + dealt);
                    if (dealt > 5) {
                        crits++;
                    }
                }
                // Expected 50 of 100; the bounds are 5 standard deviations away.
                check(helper, crits > 25 && crits < 75, "H6: with 50% crit, " + crits + " of 100 hits were crits");
            })
            .thenSucceed();
    }
}
