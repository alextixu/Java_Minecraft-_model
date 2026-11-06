package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.data.StatFolder;
import io.github.alextixu.mobarmory.data.StatLoadException;
import io.github.alextixu.mobarmory.data.StatLoader;
import io.github.alextixu.mobarmory.data.StatSetup;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.slf4j.Logger;

/**
 * Reads the stat files when the mod is constructed, before attributes are registered (section E). Same shape as
 * {@link WeaponStartup}: a wrong file stops the game with a message naming the file (Q9 A), because the attribute
 * registry cannot change once the game runs (Q8 A).
 */
final class StatStartup {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StatStartup() {
    }

    /** First start: writes the built-in stats (O1); then reads every file. */
    static StatSetup load(StatFolder folder, StatCatalog builtIns) {
        StatLoader loader = new StatLoader(builtIns);
        try {
            if (loader.installIfMissing(folder)) {
                LOGGER.info("Mob Armory: wrote the {} built-in stats to {}", builtIns.size(), folder.statsFolder());
            }
            StatSetup setup = loader.load(folder);
            if (!setup.builtInsFromCode().isEmpty()) {
                LOGGER.warn("Mob Armory: no file in {} for the built-in stat(s) {}; using the built-in values. "
                    + "Delete the folder to get every file back.", folder.statsFolder(), setup.builtInsFromCode());
            }
            return setup;
        } catch (StatLoadException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Mob Armory could not read its stat files in " + folder.statsFolder(), e);
        }
    }
}
