package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * The game test server's config folder, {@code src/gametest/config/mobarmory/}, which build.gradle copies into
 * {@code run-gametest/config} (section E, R4b O20). Unit tests that read a file from it use the stats that server has,
 * exactly as the game does: the seven built-in ones plus the stat files there ({@code gametest_jump},
 * {@code gametest_luck}), read by the same {@link StatLoader}. So the test-only goblin can keep using a stat that exists
 * only as a JSON file (review-05 S2) and the unit tests still read it (round 12).
 */
public final class GameTestConfig {
    public static final Path ROOT = Path.of("src/gametest/config/mobarmory");
    /** A mob that exists only as a JSON file and uses a stat that exists only as a JSON file (R4b T2, review-05 S2). */
    public static final Path GOBLIN = ROOT.resolve("mobs/goblin.json");
    private static final StatCatalog STATS = load();

    private GameTestConfig() {
    }

    /** Every stat on the game test server: the built-in ones, then the stat files in {@link #ROOT}. */
    public static StatCatalog stats() {
        return STATS;
    }

    private static StatCatalog load() {
        try {
            return new StatLoader(ModStats.CATALOG).load(new StatFolder(ROOT)).catalog();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (StatLoadException e) {
            throw new IllegalStateException(ROOT + ": " + e.getMessage(), e);
        }
    }
}
