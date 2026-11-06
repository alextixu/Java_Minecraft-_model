package io.github.alextixu.mobarmory.web;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.data.DefaultWeapons;
import io.github.alextixu.mobarmory.data.InMemoryWeaponStore;
import io.github.alextixu.mobarmory.data.WeaponEditor;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import io.github.alextixu.mobarmory.data.WeaponLoadException;
import io.github.alextixu.mobarmory.data.WeaponLoader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** {@link WeaponAdmin} without the game: the real editor over an in-memory store holding the four defaults. */
final class TestWeaponAdmin implements WeaponAdmin {
    final InMemoryWeaponStore store = new InMemoryWeaponStore();
    final EquipmentLibrary library;
    private final WeaponEditor editor;

    TestWeaponAdmin() {
        try {
            DefaultWeapons.installIfMissing(store);
            WeaponLoader loader = new WeaponLoader(ModStats.CATALOG, item -> item.startsWith("minecraft:") && !item.contains("laser"));
            EquipmentCatalog.Builder catalog = EquipmentCatalog.builder(ModStats.CATALOG);
            loader.load(store).forEach(catalog::add);
            library = new EquipmentLibrary(catalog.build());
            editor = new WeaponEditor(library, store, loader);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public StatCatalog stats() {
        return ModStats.CATALOG;
    }

    @Override
    public EquipmentLibrary.View view() {
        return library.view();
    }

    @Override
    public List<String> vanillaLooks() {
        return List.of("minecraft:iron_sword", "minecraft:netherite_sword", "minecraft:trident");
    }

    @Override
    public List<String> textures() {
        return store.textureNames();
    }

    @Override
    public Optional<byte[]> texture(String name) {
        return store.texture(name);
    }

    @Override
    public long update(EquipmentDefinition changed, long expectedVersion) throws IOException, WeaponFormatException {
        return editor.update(changed, expectedVersion);
    }

    @Override
    public void create(EquipmentDefinition definition) throws IOException, WeaponFormatException {
        editor.create(definition);
    }

    @Override
    public void saveTexture(String name, byte[] png) throws IOException, WeaponFormatException {
        editor.saveTexture(name, png);
    }

    @Override
    public ReloadReport reload() throws IOException, WeaponLoadException {
        return editor.reload();
    }
}
