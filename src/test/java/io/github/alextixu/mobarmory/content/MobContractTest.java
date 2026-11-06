package io.github.alextixu.mobarmory.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.equipment.LoadoutCalculator;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobEquipmentCheck;
import io.github.alextixu.mobarmory.core.stat.StackingRules;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import io.github.alextixu.mobarmory.core.stat.StatModifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract tests: every mob definition runs the same checks (06, success condition 1). A new mob is covered as
 * soon as it is in {@link ModMobs#ALL}; nothing in this file needs to change. That every behavior has a binder is
 * part of the same contract, checked in game ({@code mob_behaviors_all_have_binders}) because binders are Forge code.
 */
class MobContractTest {

    static Stream<MobDefinition> allMobs() {
        return ModMobs.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allMobs")
    void isInTheCatalog(MobDefinition mob) {
        assertEquals(mob, MobTestCatalog.MOBS.get(mob.id()), mob.id() + " was not accepted by the catalog");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allMobs")
    void onlySetsRegisteredStatsWithinLimits(MobDefinition mob) {
        mob.stats().forEach((id, value) -> {
            StatDefinition stat = ModStats.CATALOG.find(id).orElseThrow(() -> new AssertionError(mob.id() + " sets unknown " + id));
            assertTrue(value >= stat.min() && value <= stat.max(), mob.id() + " sets " + id + " to " + value);
        });
    }

    /**
     * In the game a missing piece is only a warning (O2 changed to B), so this contract is what still catches a typo
     * in a definition: with the shipped weapons, every mob gets everything it lists.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allMobs")
    void getsAllItsEquipmentFromTheShippedWeapons(MobDefinition mob) {
        var result = MobEquipmentCheck.check(mob, MobTestCatalog.SHIPPED_WEAPONS::find);
        assertEquals(List.of(), result.problems(), mob.id() + " would spawn with a warning on a fresh install");
        assertEquals(mob.equipment(), result.given());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allMobs")
    void spawnsWithEveryStat(MobDefinition mob) {
        Map<StatId, Double> values = mob.spawnStats(ModStats.CATALOG);
        for (StatDefinition stat : ModStats.CATALOG.all()) {
            assertTrue(values.containsKey(stat.id()), mob.id() + " spawns without " + stat.id());
        }
    }

    /** Equipment on top of the mob's own stats never silently hits a limit (same rule as the equipment contract). */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allMobs")
    void equipmentKeepsStatsWithinLimits(MobDefinition mob) {
        Map<StatId, Double> base = mob.spawnStats(ModStats.CATALOG);
        List<StatModifier> active = new LoadoutCalculator(MobTestCatalog.SHIPPED_WEAPONS).activeModifiers(mob.equipment());
        assertEquals(mob.equipment().size() > 0, !active.isEmpty(), mob.id() + ": equipment should be active");
        for (StatId id : base.keySet()) {
            List<StatModifier> forStat = active.stream().filter(modifier -> modifier.stat().equals(id)).toList();
            var result = StackingRules.stack(ModStats.CATALOG.get(id), base.get(id), forStat);
            assertFalse(result.clamped(), mob.id() + ": equipment pushes " + id + " to " + result.raw() + ", outside its limits");
        }
    }
}
