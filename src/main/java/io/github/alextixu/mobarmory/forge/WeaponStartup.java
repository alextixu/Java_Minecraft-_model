package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.data.DefaultWeapons;
import io.github.alextixu.mobarmory.data.WeaponLoadException;
import io.github.alextixu.mobarmory.data.WeaponLoader;
import io.github.alextixu.mobarmory.data.WeaponStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Loads the weapon files when the mod is constructed, before items are registered (R2b, decision Q4 A).
 * A wrong file stops the game with a message naming the file (decision O7): skipping it would make that kind of
 * item vanish from every world.
 */
final class WeaponStartup {
    private WeaponStartup() {
    }

    /** First start: installs the defaults; then reads every file. Vanilla looks are checked later, see {@link #verifyLooks}. */
    static EquipmentLibrary load(WeaponStore store, StatCatalog stats) {
        try {
            DefaultWeapons.installIfMissing(store);
            List<EquipmentDefinition> loaded = new WeaponLoader(stats, item -> true).load(store);
            EquipmentCatalog.Builder catalog = EquipmentCatalog.builder(stats);
            loaded.forEach(catalog::add);
            return new EquipmentLibrary(catalog.build());
        } catch (WeaponLoadException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Mob Armory could not read its weapon files", e);
        }
    }

    /** A loader that knows Minecraft's items; only usable once the item registry is complete. */
    static WeaponLoader loader(StatCatalog stats) {
        return new WeaponLoader(stats, WeaponStartup::vanillaItemExists);
    }

    static boolean vanillaItemExists(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return identifier != null && BuiltInRegistries.ITEM.containsKey(identifier);
    }

    /** B4: every borrowed look must be a real item; runs in common setup, when all items are registered. */
    static void verifyLooks(EquipmentLibrary library, WeaponStore store) {
        WeaponLoader loader = loader(library.stats());
        List<String> problems = new ArrayList<>();
        for (EquipmentDefinition definition : library.current().all()) {
            try {
                Optional<String> problem = loader.lookProblem(definition.look(), store);
                problem.ifPresent(text -> problems.add(definition.id() + ".json: " + text));
            } catch (IOException e) {
                problems.add(definition.id() + ".json: " + e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(new WeaponLoadException(problems).getMessage());
        }
    }
}
