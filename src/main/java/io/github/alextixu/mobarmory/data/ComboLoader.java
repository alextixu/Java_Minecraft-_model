package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.combo.ComboCatalog;
import io.github.alextixu.mobarmory.core.combo.ComboDefinition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads every combo file and checks it, the same way {@link WeaponLoader} does for weapons: all problems are
 * collected and each names its file, so one message lists everything to fix. Two files with the same order of swings
 * are reported too, since the second could never trigger.
 */
public final class ComboLoader {
    private final ComboEffectTypes effects;

    public ComboLoader(ComboEffectTypes effects) {
        this.effects = Objects.requireNonNull(effects, "effects");
    }

    /** Reads {@code config/mobarmory/combos/}. */
    public ComboCatalog load(JsonFolder folder) throws IOException, WeaponLoadException {
        return read(folder.files());
    }

    /**
     * @return every combo, sorted by file name
     * @throws WeaponLoadException if any file has a problem
     */
    public ComboCatalog read(List<JsonFolder.DataFile> files) throws WeaponLoadException {
        List<ComboDefinition> loaded = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (JsonFolder.DataFile file : files) {
            try {
                loaded.add(ComboJson.read(file.id(), file.text(), effects));
            } catch (WeaponFormatException e) {
                problems.add(file.fileName() + ": " + e.getMessage());
            } catch (IllegalArgumentException e) {
                problems.add(file.fileName() + ": the file name is the combo id; " + e.getMessage());
            }
        }
        if (problems.isEmpty()) {
            try {
                return ComboCatalog.of(loaded);
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        throw new WeaponLoadException("combo files", problems);
    }
}
