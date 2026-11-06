package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The one place that changes weapons while the game runs: the reload command, the web page's save, upload and
 * texture upload all come here, so files and game always agree (decision O2: the file is written first, and
 * only a successful write changes the game).
 * <p>
 * Plain Java; the Forge adapter runs it on the server thread and then recomputes the holders.
 */
public final class WeaponEditor {
    private final EquipmentLibrary library;
    private final WeaponStore store;
    private final WeaponLoader loader;

    public WeaponEditor(EquipmentLibrary library, WeaponStore store, WeaponLoader loader) {
        this.library = Objects.requireNonNull(library, "library");
        this.store = Objects.requireNonNull(store, "store");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public EquipmentLibrary library() {
        return library;
    }

    public WeaponStore store() {
        return store;
    }

    public WeaponLoader loader() {
        return loader;
    }

    /**
     * Reads every file again and replaces the definitions (H3). All or nothing (B6, decision O8).
     *
     * @throws WeaponLoadException if any file has a problem; nothing changes
     */
    public synchronized ReloadReport reload() throws IOException, WeaponLoadException {
        List<EquipmentDefinition> loaded = loader.load(store);
        try {
            return library.replaceAll(loaded);
        } catch (IllegalArgumentException e) {
            throw new WeaponLoadException(List.of(e.getMessage()));
        }
    }

    /**
     * Saves a changed weapon (H5): checks it, writes its file, then puts it into effect. The later of two saves wins
     * (decision O10).
     *
     * @throws NoSuchElementException if the weapon does not exist (404)
     * @throws WeaponFormatException  if the change is not valid, naming the field (B9, B10; 400)
     * @throws IOException            if the file cannot be written; the game is not changed (B12)
     */
    public synchronized void update(EquipmentDefinition changed) throws IOException, WeaponFormatException {
        EquipmentId id = changed.id();
        EquipmentDefinition current = library.current().find(id)
            .orElseThrow(() -> new NoSuchElementException("Unknown equipment '" + id + "'"));
        if (current.slot() != changed.slot()) {
            throw new WeaponFormatException("slot", "can only change after a restart (now " + current.slot().name().toLowerCase(Locale.ROOT) + ")");
        }
        checkLook(changed.look());
        store.writeWeapon(id, WeaponJson.write(changed));
        library.update(changed);
    }

    /**
     * Saves a new weapon (H9). It becomes an item after a restart (platform limit).
     *
     * @throws WeaponFormatException if the id is taken or the weapon is not valid
     */
    public synchronized void create(EquipmentDefinition definition) throws IOException, WeaponFormatException {
        if (library.isRegistered(definition.id()) || library.pending().containsKey(definition.id())) {
            throw new WeaponFormatException("id", "'" + definition.id() + "' already exists");
        }
        checkLook(definition.look());
        store.writeWeapon(definition.id(), WeaponJson.write(definition));
        library.addPending(definition);
    }

    /**
     * Saves an uploaded texture as {@code textures/<name>.png}. Replacing a texture in use changes its look at
     * the next resource reload.
     *
     * @throws WeaponFormatException if the name or the image is not usable (B4)
     */
    public synchronized void saveTexture(String name, byte[] png) throws IOException, WeaponFormatException {
        try {
            EquipmentLook.texture(name);
        } catch (IllegalArgumentException e) {
            throw new WeaponFormatException("name", e.getMessage());
        }
        if (BuiltInTextures.contains(name)) {
            throw new WeaponFormatException("name", "'" + name + "' is a built-in texture; pick another name");
        }
        Optional<String> problem = PngCheck.problem(png);
        if (problem.isPresent()) {
            throw new WeaponFormatException("file", "the image " + problem.get());
        }
        store.writeTexture(name, png);
    }

    private void checkLook(EquipmentLook look) throws IOException, WeaponFormatException {
        Optional<String> problem = loader.lookProblem(look, store);
        if (problem.isPresent()) {
            String text = problem.get();
            int colon = text.indexOf(": ");
            throw new WeaponFormatException(text.substring(0, colon), text.substring(colon + 2));
        }
    }
}
