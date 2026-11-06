package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.stats;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import io.github.alextixu.mobarmory.core.stat.StatModifier;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * System tests for section E (25-stat-files-given-when-then.md): stats a player added as files reach every mob and can
 * be used by weapons. The test server starts with two such files, copied from {@code src/gametest/config} by
 * build.gradle: {@code gametest_luck} (a stat of its own, default 0.25) and {@code gametest_jump} (Minecraft's jump
 * strength). Its stats folder has no built-in files, so the built-in stats come from code (B4).
 */
final class StatFileGameTests {
    private static final StatId LUCK = StatId.of("gametest_luck");
    private static final StatId JUMP = StatId.of("gametest_jump");
    private static final EquipmentId SWORD = EquipmentId.of("heavy_sword");

    private StatFileGameTests() {
    }

    static void registerAll() {
        test("stat_files_reach_every_mob", StatFileGameTests::reachEveryMob);
        test("weapon_uses_stats_from_files", StatFileGameTests::weaponUsesThem);
    }

    private static double value(LivingEntity entity, StatId id) {
        return entity.getAttributeValue(stats().holder(id));
    }

    /** H2, H4, B4, Q7 A: both files are stats now, on a zombie and on a player, next to the seven built-in ones. */
    static void reachEveryMob(GameTestHelper helper) {
        check(helper, stats().catalog().size() == ModStats.CATALOG.size() + 2,
            "H2: 7 built-in stats plus 2 from files, got " + stats().catalog().all());
        StatDefinition luck = stats().catalog().get(LUCK);
        check(helper, luck.isCustom(), "H2: gametest_luck is a stat of its own");
        check(helper, "minecraft:jump_strength".equals(stats().catalog().get(JUMP).vanillaAttribute().orElse("")),
            "H4: gametest_jump is Minecraft's jump strength");
        check(helper, MobArmoryMod.STAT_SETUP.builtInsFromCode().size() == ModStats.CATALOG.size(),
            "B4: no built-in files in this folder, so all seven come from code: " + MobArmoryMod.STAT_SETUP.builtInsFromCode());
        check(helper, ModStats.CATALOG.all().equals(stats().catalog().all().subList(0, ModStats.CATALOG.size())),
            "B4: the built-in stats are exactly ModStats, first");
        check(helper, "Luck (test)".equals(MobArmoryMod.STAT_SETUP.names().get(LUCK)), "Q7 A: the file's name is kept");

        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        checkNear(helper, 0.25, value(zombie, LUCK), "H2: a zombie has gametest_luck at its default");
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        checkNear(helper, 0.25, value(player, LUCK), "H2: a player has gametest_luck at its default");
        checkNear(helper, 0.42, value(player, JUMP), "H4: a player's jump strength is Minecraft's 0.42");
        helper.succeed();
    }

    /** H3, H4: the heavy sword gets +0.5 luck and +50% jump; the player holding it has both, then it is put back. */
    static void weaponUsesThem(GameTestHelper helper) {
        LiveEditGameTests.inBatch(helper, () -> {
            ServerPlayer player = LiveEditGameTests.playerHoldingSword(helper);
            EquipmentDefinition sword = MobArmoryMod.EQUIPMENT_LIBRARY.current().get(SWORD);
            List<StatModifier> modifiers = new ArrayList<>(sword.modifiers());
            modifiers.add(StatModifier.add(LUCK, 0.5));
            modifiers.add(StatModifier.percent(JUMP, 0.5));

            MobArmoryMod.WEAPON_ADMIN.update(new EquipmentDefinition(SWORD, sword.name(), sword.slot(), modifiers, sword.look()));

            checkNear(helper, 0.75, value(player, LUCK), "H3: luck with the sword (0.25 + 0.5)");
            checkNear(helper, 0.63, value(player, JUMP), "H4: jump strength with the sword (0.42 x 1.5)");
        });
    }
}
