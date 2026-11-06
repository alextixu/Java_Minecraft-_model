package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The prototype's two mobs (R4 decision O1), shipped inside the mod as mob files in {@code mobarmory/default_mobs/}
 * (R4b, decision O16; they replace the Java {@code content/ModMobs}). On the very first start they are copied to the
 * config folder (H1); after that the config folder is the only source, and the game never overwrites it (O15).
 */
public final class DefaultMobs {
    public static final List<String> IDS = List.of("brute", "skirmisher");
    public static final MobId BRUTE = MobId.of("brute");
    public static final MobId SKIRMISHER = MobId.of("skirmisher");

    private static final String FOLDER = "/mobarmory/default_mobs/";

    private DefaultMobs() {
    }

    /** The shipped file content for one default mob. */
    public static String text(String id) {
        try (InputStream in = DefaultMobs.class.getResourceAsStream(FOLDER + id + ".json")) {
            if (in == null) {
                throw new IllegalStateException("The mod is missing " + FOLDER + id + ".json");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Copies the defaults into the store if its mobs folder does not exist yet (the very first start). An existing
     * folder is never touched, even if the player deleted every file in it (O15).
     *
     * @return true if the defaults were installed
     */
    public static boolean installIfMissing(MobStore store) throws IOException {
        if (store.exists()) {
            return false;
        }
        for (String id : IDS) {
            store.writeMob(MobId.of(id), text(id));
        }
        return true;
    }

    /** One default mob, read through the same format rules as the files. */
    public static MobDefinition definition(MobId id, StatCatalog stats) {
        try {
            return MobFileJson.read(id, text(id.value()), stats);
        } catch (WeaponFormatException e) {
            throw new IllegalStateException("Shipped default " + id + ".json: " + e.getMessage(), e);
        }
    }

    /** The defaults as a catalog (for tests and the first start). */
    public static MobCatalog catalog(StatCatalog stats) {
        MobCatalog.Builder builder = MobCatalog.builder(stats);
        for (String id : IDS) {
            builder.add(definition(MobId.of(id), stats));
        }
        return builder.build();
    }
}
