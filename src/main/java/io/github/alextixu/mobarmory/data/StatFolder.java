package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.stat.StatId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The stat files in the game folder (section E):
 * <pre>
 * config/mobarmory/stats/attack_damage.json ...   one file per stat, the file name is the id
 * </pre>
 */
public final class StatFolder implements StatStore {
    private final Path stats;

    /** @param root usually {@code config/mobarmory} */
    public StatFolder(Path root) {
        this.stats = root.resolve("stats");
    }

    public Path statsFolder() {
        return stats;
    }

    @Override
    public boolean exists() {
        return Files.isDirectory(stats);
    }

    @Override
    public List<StatFile> statFiles() throws IOException {
        List<StatFile> files = new ArrayList<>();
        for (Path file : ConfigFiles.list(stats, ".json")) {
            files.add(new StatFile(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8)));
        }
        return files;
    }

    @Override
    public void writeStat(StatId id, String json) throws IOException {
        ConfigFiles.writeAtomically(stats, id.value() + ".json", json.getBytes(StandardCharsets.UTF_8));
    }
}
