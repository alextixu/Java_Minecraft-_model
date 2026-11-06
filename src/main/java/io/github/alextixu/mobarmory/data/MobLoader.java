package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads every mob file (R4b). All problems are collected, so one message lists every file to fix (like the weapons'
 * {@link WeaponLoader}). Whether a mob's weapons exist is not checked here: a missing weapon only leaves the mob
 * without it (R4 decision O2, changed to B on 11/07).
 */
public final class MobLoader {
    private final StatCatalog stats;

    /** @param stats every stat in the game, including the ones from stat files (section E) */
    public MobLoader(StatCatalog stats) {
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    public StatCatalog stats() {
        return stats;
    }

    /**
     * @return every mob, sorted by file name
     * @throws MobLoadException if any file has a problem
     */
    public List<MobDefinition> load(MobStore store) throws IOException, MobLoadException {
        List<MobDefinition> loaded = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (MobStore.MobFile file : store.mobFiles()) {
            String fileName = file.fileName();
            MobId id;
            try {
                id = MobId.of(fileName.substring(0, fileName.length() - ".json".length()));
            } catch (IllegalArgumentException e) {
                problems.add(fileName + ": the file name is the mob id; " + e.getMessage());
                continue;
            }
            try {
                loaded.add(MobFileJson.read(id, file.text(), stats));
            } catch (WeaponFormatException e) {
                problems.add(fileName + ": " + e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new MobLoadException(problems);
        }
        return loaded;
    }
}
