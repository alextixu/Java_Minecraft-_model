package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.stats;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobReloadReport;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.data.DefaultMobs;
import io.github.alextixu.mobarmory.data.MobAssets;
import io.github.alextixu.mobarmory.data.MobFileJson;
import io.github.alextixu.mobarmory.forge.ArmoryMob;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import io.github.alextixu.mobarmory.forge.MobNames;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * System tests for R4b (22-r4b-mob-admin-given-when-then.md): a mob that exists only as a JSON file (T2), and changing
 * mobs while the game runs through the same {@code MOB_ADMIN} the web page and {@code /mobarmory reload} use.
 * <p>
 * Tests that change the shared brute run in main's {@code mobarmory:live_edit} batch and follow its rules
 * ({@link LiveEditGameTests#inBatch}): everything within one tick, the shipped file put back before they finish.
 */
final class MobAdminGameTests {
    /** The middle of {@code mobarmory:arena}. */
    private static final BlockPos HERE = new BlockPos(12, 0, 12);

    private MobAdminGameTests() {
    }

    static void registerAll() {
        test("t2_goblin_from_json_only", MobAdminGameTests::goblinFromJsonOnly);
        test("mob_save_changes_stats_at_once", MobAdminGameTests::saveChangesStatsAtOnce);
        test("mob_save_changes_name_and_size_at_once", MobAdminGameTests::saveChangesNameAndSizeAtOnce);
        test("mob_save_changes_behaviors_for_new_mobs", MobAdminGameTests::saveChangesBehaviorsForNewMobs);
        test("mob_save_never_heals", MobAdminGameTests::saveNeverHeals);
        test("mob_reload_reads_hand_edits", MobAdminGameTests::reloadReadsHandEdits);
        test("mob_loads_with_current_stats", MobAdminGameTests::loadsWithCurrentStats);
        test("reload_command_prints_weapons_combos_mobs", MobAdminGameTests::reloadCommandPrintsThreeSections);
    }

    /** Spawns like a spawn egg would (finalizeSpawn takes the definition in effect now). */
    private static ArmoryMob spawn(GameTestHelper helper, MobId id) {
        return helper.spawn(MobArmoryMod.MOBS.type(id), HERE, EntitySpawnReason.SPAWN_ITEM_USE);
    }

    private static double value(LivingEntity entity, StatDefinition stat) {
        return entity.getAttributeValue(stats().holder(stat.id()));
    }

    private static boolean hasGoal(ArmoryMob mob, Class<? extends Goal> kind) {
        return mob.goals().goals().getAvailableGoals().stream().map(WrappedGoal::getGoal).anyMatch(kind::isInstance)
            || mob.goals().targets().getAvailableGoals().stream().map(WrappedGoal::getGoal).anyMatch(kind::isInstance);
    }

    /** The brute's shipped file with some text replaced, read through the same rules as the web page's save. */
    private static MobDefinition brute(String from, String to) throws Exception {
        String text = DefaultMobs.text("brute");
        if (!text.contains(from)) {
            throw new IllegalStateException("brute.json has no '" + from + "'");
        }
        return MobFileJson.read(DefaultMobs.BRUTE, text.replace(from, to), MobArmoryMod.STATS.catalog());
    }

    /** Puts the shipped brute.json and definition back, whatever the test did. */
    static void restore() {
        try {
            MobArmoryMod.MOB_FILES.writeMob(DefaultMobs.BRUTE, DefaultMobs.text("brute"));
            MobArmoryMod.MOB_ADMIN.reload();
        } catch (Exception e) {
            throw new IllegalStateException("could not restore brute.json", e);
        }
    }

    /** {@link LiveEditGameTests#inBatch} for mobs: one tick, then the shipped brute and heavy sword are back. */
    private static void inBatch(GameTestHelper helper, LiveEditGameTests.Body body) {
        LiveEditGameTests.inBatch(helper, () -> {
            try {
                body.run();
            } finally {
                restore();
            }
        });
    }

    /**
     * H2, T2: {@code goblin.json} is only a JSON file in the test server's config folder; no Java and no resource file
     * mention it. After the start it is a kind of mob like the others: its own entity type and spawn egg, its name, its
     * stats with the dagger on top, its look, its behaviors (neutral running at 1.3, leap).
     */
    static void goblinFromJsonOnly(GameTestHelper helper) {
        check(helper, MobArmoryMod.MOB_LIBRARY.isRegistered(Mobs.GOBLIN), "goblin.json should be an entity type at startup");
        ArmoryMob goblin = spawn(helper, Mobs.GOBLIN);
        check(helper, goblin.getName().getString().equals("Goblin"), "name from the file, got " + goblin.getName().getString());
        Component egg = new ItemStack(MobArmoryMod.MOBS.spawnEgg(Mobs.GOBLIN)).getHoverName();
        check(helper, egg.getContents() instanceof TranslatableContents name && name.getArgs().length == 1
            && String.valueOf(name.getArgs()[0]).equals("Goblin"), "the egg is \"Goblin Spawn Egg\", got " + egg);
        check(helper, MobAssets.spawnEggModels(java.util.List.of(Mobs.goblin())).get("items/goblin_spawn_egg.json")
            .contains("minecraft:item/husk_spawn_egg"), "the egg borrows the husk egg");

        MobArmoryMod.EQUIPMENT_STATS.refresh(goblin);
        checkNear(helper, 12, goblin.getMaxHealth(), "T2: max health");
        checkNear(helper, 12, goblin.getHealth(), "T2: spawns at full health");
        checkNear(helper, 5, value(goblin, ModStats.ATTACK_DAMAGE), "T2: attack 3 + dagger 2");
        checkNear(helper, 0.25, value(goblin, ModStats.CRIT_CHANCE), "T2: crit from the dagger");
        checkNear(helper, 0.33, value(goblin, ModStats.MOVEMENT_SPEED), "T2: speed 0.3 x 1.1");
        checkNear(helper, 0.8, goblin.getAttributeValue(Attributes.SCALE), "T2: size");
        check(helper, goblin.getMainHandItem().is(MobArmoryMod.EQUIPMENT.item(io.github.alextixu.mobarmory.core.equipment.EquipmentId.of("dagger"))),
            "T2: holds its dagger");

        check(helper, hasGoal(goblin, HurtByTargetGoal.class), "H6: neutral hits back");
        check(helper, !hasGoal(goblin, NearestAttackableTargetGoal.class), "H6: neutral never hunts players");
        check(helper, hasGoal(goblin, LeapAtTargetGoal.class), "H8: leap");
        MeleeAttackGoal melee = (MeleeAttackGoal) goblin.goals().goals().getAvailableGoals().stream().map(WrappedGoal::getGoal)
            .filter(MeleeAttackGoal.class::isInstance).findFirst().orElseThrow();
        checkNear(helper, 1.3, speedOf(melee), "H7: runs at 1.3 x its speed while chasing");
        goblin.discard();
        helper.succeed();
    }

    private static double speedOf(MeleeAttackGoal goal) {
        try {
            Field field = MeleeAttackGoal.class.getDeclaredField("speedModifier");
            field.setAccessible(true);
            return field.getDouble(goal);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * H4 + B22: saving the brute with attack 8 and speed 0.3 changes a brute already in the world at once (attack
     * 8 + 6 = 14, speed 0.3 x 0.7 = 0.21) and one spawned afterwards; the file holds the new numbers.
     */
    static void saveChangesStatsAtOnce(GameTestHelper helper) {
        inBatch(helper, () -> {
            ArmoryMob brute = spawn(helper, DefaultMobs.BRUTE);
            MobArmoryMod.EQUIPMENT_STATS.refresh(brute);
            try {
                checkNear(helper, 10, value(brute, ModStats.ATTACK_DAMAGE), "given: brute with the heavy sword");
                MobArmoryMod.MOB_ADMIN.update(brute("\"attack_damage\": 4", "\"attack_damage\": 8"));
                checkNear(helper, 14, value(brute, ModStats.ATTACK_DAMAGE), "H4: the brute in the world hits for 14 at once");
                // A save replaces the whole mob, so this one (from the shipped file) also puts attack back to 4 (B19).
                MobArmoryMod.MOB_ADMIN.update(brute("\"movement_speed\": 0.25", "\"movement_speed\": 0.3"));
                checkNear(helper, 0.21, value(brute, ModStats.MOVEMENT_SPEED), "B22: speed 0.3 x 0.7 with the sword");
                checkNear(helper, 10, value(brute, ModStats.ATTACK_DAMAGE), "B19: the later save wins, attack 4 + 6");

                MobArmoryMod.MOB_ADMIN.update(brute("\"attack_damage\": 4", "\"attack_damage\": 8"));
                ArmoryMob later = spawn(helper, DefaultMobs.BRUTE);
                MobArmoryMod.EQUIPMENT_STATS.refresh(later);
                checkNear(helper, 14, value(later, ModStats.ATTACK_DAMAGE), "H4: a brute spawned afterwards too");
                later.discard();
                String file = java.nio.file.Files.readString(MobArmoryMod.MOB_FILES.mobsFolder().resolve("brute.json"));
                check(helper, file.contains("\"attack_damage\": 8"), "the file holds the save: " + file);
            } finally {
                brute.discard();
            }
        });
    }

    /**
     * H10 + H11: a new name shows at once on the brute and its egg, and is what the server sends to every player; a new
     * size applies at once.
     */
    static void saveChangesNameAndSizeAtOnce(GameTestHelper helper) {
        inBatch(helper, () -> {
            ArmoryMob brute = spawn(helper, DefaultMobs.BRUTE);
            try {
                MobArmoryMod.MOB_ADMIN.update(brute("\"Brute\"", "\"Berserker\"").withLook(Mobs.brute().look().scaled(1.5)));
                check(helper, brute.getName().getString().equals("Berserker"), "H10: name at once, got " + brute.getName().getString());
                check(helper, MobNames.of(DefaultMobs.BRUTE, MobArmoryMod.MOB_LIBRARY).equals("Berserker"), "H10: the egg's name");
                check(helper, "Berserker".equals(MobArmoryMod.MOB_SYNC.current().names().get("brute")),
                    "H10: the names sent to players, got " + MobArmoryMod.MOB_SYNC.current().names());
                checkNear(helper, 1.5, brute.getAttributeValue(Attributes.SCALE), "H11: size at once");
            } finally {
                brute.discard();
            }
        });
    }

    /**
     * H5: changing the brute from attacking players to neutral leaves a brute already in the world as it was (its AI was
     * set up when it was created, Q6 A); a brute spawned afterwards is neutral.
     */
    static void saveChangesBehaviorsForNewMobs(GameTestHelper helper) {
        inBatch(helper, () -> {
            ArmoryMob before = spawn(helper, DefaultMobs.BRUTE);
            try {
                MobArmoryMod.MOB_ADMIN.update(brute("\"type\": \"attack_players\"", "\"type\": \"neutral\""));
                ArmoryMob after = spawn(helper, DefaultMobs.BRUTE);
                check(helper, hasGoal(before, NearestAttackableTargetGoal.class), "H5: the brute already there still hunts players");
                check(helper, !hasGoal(after, NearestAttackableTargetGoal.class), "H5: a new brute is neutral");
                check(helper, hasGoal(after, HurtByTargetGoal.class), "H5: a new brute still hits back");
                after.discard();
            } finally {
                before.discard();
            }
        });
    }

    /** B17: a hurt brute (25/40) keeps 25 when its maximum becomes 30, and is not healed when it becomes 60. */
    static void saveNeverHeals(GameTestHelper helper) {
        inBatch(helper, () -> {
            ArmoryMob brute = spawn(helper, DefaultMobs.BRUTE);
            try {
                brute.setHealth(25);
                MobArmoryMod.MOB_ADMIN.update(brute("\"max_health\": 40", "\"max_health\": 30"));
                checkNear(helper, 30, brute.getMaxHealth(), "B17: new maximum");
                checkNear(helper, 25, brute.getHealth(), "B17: health below the new maximum stays");
                MobArmoryMod.MOB_ADMIN.update(brute("\"max_health\": 40", "\"max_health\": 60"));
                checkNear(helper, 60, brute.getMaxHealth(), "B17: new maximum");
                checkNear(helper, 25, brute.getHealth(), "B17: not healed");
            } finally {
                brute.discard();
            }
        });
    }

    /** H9: brute.json edited by hand (max health 60), then /mobarmory reload: the brute in the world follows at once. */
    static void reloadReadsHandEdits(GameTestHelper helper) {
        inBatch(helper, () -> {
            ArmoryMob brute = spawn(helper, DefaultMobs.BRUTE);
            try {
                MobArmoryMod.MOB_FILES.writeMob(DefaultMobs.BRUTE, DefaultMobs.text("brute").replace("\"max_health\": 40", "\"max_health\": 60"));
                MobReloadReport report = MobArmoryMod.MOB_ADMIN.reload();
                check(helper, report.changed().equals(java.util.List.of(DefaultMobs.BRUTE)), "H9: report " + report);
                checkNear(helper, 60, brute.getMaxHealth(), "H9: the brute in the world follows the file");
                checkNear(helper, 40, brute.getHealth(), "O10: not healed");
            } finally {
                brute.discard();
            }
        });
    }

    /**
     * H12: a brute saved before its attack was changed to 8 loads with the attack in effect now (8 + sword 6 = 14), and
     * its speed is still 0.175, not stacked.
     */
    static void loadsWithCurrentStats(GameTestHelper helper) {
        inBatch(helper, () -> {
            ServerLevel level = helper.getLevel();
            ArmoryMob brute = spawn(helper, DefaultMobs.BRUTE);
            MobArmoryMod.EQUIPMENT_STATS.refresh(brute);
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            brute.save(out);
            CompoundTag saved = out.buildResult();
            brute.discard();

            MobArmoryMod.MOB_ADMIN.update(brute("\"attack_damage\": 4", "\"attack_damage\": 8"));
            EntityType<ArmoryMob> type = MobArmoryMod.MOBS.type(DefaultMobs.BRUTE);
            Entity loaded = EntityType.loadEntityRecursive(type, saved, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
            check(helper, loaded instanceof ArmoryMob, "the save should load back as a brute, got " + loaded);
            ArmoryMob copy = (ArmoryMob) loaded;
            copy.setUUID(UUID.randomUUID());
            level.addFreshEntity(copy);
            MobArmoryMod.EQUIPMENT_STATS.refresh(copy); // what the first tick does, so the test stays within one tick
            try {
                checkNear(helper, 14, value(copy, ModStats.ATTACK_DAMAGE), "H12: attack follows the definition in effect now");
                checkNear(helper, 0.175, value(copy, ModStats.MOVEMENT_SPEED), "H12: speed not stacked");
                check(helper, copy.getMainHandItem().is(MobArmoryMod.EQUIPMENT.item(io.github.alextixu.mobarmory.core.equipment.EquipmentId.of("heavy_sword"))),
                    "keeps the sword it was saved with");
            } finally {
                copy.discard();
            }
        });
    }

    /**
     * Merge with R3 (review-05 F1): the real {@code /mobarmory reload} reads weapons, combos and mobs, in that order, each
     * with its own line (R4b O12: each kind is all or nothing on its own).
     */
    static void reloadCommandPrintsThreeSections(GameTestHelper helper) {
        inBatch(helper, () -> {
            List<String> keys = new ArrayList<>();
            CommandSource capture = new CommandSource() {
                @Override
                public void sendSystemMessage(Component message) {
                    if (message.getContents() instanceof TranslatableContents translatable) {
                        keys.add(translatable.getKey());
                    }
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
            MinecraftServer server = helper.getLevel().getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture), "mobarmory reload");
            int weapons = keys.indexOf("commands.mobarmory.reload.done");
            int combos = keys.indexOf("commands.mobarmory.reload.combos");
            int mobs = keys.indexOf("commands.mobarmory.reload.mobs.done");
            check(helper, weapons >= 0 && weapons < combos && combos < mobs, "weapons, then combos, then mobs: " + keys);
        });
    }
}
