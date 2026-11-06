package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Contract tests: the stat folder (game) and the in-memory store (tests) behave the same (section E, Q2 A). */
class StatStoreContractTest {
    @TempDir
    static Path temp;
    private static int folders;

    static Stream<Named<Function<Path, StatStore>>> stores() {
        return Stream.of(
            Named.of("StatFolder", root -> new StatFolder(root.resolve("store" + folders++))),
            Named.of("InMemoryStatStore", root -> new InMemoryStatStore()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stores")
    void startsEmptyAndDoesNotExist(Function<Path, StatStore> factory) throws IOException {
        StatStore store = factory.apply(temp);
        assertFalse(store.exists());
        assertEquals(List.of(), store.statFiles());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stores")
    void readsWhatWasWrittenSortedByName(Function<Path, StatStore> factory) throws IOException {
        StatStore store = factory.apply(temp);
        store.writeStat(StatId.of("luck"), "{\"format\": 1}");
        store.writeStat(StatId.of("armor"), "{}");
        store.writeStat(StatId.of("luck"), "{\"format\": 2}");

        assertTrue(store.exists());
        assertEquals(List.of(new StatStore.StatFile("armor.json", "{}"), new StatStore.StatFile("luck.json", "{\"format\": 2}")),
            store.statFiles());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stores")
    void firstStartWritesEveryBuiltInStatThatReadsBackTheSame(Function<Path, StatStore> factory) throws Exception {
        StatStore store = factory.apply(temp);
        StatLoader loader = new StatLoader(ModStats.CATALOG);

        assertTrue(loader.installIfMissing(store));
        assertEquals(ModStats.CATALOG.size(), store.statFiles().size());
        assertEquals(ModStats.CATALOG.all(), loader.load(store).catalog().all());
        assertFalse(loader.installIfMissing(store), "a folder that exists is left alone");
    }

    @org.junit.jupiter.api.Test
    void folderIgnoresFilesThatAreNotJson() throws IOException {
        StatFolder folder = new StatFolder(temp.resolve("store" + folders++));
        folder.writeStat(StatId.of("luck"), "{}");
        Files.writeString(folder.statsFolder().resolve("notes.txt"), "not a stat");

        assertEquals(List.of(new StatStore.StatFile("luck.json", "{}")), folder.statFiles());
    }
}
