package io.github.alextixu.mobarmory.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.data.DefaultWeapons;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract for content files: adding a stat or a piece of equipment also needs a name and (for equipment)
 * an item model. Forgetting one shows up here instead of as a missing texture or a raw translation key.
 */
class ContentResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/mobarmory");

    static Stream<EquipmentDefinition> allEquipment() {
        return DefaultWeapons.catalog(ModStats.CATALOG).all().stream();
    }

    static Stream<StatDefinition> customStats() {
        return ModStats.CATALOG.custom().stream();
    }

    private static String lang() throws IOException {
        return Files.readString(ASSETS.resolve("lang/en_us.json"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEquipment")
    void equipmentHasItemModelPointingAtItsLook(EquipmentDefinition equipment) throws IOException {
        Path model = ASSETS.resolve("items/" + equipment.id().value() + ".json");
        assertTrue(Files.exists(model), "missing " + model);

        String[] look = ((EquipmentLook.VanillaItem) equipment.look()).item().split(":", 2);
        String expected = "\"" + look[0] + ":item/" + look[1] + "\"";
        assertTrue(Files.readString(model).contains(expected), model + " should use model " + expected);
    }

    /** R2b: the name comes from the weapon file (shown as-is), so it is never a raw translation key. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allEquipment")
    void equipmentHasName(EquipmentDefinition equipment) {
        assertTrue(!equipment.name().isBlank() && !equipment.name().startsWith("item."), equipment.id() + ": " + equipment.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("customStats")
    void customStatHasName(StatDefinition stat) throws IOException {
        String key = "\"attribute.name.mobarmory." + stat.id().value() + "\"";
        assertTrue(lang().contains(key), "en_us.json is missing " + key);
    }
}
