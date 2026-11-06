package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The weapon files in the game folder (decision Q4 A):
 * <pre>
 * config/mobarmory/
 *   weapons/heavy_sword.json ...   one file per weapon, the file name is the id
 * </pre>
 */
public final class WeaponFolder implements WeaponStore {
    private final Path weapons;

    /** @param root usually {@code config/mobarmory} */
    public WeaponFolder(Path root) {
        this.weapons = root.resolve("weapons");
    }

    public Path weaponsFolder() {
        return weapons;
    }

    @Override
    public boolean exists() {
        return Files.isDirectory(weapons);
    }

    @Override
    public List<WeaponFile> weaponFiles() throws IOException {
        List<WeaponFile> files = new ArrayList<>();
        if (!Files.isDirectory(weapons)) {
            return files;
        }
        for (Path file : list(weapons, ".json")) {
            files.add(new WeaponFile(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8)));
        }
        return files;
    }

    @Override
    public void writeWeapon(EquipmentId id, String json) throws IOException {
        writeAtomically(weapons, id.value() + ".json", json.getBytes(StandardCharsets.UTF_8));
    }

    private static List<Path> list(Path folder, String extension) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(extension))
                .sorted()
                .toList();
        }
    }

    /** Writes a temporary file next to the target, then renames it over the target. */
    private static void writeAtomically(Path folder, String fileName, byte[] content) throws IOException {
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
