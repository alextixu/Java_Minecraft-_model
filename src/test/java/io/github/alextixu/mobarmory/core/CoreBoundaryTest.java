package io.github.alextixu.mobarmory.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the change boundary from 01-boundary-note.md: {@code core/} and {@code content/} are plain Java.
 * If someone imports Minecraft or Forge there, the core can no longer be tested without the game.
 */
class CoreBoundaryTest {
    private static final Path SOURCES = Path.of("src/main/java/io/github/alextixu/mobarmory");

    @ParameterizedTest(name = "{0}/ 不 import Minecraft 或 Forge")
    @ValueSource(strings = {"core", "content"})
    void layerHasNoGameImports(String layer) throws IOException {
        Path dir = SOURCES.resolve(layer);
        if (!Files.isDirectory(dir)) {
            return; // layer not created yet
        }
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("import net.minecraft") || trimmed.startsWith("import com.mojang")) {
                        offenders.add(SOURCES.relativize(file) + ": " + trimmed);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Game imports found in " + layer + "/: " + offenders);
    }
}
