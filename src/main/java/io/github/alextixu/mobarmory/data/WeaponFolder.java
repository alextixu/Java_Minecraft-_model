package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The weapon files in the game folder (decision Q4 A):
 * <pre>
 * config/mobarmory/
 *   weapons/heavy_sword.json ...   one file per weapon, the file name is the id
 *   textures/halberd_netherite.png ...
 * </pre>
 * The weapon files are a {@link JsonFolder}, listed and written the same way as every other kind of data file
 * (combos, R3).
 */
public final class WeaponFolder implements WeaponStore {
    private final JsonFolder weapons;
    private final Path textures;

    /** @param root usually {@code config/mobarmory} */
    public WeaponFolder(Path root) {
        this.weapons = new JsonFolder(root.resolve("weapons"));
        this.textures = root.resolve("textures");
    }

    public Path weaponsFolder() {
        return weapons.path();
    }

    public Path texturesFolder() {
        return textures;
    }

    @Override
    public boolean exists() {
        return weapons.exists();
    }

    @Override
    public List<WeaponFile> weaponFiles() throws IOException {
        List<WeaponFile> files = new ArrayList<>();
        for (JsonFolder.DataFile file : weapons.files()) {
            files.add(new WeaponFile(file.fileName(), file.text()));
        }
        return files;
    }

    @Override
    public void writeWeapon(EquipmentId id, String json) throws IOException {
        weapons.write(id.value(), json);
    }

    @Override
    public Optional<byte[]> texture(String name) throws IOException {
        Path file = textures.resolve(checkedTextureName(name) + ".png");
        return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
    }

    @Override
    public void writeTexture(String name, byte[] png) throws IOException {
        JsonFolder.writeAtomically(textures, checkedTextureName(name) + ".png", png);
    }

    @Override
    public List<String> textureNames() throws IOException {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(textures)) {
            return names;
        }
        for (Path file : JsonFolder.list(textures, ".png")) {
            String name = file.getFileName().toString();
            names.add(name.substring(0, name.length() - ".png".length()));
        }
        return names;
    }

    /** Only names that pass {@link EquipmentLook.Texture} reach the file system, so no path can escape the folder. */
    private static String checkedTextureName(String name) {
        return ((EquipmentLook.Texture) EquipmentLook.texture(name)).texture();
    }
}
