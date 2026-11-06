package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The mobs the game starts with (R4b): every file in the mobs folder, checked as at a reload, plus one rule that only
 * matters at startup (B10, O18): a mob's spawn egg is the item {@code <id>_spawn_egg}, which must not be a weapon's id
 * too. Plain Java, so it is tested without the game; the Forge side ({@code forge/MobStartup}) installs the defaults
 * first and stops the game on a problem (O2).
 */
public final class MobSetup {
    private MobSetup() {
    }

    /**
     * @param stats     every stat in the game (section E), so a mob file may set stats players added too
     * @param equipment the weapons, for the spawn egg rule
     * @throws MobLoadException naming every file with a problem
     */
    public static MobLibrary read(MobStore store, StatCatalog stats, EquipmentLibrary equipment) throws IOException, MobLoadException {
        List<MobDefinition> loaded = new MobLoader(stats).load(store);
        List<String> problems = new ArrayList<>();
        for (MobDefinition mob : loaded) {
            String egg = mob.id().value() + "_spawn_egg";
            if (equipment.isRegistered(EquipmentId.of(egg))) {
                problems.add(mob.id() + ".json: its spawn egg's item id '" + egg + "' is already the weapon " + egg
                    + ".json; rename one of the two files");
            }
        }
        if (!problems.isEmpty()) {
            throw new MobLoadException(problems);
        }
        return new MobLibrary(MobCatalog.builder(stats).addAll(loaded).build());
    }
}
