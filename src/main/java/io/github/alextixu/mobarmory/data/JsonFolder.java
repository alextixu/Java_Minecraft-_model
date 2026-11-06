package io.github.alextixu.mobarmory.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * One folder of {@code <id>.json} data files in the game folder: {@code config/mobarmory/weapons/},
 * {@code stats/} and {@code combos/}. Every kind of data file is listed, read and written the same way: sorted by file
 * name, read as UTF-8, and written all or nothing (R2b decision O2), so a crash never leaves half a file. This is the
 * one place with those file details.
 */
public final class JsonFolder {
    /**
     * File ids that can never escape the folder, the same rule as {@code EquipmentId}, {@code StatId} and combo ids:
     * a lowercase letter, then lowercase letters, digits and underscores.
     */
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]*");

    private final Path folder;

    public JsonFolder(Path folder) {
        this.folder = Objects.requireNonNull(folder, "folder");
    }

    /** One file: its name ({@code lunge.json}) and its content. */
    public record DataFile(String fileName, String text) {
        /** The file name without {@code .json}. */
        public String id() {
            return fileName.substring(0, fileName.length() - ".json".length());
        }
    }

    public Path path() {
        return folder;
    }

    /** False until the folder is created; on the very first start the defaults are installed then. */
    public boolean exists() {
        return Files.isDirectory(folder);
    }

    /** Every {@code *.json} file, sorted by file name; none if the folder does not exist. */
    public List<DataFile> files() throws IOException {
        List<DataFile> files = new ArrayList<>();
        for (Path file : list(folder, ".json")) {
            files.add(new DataFile(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8)));
        }
        return files;
    }

    /** Writes {@code <id>.json} all or nothing, creating the folder if needed. */
    public void write(String id, String json) throws IOException {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("not a valid file id: '" + id + "'");
        }
        writeAtomically(folder, id + ".json", json.getBytes(StandardCharsets.UTF_8));
    }

    /** Regular files in a folder with this extension, sorted by name; none if the folder does not exist. */
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

    /** Writes a temporary file next to the target, then renames it over the target. */
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
