package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.data.DefaultMobs;
import io.github.alextixu.mobarmory.data.MobFolder;
import io.github.alextixu.mobarmory.data.MobLoadException;
import io.github.alextixu.mobarmory.data.MobSetup;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.slf4j.Logger;

/**
 * Loads the mob files when the mod is constructed, before entity types are registered (R4b). Same shape as
 * {@link WeaponStartup} and {@link StatStartup}: a wrong file stops the game with a message naming the file (decision
 * O2), because skipping it would make that kind of mob vanish from every world. The rules are in {@link MobSetup}.
 */
final class MobStartup {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MobStartup() {
    }

    /** First start: installs the two default mobs (H1, O15); then reads every file. */
    static MobLibrary load(MobFolder folder, StatCatalog stats, EquipmentLibrary equipment) {
        try {
            if (DefaultMobs.installIfMissing(folder)) {
                LOGGER.info("Mob Armory: wrote the {} default mobs to {}", DefaultMobs.IDS.size(), folder.mobsFolder());
            }
            return MobSetup.read(folder, stats, equipment);
        } catch (MobLoadException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Mob Armory could not read its mob files in " + folder.mobsFolder(), e);
        }
    }
}
