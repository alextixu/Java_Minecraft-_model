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
import io.github.alextixu.mobarmory.forge.client.ClientResourceReload;
import io.github.alextixu.mobarmory.web.WeaponAdmin;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Connects {@link WeaponEditor} to the running game, for the reload command and the web page (decision Q2 A):
 * <ol>
 *   <li>runs every change on the server thread (the web server calls from its own threads); the queue keeps
 *       running while a single-player game is paused, so saving from the browser works then too (B15);</li>
 *   <li>then recomputes every entity holding a changed piece (R2 decision Q6);</li>
 *   <li>and if a look changed, reloads the client's resources on this machine (single player, decision O11).</li>
 * </ol>
 */
public final class ServerWeaponAdmin implements WeaponAdmin {
    private static final List<String> LOOK_SUFFIXES = List.of("_sword", "_axe", "_pickaxe", "_shovel", "_hoe", "_spear",
        "_helmet", "_chestplate", "_leggings", "_boots");
    private static final List<String> LOOK_EXTRAS = List.of("minecraft:trident", "minecraft:mace", "minecraft:bow",
        "minecraft:crossbow", "minecraft:shield", "minecraft:stick", "minecraft:blaze_rod", "minecraft:bone");
    private static final long TIMEOUT_SECONDS = 10;

    private final WeaponEditor editor;
    private final EquipmentStatApplier applier;

    public ServerWeaponAdmin(WeaponEditor editor, EquipmentStatApplier applier) {
        this.editor = editor;
        this.applier = applier;
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

    @Override
    public List<String> textures() throws IOException {
        return editor.store().textureNames();
    }

    @Override
    public Optional<byte[]> texture(String name) throws IOException {
        return editor.store().texture(name);
    }

    @Override
    public long update(EquipmentDefinition changed, long expectedVersion) throws IOException, WeaponFormatException {
        return change(() -> {
            EquipmentLook before = editor.library().current().get(changed.id()).look();
            long version = editor.update(changed, expectedVersion);
            afterChange(true, !before.equals(changed.look()));
            return version;
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
    public void saveTexture(String name, byte[] png) throws IOException, WeaponFormatException {
        change(() -> {
            editor.saveTexture(name, png);
            boolean inUse = editor.library().current().all().stream()
                .anyMatch(definition -> definition.look().equals(EquipmentLook.texture(name)));
            afterChange(false, inUse);
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
        if (statsChanged && server != null) {
            applier.refreshHolders(server);
        }
        if (looksChanged) {
            ClientResourceReload.requestIfClient();
        }
    }

    @FunctionalInterface
    private interface Action<T> {
        T run() throws IOException, WeaponFormatException, WeaponLoadException;
    }

    private record Outcome<T>(T value, Exception error) {
    }

    /** A change that never reads all files, so it cannot fail with a load problem. */
    private <T> T change(Action<T> action) throws IOException, WeaponFormatException {
        try {
            return onServerThread(action);
        } catch (WeaponLoadException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Runs on the server thread and waits for the answer; directly if already there (the reload command, game tests). */
    private <T> T onServerThread(Action<T> action) throws IOException, WeaponFormatException, WeaponLoadException {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.isSameThread()) {
            return action.run();
        }
        CompletableFuture<Outcome<T>> future = server.submit(() -> {
            try {
                return new Outcome<>(action.run(), null);
            } catch (Exception e) {
                return new Outcome<>(null, e);
            }
        });
        Outcome<T> outcome;
        try {
            outcome = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("the game did not answer within " + TIMEOUT_SECONDS + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for the game");
        } catch (ExecutionException e) {
            throw new IOException("the game failed to apply the change: " + e.getCause(), e.getCause());
        }
        if (outcome.error() == null) {
            return outcome.value();
        }
        switch (outcome.error()) {
            case IOException e -> throw e;
            case WeaponFormatException e -> throw e;
            case WeaponLoadException e -> throw e;
            case RuntimeException e -> throw e;
            default -> throw new IOException(outcome.error());
        }
    }
}
