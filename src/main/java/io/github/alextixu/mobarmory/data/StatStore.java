package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.stat.StatId;
import java.io.IOException;
import java.util.List;

/**
 * Where stat files live (section E). The game uses {@link StatFolder} ({@code config/mobarmory/stats/}); tests use an
 * in-memory store. Both pass the same contract tests, like {@link WeaponStore}.
 */
public interface StatStore {
    /** One stat file. */
    record StatFile(String fileName, String text) {
    }

    /** True once the stats folder exists. On the very first start it does not, and the built-in stats are written. */
    boolean exists() throws IOException;

    /** Every {@code *.json} file, sorted by file name; other files are ignored (O4). */
    List<StatFile> statFiles() throws IOException;

    /** Writes {@code <id>.json} all or nothing. Creates the folder if needed. */
    void writeStat(StatId id, String json) throws IOException;
}
