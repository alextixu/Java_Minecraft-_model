package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.combo.ComboCatalog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The two combos the mod ships (R3, decision Q5 A), as combo files in {@code mobarmory/default_combos/}: Lunge (walk,
 * then run: x1.5) and Follow-up (run, then walk: x1.3). On the very first start they are copied to
 * {@code config/mobarmory/combos/}, the same way as the default weapons; after that the config folder is the only
 * source, and the game never overwrites it.
 */
public final class DefaultCombos {
    public static final List<String> IDS = List.of("lunge", "follow_up");

    private static final String FOLDER = "/mobarmory/default_combos/";

    private DefaultCombos() {
    }

    /** The shipped file content for one default combo. */
    public static String text(String id) {
        return ShippedFiles.text(FOLDER, id);
    }

    /**
     * Copies the defaults into the folder if it does not exist yet (the very first start). An existing folder is never
     * touched, even if the player deleted every file in it: then there are no combos.
     *
     * @return true if the defaults were installed
     */
    public static boolean installIfMissing(JsonFolder folder) throws IOException {
        if (folder.exists()) {
            return false;
        }
        for (String id : IDS) {
            folder.write(id, text(id));
        }
        return true;
    }

    /** The shipped files, read through the same rules as the files in the config folder. */
    public static List<JsonFolder.DataFile> files() {
        List<JsonFolder.DataFile> files = new ArrayList<>();
        for (String id : IDS) {
            files.add(new JsonFolder.DataFile(id + ".json", text(id)));
        }
        return files;
    }

    /** The defaults as a catalog (for tests and the behaviour report's numbers). */
    public static ComboCatalog catalog() {
        try {
            return new ComboLoader(ComboEffectTypes.STANDARD).read(files());
        } catch (WeaponLoadException e) {
            throw new IllegalStateException("Shipped default combos: " + e.getMessage(), e);
        }
    }
}
