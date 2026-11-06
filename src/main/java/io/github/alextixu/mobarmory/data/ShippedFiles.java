package io.github.alextixu.mobarmory.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Data files shipped inside the mod jar, such as {@code mobarmory/default_weapons/dagger.json}. They are copied to
 * the config folder on the very first start and never again; after that the config folder is the only source.
 */
final class ShippedFiles {
    private ShippedFiles() {
    }

    /**
     * @param folder the folder inside the jar, for example {@code /mobarmory/default_combos/}
     * @param id     the file name without {@code .json}
     */
    static String text(String folder, String id) {
        try (InputStream in = ShippedFiles.class.getResourceAsStream(folder + id + ".json")) {
            if (in == null) {
                throw new IllegalStateException("The mod is missing " + folder + id + ".json");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
