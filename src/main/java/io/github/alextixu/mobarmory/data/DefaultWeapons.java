package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The prototype's four pieces (R2 decision O7), shipped inside the mod as weapon files in
 * {@code mobarmory/default_weapons/}. On the very first start they are copied to the config folder (H1); after that
 * the config folder is the only source, and the game never overwrites it.
 */
public final class DefaultWeapons {
    public static final List<String> IDS = List.of("heavy_sword", "dagger", "evasion_chestplate", "heavy_armor");

    private static final String FOLDER = "/mobarmory/default_weapons/";

    private DefaultWeapons() {
    }

    /** The shipped file content for one default piece. */
    public static String text(String id) {
        try (InputStream in = DefaultWeapons.class.getResourceAsStream(FOLDER + id + ".json")) {
            if (in == null) {
                throw new IllegalStateException("The mod is missing " + FOLDER + id + ".json");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Copies the defaults into the store if its weapons folder does not exist yet (the very first start).
     * An existing folder is never touched, even if the player deleted every file in it.
     *
     * @return true if the defaults were installed
     */
    public static boolean installIfMissing(WeaponStore store) throws IOException {
        if (store.exists()) {
            return false;
        }
        for (String id : IDS) {
            store.writeWeapon(EquipmentId.of(id), text(id));
        }
        return true;
    }

    /** The defaults as a catalog, read through the same format rules as the files (for tests). */
    public static EquipmentCatalog catalog(StatCatalog stats) {
        EquipmentCatalog.Builder builder = EquipmentCatalog.builder(stats);
        for (String id : IDS) {
            try {
                builder.add(WeaponJson.read(EquipmentId.of(id), text(id), stats));
            } catch (WeaponFormatException e) {
                throw new IllegalStateException("Shipped default " + id + ".json: " + e.getMessage(), e);
            }
        }
        return builder.build();
    }
}
