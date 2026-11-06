package io.github.alextixu.mobarmory.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract for content files: adding a stat, a piece of equipment or a mob also needs a name and (for equipment
 * and mob spawn eggs) an item model. Forgetting one shows up here instead of as a missing texture or a raw
 * translation key.
 */
class ContentResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/mobarmory");

    static Stream<EquipmentDefinition> allEquipment() {
        return ModEquipment.CATALOG.all().stream();
    }

    static Stream<MobDefinition> allMobs() {
        return ModMobs.ALL.stream();
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

        String[] look = equipment.look().split(":", 2);
        String expected = "\"" + look[0] + ":item/" + look[1] + "\"";
        assertTrue(Files.readString(model).contains(expected), model + " should use model " + expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allEquipment")
    void equipmentHasName(EquipmentDefinition equipment) throws IOException {
        String key = "\"item.mobarmory." + equipment.id().value() + "\"";
        assertTrue(lang().contains(key), "en_us.json is missing " + key);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("customStats")
    void customStatHasName(StatDefinition stat) throws IOException {
        String key = "\"attribute.name.mobarmory." + stat.id().value() + "\"";
        assertTrue(lang().contains(key), "en_us.json is missing " + key);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allMobs")
    void mobHasName(MobDefinition mob) throws IOException {
        String key = "\"entity.mobarmory." + mob.id().value() + "\"";
        assertTrue(lang().contains(key), "en_us.json is missing " + key);
        assertTrue(lang().contains("\"item.mobarmory.spawn_egg\""), "en_us.json is missing the spawn egg name");
    }

    /** The egg borrows the vanilla spawn egg of the body the mob borrows (a zombie-looking brute has a zombie egg). */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allMobs")
    void mobHasSpawnEggModel(MobDefinition mob) throws IOException {
        Path model = ASSETS.resolve("items/" + mob.id().value() + "_spawn_egg.json");
        assertTrue(Files.exists(model), "missing " + model);

        String body = mob.look().body().name().toLowerCase(Locale.ROOT);
        String expected = "\"minecraft:item/" + body + "_spawn_egg\"";
        assertTrue(Files.readString(model).contains(expected), model + " should use model " + expected);
    }
}
