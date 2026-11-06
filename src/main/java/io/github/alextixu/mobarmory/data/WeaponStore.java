package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import java.io.IOException;
import java.util.List;

/**
 * Where weapon files live. The game uses {@link WeaponFolder} ({@code config/mobarmory/weapons/}); tests use an
 * in-memory store. Both pass the same contract tests. Textures are not here: they ship in the mod jar
 * ({@link BuiltInTextures}, decision Q5 B).
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
}
