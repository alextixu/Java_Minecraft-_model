package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Client resources made from the mob definitions (R4b, decision O14), so a new mob needs no resource file: each spawn
 * egg borrows the vanilla egg of the same body ({@code mobarmory:goblin_spawn_egg} looks like a husk spawn egg).
 * Minecraft's item properties cannot point an item at another model, so the runtime resource pack provides
 * {@code items/<id>_spawn_egg.json}.
 */
public final class MobAssets {
    private MobAssets() {
    }

    /** Path inside the pack's namespace → file content. */
    public static Map<String, String> spawnEggModels(Collection<MobDefinition> mobs) {
        Map<String, String> files = new LinkedHashMap<>();
        for (MobDefinition mob : mobs) {
            files.put("items/" + mob.id().value() + "_spawn_egg.json", spawnEggModel(mob));
        }
        return files;
    }

    /** The vanilla spawn egg model of the mob's body, e.g. {@code minecraft:item/husk_spawn_egg}. */
    public static String vanillaEggModel(MobDefinition mob) {
        return "minecraft:item/" + mob.look().body().name().toLowerCase(Locale.ROOT) + "_spawn_egg";
    }

    private static String spawnEggModel(MobDefinition mob) {
        return "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"" + vanillaEggModel(mob) + "\"\n  }\n}\n";
    }
}
