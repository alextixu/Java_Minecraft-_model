package io.github.alextixu.mobarmory.core.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModEquipment;
import io.github.alextixu.mobarmory.content.ModStats;
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
 * Contract tests: every piece of equipment runs the same checks. A new piece is covered as soon as it is
 * added to {@link ModEquipment#CATALOG}; nothing in this file needs to change.
 */
class EquipmentContractTest {
    private static final double EPS = 1e-9;
    private final LoadoutCalculator calculator = new LoadoutCalculator(ModEquipment.CATALOG);

    static Stream<EquipmentDefinition> allEquipment() {
        return ModEquipment.CATALOG.all().stream();
    }

    /** Each stat starts at its catalog default, which for vanilla stats is the player's base. */
    private static double base(StatId stat) {
        return ModStats.CATALOG.get(stat).defaultValue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEquipment")
    void onlyReferencesRegisteredStats(EquipmentDefinition equipment) {
        for (StatModifier modifier : equipment.modifiers()) {
            assertTrue(ModStats.CATALOG.contains(modifier.stat()), equipment.id() + " uses " + modifier.stat());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEquipment")
    void eachModifierAloneStaysWithinLimits(EquipmentDefinition equipment) {
        for (StatModifier modifier : equipment.modifiers()) {
            StatDefinition stat = ModStats.CATALOG.get(modifier.stat());
            var result = StackingRules.stack(stat, stat.defaultValue(), List.of(modifier));
            assertFalse(result.clamped(),
                equipment.id() + " alone pushes " + stat.id() + " to " + result.raw() + ", outside its limits");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEquipment")
    void changesEveryListedStatInItsOwnSlot(EquipmentDefinition equipment) {
        Map<StatId, Double> values = calculator.compute(Map.of(equipment.slot(), equipment.id()), EquipmentContractTest::base);
        for (StatModifier modifier : equipment.modifiers()) {
            assertTrue(values.containsKey(modifier.stat()), equipment.id() + " did not change " + modifier.stat());
            assertNotEquals(base(modifier.stat()), values.get(modifier.stat()), EPS);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEquipment")
    void doesNothingInAnyOtherSlot(EquipmentDefinition equipment) {
        for (EquipmentSlotKind slot : EquipmentSlotKind.values()) {
            if (slot != equipment.slot()) {
                assertTrue(calculator.compute(Map.of(slot, equipment.id()), EquipmentContractTest::base).isEmpty(),
                    equipment.id() + " worked in " + slot);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEquipment")
    void equipThenUnequipReturnsToBase(EquipmentDefinition equipment) {
        calculator.compute(Map.of(equipment.slot(), equipment.id()), EquipmentContractTest::base);

        assertTrue(calculator.compute(Map.of(), EquipmentContractTest::base).isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEquipment")
    void recomputingDoesNotStack(EquipmentDefinition equipment) {
        var worn = Map.of(equipment.slot(), equipment.id());
        var first = calculator.compute(worn, EquipmentContractTest::base);

        assertEquals(first, calculator.compute(worn, EquipmentContractTest::base));
    }
}
