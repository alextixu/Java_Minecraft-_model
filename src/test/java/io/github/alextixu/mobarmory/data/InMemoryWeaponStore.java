package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** A {@link WeaponStore} in memory, for tests. Passes the same contract as {@link WeaponFolder}. */
public class InMemoryWeaponStore implements WeaponStore {
    private final Map<String, String> weapons = new TreeMap<>();
    private final Map<String, byte[]> textures = new TreeMap<>();
    private boolean exists;

    /** Puts a file in as-is, even one with a bad name or bad content (the loader must catch those). */
    public InMemoryWeaponStore putFile(String fileName, String text) {
        exists = true;
        weapons.put(fileName, text);
        return this;
    }

    public InMemoryWeaponStore putTexture(String name, byte[] png) {
        textures.put(name, png.clone());
        return this;
    }

    public void deleteFile(String fileName) {
        weapons.remove(fileName);
    }

    public Optional<String> file(String fileName) {
        return Optional.ofNullable(weapons.get(fileName));
    }

    @Override
    public boolean exists() {
        return exists;
    }

    @Override
    public List<WeaponFile> weaponFiles() {
        List<WeaponFile> files = new ArrayList<>();
        weapons.forEach((name, text) -> {
            if (name.endsWith(".json")) {
                files.add(new WeaponFile(name, text));
            }
        });
        return files;
    }

    @Override
    public void writeWeapon(EquipmentId id, String json) {
        putFile(id.value() + ".json", json);
    }

    @Override
    public Optional<byte[]> texture(String name) {
        EquipmentLook.texture(name);
        return Optional.ofNullable(textures.get(name)).map(byte[]::clone);
    }

    @Override
    public void writeTexture(String name, byte[] png) {
        EquipmentLook.texture(name);
        putTexture(name, png);
    }

    @Override
    public List<String> textureNames() {
        return List.copyOf(textures.keySet());
    }
}
