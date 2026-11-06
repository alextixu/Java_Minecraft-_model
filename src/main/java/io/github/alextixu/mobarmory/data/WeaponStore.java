package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Where weapon files and textures live. The game uses {@link WeaponFolder} ({@code config/mobarmory/});
 * tests use an in-memory store. Both pass the same contract tests.
 */
public interface WeaponStore {

    /** One weapon file. */
    record WeaponFile(String fileName, String text) {
    }

    /** True once the weapons folder exists. On the very first start it does not, and the defaults are installed. */
    boolean exists() throws IOException;

    /** Every {@code *.json} weapon file, sorted by file name. */
    List<WeaponFile> weaponFiles() throws IOException;

    /**
     * Writes one weapon file so that it holds either the old or the new content, never half of it (decision O2).
     * Creates the folder if needed.
     */
    void writeWeapon(EquipmentId id, String json) throws IOException;

    /** The PNG named {@code <name>.png}, if there is one. */
    Optional<byte[]> texture(String name) throws IOException;

    /** Writes {@code <name>.png} the same all-or-nothing way. The name must be a valid texture name. */
    void writeTexture(String name, byte[] png) throws IOException;

    /** Names of all textures (file names without {@code .png}), sorted. */
    List<String> textureNames() throws IOException;
}
