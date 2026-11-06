package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.stat.StatId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The stat files in the game folder (section E):
 * <pre>
 * config/mobarmory/stats/attack_damage.json ...   one file per stat, the file name is the id
 * </pre>
 * The folder is a {@link JsonFolder}, listed and written the same way as the weapon and combo files.
 */
public final class StatFolder implements StatStore {
    private final JsonFolder stats;

    /** @param root usually {@code config/mobarmory} */
    public StatFolder(Path root) {
        this.stats = new JsonFolder(root.resolve("stats"));
    }

    public Path statsFolder() {
        return stats.path();
    }

    @Override
    public boolean exists() {
        return stats.exists();
    }

    /** Empty when the folder does not exist yet. */
    @Override
    public List<StatFile> statFiles() throws IOException {
        List<StatFile> files = new ArrayList<>();
        for (JsonFolder.DataFile file : stats.files()) {
            files.add(new StatFile(file.fileName(), file.text()));
        }
        return files;
    }

    @Override
    public void writeStat(StatId id, String json) throws IOException {
        stats.write(id.value(), json);
    }
}
