package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.data.WeaponEditor;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import io.github.alextixu.mobarmory.data.WeaponLoadException;
import io.github.alextixu.mobarmory.data.BuiltInTextures;
import io.github.alextixu.mobarmory.web.WeaponAdmin;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Connects {@link WeaponEditor} to the running game, for the reload command and the web page (decision Q2 A):
 * <ol>
 *   <li>runs every change on the server thread (the web server calls from its own threads); the queue keeps
 *       running while a single-player game is paused, so saving from the browser works then too (B15);</li>
 *   <li>then recomputes every entity holding a changed piece (R2 decision Q6), updates the look they show, and sends
 *       the new definitions and any uploaded PNG to every player (decision Q3 B), so remote clients show the same names,
 *       tooltips and textures.</li>
 * </ol>
 */
public final class ServerWeaponAdmin implements WeaponAdmin {
    private static final List<String> LOOK_SUFFIXES = List.of("_sword", "_axe", "_pickaxe", "_shovel", "_hoe", "_spear",
        "_helmet", "_chestplate", "_leggings", "_boots");
    private static final List<String> LOOK_EXTRAS = List.of("minecraft:trident", "minecraft:mace", "minecraft:bow",
        "minecraft:crossbow", "minecraft:shield", "minecraft:stick", "minecraft:blaze_rod", "minecraft:bone");

    private final WeaponEditor editor;
    private final EquipmentStatApplier applier;
    private final WeaponSyncChannel sync;

    public ServerWeaponAdmin(WeaponEditor editor, EquipmentStatApplier applier, WeaponSyncChannel sync) {
        this.editor = editor;
        this.applier = applier;
        this.sync = sync;
    }

    @Override
    public StatCatalog stats() {
        return editor.library().stats();
    }

    @Override
    public EquipmentLibrary.View view() {
        return editor.library().view();
    }

    @Override
    public List<String> vanillaLooks() {
        return BuiltInRegistries.ITEM.keySet().stream()
            .filter(id -> id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE))
            .map(Identifier::toString)
            .filter(id -> LOOK_EXTRAS.contains(id) || LOOK_SUFFIXES.stream().anyMatch(id::endsWith))
            .sorted()
            .toList();
    }

    /** The built-in textures, then the uploaded ones. */
    @Override
    public List<String> textures() throws IOException {
        List<String> names = new ArrayList<>(BuiltInTextures.NAMES);
        editor.store().textureNames().stream().filter(name -> !names.contains(name)).forEach(names::add);
        return names;
    }

    @Override
    public Optional<byte[]> texture(String name) throws IOException {
        return BuiltInTextures.contains(name) ? BuiltInTextures.png(name) : editor.store().texture(name);
    }

    @Override
    public void update(EquipmentDefinition changed) throws IOException, WeaponFormatException {
        change(() -> {
            EquipmentLook before = editor.library().current().find(changed.id()).map(EquipmentDefinition::look).orElse(null);
            editor.update(changed);
            afterChange(true, !changed.look().equals(before));
            return null;
        });
    }

    @Override
    public void saveTexture(String name, byte[] png) throws IOException, WeaponFormatException {
        change(() -> {
            editor.saveTexture(name, png);
            boolean inUse = editor.library().current().all().stream()
                .anyMatch(definition -> definition.look().equals(EquipmentLook.texture(name)));
            if (inUse) {
                afterChange(false, true); // a replaced PNG: players get the new one, clients reload it
            }
            return null;
        });
    }

    @Override
    public void create(EquipmentDefinition definition) throws IOException, WeaponFormatException {
        change(() -> {
            editor.create(definition);
            return null;
        });
    }

    @Override
    public ReloadReport reload() throws IOException, WeaponLoadException {
        try {
            return onServerThread(() -> {
                Map<EquipmentId, EquipmentLook> before = looks();
                ReloadReport report = editor.reload();
                afterChange(!report.changed().isEmpty(), !looks().equals(before));
                return report;
            });
        } catch (WeaponFormatException e) {
            throw new WeaponLoadException(List.of(e.getMessage())); // reload never throws this; keeps the signature honest
        }
    }

    private Map<EquipmentId, EquipmentLook> looks() {
        Map<EquipmentId, EquipmentLook> looks = new LinkedHashMap<>();
        editor.library().current().all().forEach(definition -> looks.put(definition.id(), definition.look()));
        return looks;
    }

    private void afterChange(boolean statsChanged, boolean looksChanged) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        if (statsChanged) {
            applier.refreshHolders(server);
        }
        if (looksChanged) {
            WeaponLooks.applyToEntities(server, editor.library());
        }
        sync.sendToAll(server);
    }

    @FunctionalInterface
    private interface Action<T> {
        T run() throws IOException, WeaponFormatException, WeaponLoadException;
    }

    /** A change that never reads all files, so it cannot fail with a load problem. */
    private <T> T change(Action<T> action) throws IOException, WeaponFormatException {
        try {
            return onServerThread(action);
        } catch (WeaponLoadException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Runs on the server thread and waits for the answer ({@link ServerThread}, shared with the mob admin). */
    private <T> T onServerThread(Action<T> action) throws IOException, WeaponFormatException, WeaponLoadException {
        try {
            return ServerThread.call(action::run);
        } catch (IOException | WeaponFormatException | WeaponLoadException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
