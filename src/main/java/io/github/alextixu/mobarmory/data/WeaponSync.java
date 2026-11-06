package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the server tells each client about weapons (decision Q3 B, review-01 risk 4): the definitions in effect, in the
 * same JSON as the files, plus the PNG of every uploaded texture they use. A remote client puts them into its own
 * library and its runtime resource pack, so names, tooltips and textures match the server. Numbers in play already
 * match, because Minecraft syncs the attributes itself.
 */
public final class WeaponSync {
    private WeaponSync() {
    }

    /** Every weapon in effect, as id → weapon JSON. */
    public static Map<String, String> snapshot(EquipmentLibrary library) {
        Map<String, String> weapons = new LinkedHashMap<>();
        for (EquipmentDefinition definition : library.current().all()) {
            weapons.put(definition.id().value(), WeaponJson.write(definition));
        }
        return weapons;
    }

    /** The uploaded (not built-in) textures the weapons in effect use, as name → PNG. */
    public static Map<String, byte[]> uploadedTexturesInUse(EquipmentLibrary library, WeaponStore store) throws IOException {
        Map<String, byte[]> textures = new LinkedHashMap<>();
        for (EquipmentDefinition definition : library.current().all()) {
            if (definition.look() instanceof EquipmentLook.Texture texture && !BuiltInTextures.contains(texture.texture())
                && !textures.containsKey(texture.texture())) {
                Optional<byte[]> png = store.texture(texture.texture());
                png.ifPresent(bytes -> textures.put(texture.texture(), bytes));
            }
        }
        return textures;
    }

    /** SHA-256 of each texture, so a client can tell which ones it already has. */
    public static Map<String, String> digests(Map<String, byte[]> textures) {
        Map<String, String> digests = new LinkedHashMap<>();
        textures.forEach((name, png) -> digests.put(name, digest(png)));
        return digests;
    }

    public static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    /** True if the client must reload its resources to show every texture the server uses. */
    public static boolean needsReload(Map<String, String> serverDigests, Map<String, String> clientDigests) {
        return serverDigests.entrySet().stream().anyMatch(entry -> !entry.getValue().equals(clientDigests.get(entry.getKey())));
    }

    /**
     * On a remote client: replaces its definitions with the server's. Weapons this client does not have as items
     * are ignored (they could not have joined otherwise); all or nothing, like a reload.
     *
     * @throws WeaponFormatException if the server sent something this version cannot read; nothing changes
     */
    public static ReloadReport apply(EquipmentLibrary library, Map<String, String> sent) throws WeaponFormatException {
        List<EquipmentDefinition> definitions = new ArrayList<>();
        for (Map.Entry<String, String> entry : sent.entrySet()) {
            EquipmentId id;
            try {
                id = EquipmentId.of(entry.getKey());
            } catch (IllegalArgumentException e) {
                throw new WeaponFormatException("id", e.getMessage());
            }
            definitions.add(WeaponJson.read(id, entry.getValue(), library.stats()));
        }
        try {
            return library.replaceAll(definitions);
        } catch (IllegalArgumentException e) {
            throw new WeaponFormatException("", e.getMessage());
        }
    }
}
