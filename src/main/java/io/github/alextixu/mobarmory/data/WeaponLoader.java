package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reads every weapon file in a store and checks it: the format ({@link WeaponJson}), the file name, and that the
 * look exists (a vanilla item Minecraft knows, or a usable PNG). All problems are collected so one message lists
 * everything to fix.
 */
public final class WeaponLoader {
    private final StatCatalog stats;
    private final Predicate<String> vanillaItemExists;

    /**
     * @param stats             registered stats
     * @param vanillaItemExists whether Minecraft has an item with this id; the game passes its item registry
     */
    public WeaponLoader(StatCatalog stats, Predicate<String> vanillaItemExists) {
        this.stats = Objects.requireNonNull(stats, "stats");
        this.vanillaItemExists = Objects.requireNonNull(vanillaItemExists, "vanillaItemExists");
    }

    public StatCatalog stats() {
        return stats;
    }

    /**
     * @return every weapon, sorted by file name
     * @throws WeaponLoadException if any file has a problem
     */
    public List<EquipmentDefinition> load(WeaponStore store) throws IOException, WeaponLoadException {
        List<EquipmentDefinition> loaded = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (WeaponStore.WeaponFile file : store.weaponFiles()) {
            String fileName = file.fileName();
            EquipmentId id;
            try {
                id = EquipmentId.of(fileName.substring(0, fileName.length() - ".json".length()));
            } catch (IllegalArgumentException e) {
                problems.add(fileName + ": the file name is the weapon id; " + e.getMessage());
                continue;
            }
            try {
                EquipmentDefinition definition = WeaponJson.read(id, file.text(), stats);
                Optional<String> lookProblem = lookProblem(definition.look(), store);
                if (lookProblem.isPresent()) {
                    problems.add(fileName + ": " + lookProblem.get());
                    continue;
                }
                loaded.add(definition);
            } catch (WeaponFormatException e) {
                problems.add(fileName + ": " + e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new WeaponLoadException(problems);
        }
        return loaded;
    }

    /** What is wrong with a look, as "look.item: ..." or "look.texture: ...", or empty if it can be used (B4). */
    public Optional<String> lookProblem(EquipmentLook look, WeaponStore store) throws IOException {
        return switch (look) {
            case EquipmentLook.VanillaItem item -> vanillaItemExists.test(item.item())
                ? Optional.empty()
                : Optional.of("look.item: Minecraft has no item '" + item.item() + "'");
            case EquipmentLook.Texture texture -> {
                Optional<byte[]> png = store.texture(texture.texture());
                if (png.isEmpty()) {
                    yield Optional.of("look.texture: there is no textures/" + texture.texture() + ".png");
                }
                yield PngCheck.problem(png.get()).map(problem -> "look.texture: " + texture.texture() + ".png " + problem);
            }
        };
    }
}
