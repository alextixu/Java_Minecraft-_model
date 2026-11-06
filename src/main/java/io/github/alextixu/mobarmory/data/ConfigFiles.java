package io.github.alextixu.mobarmory.data;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/** File helpers shared by the folders under {@code config/mobarmory/} ({@link WeaponFolder}, {@link StatFolder}). */
final class ConfigFiles {
    private ConfigFiles() {
    }

    /** The regular files in {@code folder} whose name ends with {@code extension}, sorted; empty if there is no folder. */
    static List<Path> list(Path folder, String extension) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(extension))
                .sorted()
                .toList();
        }
    }

    /**
     * Writes a temporary file next to the target, then renames it over the target, so the file holds either the old
     * or the new content, never half of it. Creates the folder if needed.
     */
    static void writeAtomically(Path folder, String fileName, byte[] content) throws IOException {
        Files.createDirectories(folder);
        Path target = folder.resolve(fileName);
        Path temporary = Files.createTempFile(folder, "." + fileName + ".", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
