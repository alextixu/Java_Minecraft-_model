package io.github.alextixu.mobarmory.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards N5: the mod jar must not ship game-test registry entries. Minecraft marks every data-pack registry entry
 * that comes from a non-vanilla pack as experimental, so shipping them makes every world show an
 * "experimental settings" warning. Game tests belong in src/gametest, which only the test server loads.
 */
class ShippedDataTest {
    private static final Path MAIN_DATA = Path.of("src/main/resources/data");
    private static final List<String> TEST_ONLY_REGISTRIES = List.of("test_instance", "test_environment");

    @Test
    @DisplayName("N5：正式 jar 的資源裡沒有 test_instance、test_environment（否則每個世界都會跳實驗性設定警告）")
    void mainResourcesHaveNoGameTestEntries() throws IOException {
        if (!Files.isDirectory(MAIN_DATA)) {
            return;
        }
        try (Stream<Path> dirs = Files.walk(MAIN_DATA, 2)) {
            List<Path> offenders = dirs
                .filter(Files::isDirectory)
                .filter(dir -> TEST_ONLY_REGISTRIES.contains(dir.getFileName().toString()))
                .toList();
            assertTrue(offenders.isEmpty(), "move these to src/gametest/resources: " + offenders);
        }
    }

    @Test
    @DisplayName("N5：16 個遊戲內測試實例都在 src/gametest/resources")
    void gameTestInstancesLiveInTheGametestSourceSet() throws IOException {
        Path instances = Path.of("src/gametest/resources/data/mobarmory/test_instance");
        try (Stream<Path> files = Files.list(instances)) {
            long count = files.filter(file -> file.toString().endsWith(".json")).count();
            assertTrue(count >= 16, "expected at least 16 test instances, found " + count);
        }
    }
}
