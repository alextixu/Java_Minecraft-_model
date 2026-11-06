package io.github.alextixu.mobarmory.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.data.BuiltInTextures;
import io.github.alextixu.mobarmory.data.DefaultWeapons;
import io.github.alextixu.mobarmory.data.PngCheck;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract for content files: every default piece has an item model pointing at its look, every custom stat has a
 * name, and every built-in texture (decision Q5 B) has a usable PNG, a model and an item model definition. Forgetting
 * one shows up here instead of as a missing texture or a raw translation key.
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

    /** Shown before the server has stamped the current look on a stack (see forge/WeaponLooks). */
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

    @ParameterizedTest(name = "{0}")
    @FieldSource("io.github.alextixu.mobarmory.data.BuiltInTextures#NAMES")
    void builtInTextureIsComplete(String name) throws IOException {
        Path png = ASSETS.resolve("textures/item/" + name + ".png");
        assertTrue(Files.exists(png), "missing " + png);
        assertEquals(Optional.empty(), PngCheck.problem(Files.readAllBytes(png)), name + ".png");

        Path model = ASSETS.resolve("models/item/look/" + name + ".json");
        assertTrue(Files.readString(model).contains("\"mobarmory:item/" + name + "\""), model + " should use the texture");

        Path itemModel = ASSETS.resolve("items/look/" + name + ".json");
        assertTrue(Files.readString(itemModel).contains("\"mobarmory:item/look/" + name + "\""), itemModel + " should use the model");
        assertTrue(BuiltInTextures.png(name).isPresent(), "the jar lookup must find " + name);
    }

    @Test
    void texturesCarryTheAuthorsCredit() throws IOException {
        String credits = Files.readString(ASSETS.resolve("textures/item/CREDITS.md"));
        assertTrue(credits.contains("Weapon textures by HanhanPx — https://hanhanpx.itch.io/minecraft-weapon-pack"), credits);
        for (String name : BuiltInTextures.NAMES) {
            assertTrue(credits.contains(name + ".png"), "CREDITS.md does not list " + name);
        }
    }
}
