package io.github.alextixu.mobarmory.gametest;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import io.github.alextixu.mobarmory.forge.StatAttributeBridge;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.GameType;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/**
 * System tests that run inside a real game server ({@code gradlew runGameTestServer}).
 * <p>
 * Forge 66 ships the {@code @GameTest} annotation but nothing collects it, so tests are wired the way
 * Minecraft 26.x does it: each function is registered in {@code Registries.TEST_FUNCTION} here, and each has a
 * test instance in {@code data/mobarmory/test_instance/<name>.json}. The two names must match.
 * <p>
 * This whole source set ({@code src/gametest}) is a separate test-only mod that only the {@code gameTestServer} run
 * loads; it is never packed into the mod jar. Test instances are data-pack registry entries, and Minecraft marks any such
 * entry from a non-vanilla pack as experimental, which would make every world show an "experimental settings"
 * warning (N5). The tests register themselves through this event subscriber, so Mob Armory never references them.
 */
@Mod.EventBusSubscriber(modid = MobArmoryGameTestMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MobArmoryGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    private static final double EPS = 1e-6;

    static {
        test("custom_stats_on_every_mob", MobArmoryGameTests::customStatsOnEveryMob);
        test("vanilla_stats_map_to_attributes", MobArmoryGameTests::vanillaStatsMapToAttributes);
        EquipmentGameTests.registerAll();
        CombatGameTests.registerAll();
        PlayerGameTests.registerAll();
        LiveEditGameTests.registerAll();
        WebAdminGameTests.registerAll();
    }

    private MobArmoryGameTests() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach(
            (name, function) -> helper.register(Identifier.fromNamespaceAndPath(MobArmoryMod.MODID, name), function)));
    }

    static void test(String name, Consumer<GameTestHelper> function) {
        if (TESTS.putIfAbsent(name, function) != null) {
            throw new IllegalStateException("Game test '" + name + "' is registered twice");
        }
    }

    static StatAttributeBridge stats() {
        return MobArmoryMod.STATS;
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(Component.literal(message));
        }
    }

    static void checkNear(GameTestHelper helper, double expected, double actual, String what) {
        check(helper, Math.abs(expected - actual) < EPS, what + ": expected " + expected + " but was " + actual);
    }

    /** H3 (system): every custom stat exists on players, zombies and skeletons, at its default value. */
    static void customStatsOnEveryMob(GameTestHelper helper) {
        List<LivingEntity> entities = List.of(
            helper.makeMockPlayer(GameType.SURVIVAL),
            helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO),
            helper.spawn(EntityTypes.SKELETON, BlockPos.ZERO),
            helper.spawn(EntityTypes.COW, BlockPos.ZERO));
        for (LivingEntity entity : entities) {
            for (StatDefinition stat : ModStats.CATALOG.custom()) {
                AttributeInstance instance = entity.getAttribute(stats().holder(stat.id()));
                check(helper, instance != null, entity.getType().toShortString() + " is missing " + stat.id());
                checkNear(helper, stat.defaultValue(), instance.getValue(), entity.getType().toShortString() + " " + stat.id());
            }
        }
        helper.succeed();
    }

    /** Every vanilla-backed stat resolves to an attribute players actually have. */
    static void vanillaStatsMapToAttributes(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (StatDefinition stat : ModStats.CATALOG.all()) {
            if (!stat.isCustom()) {
                check(helper, player.getAttribute(stats().holder(stat.id())) != null, "player is missing " + stat.id());
            }
        }
        checkNear(helper, 0.1, player.getAttributeBaseValue(stats().holder(ModStats.MOVEMENT_SPEED.id())), "player base speed");
        helper.succeed();
    }
}
