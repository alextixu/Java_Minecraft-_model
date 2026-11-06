package io.github.alextixu.mobarmory.data;

import java.util.List;

/**
 * One or more weapon files are wrong. Every problem names its file, so one message lists everything to fix
 * (B1 to B6). At startup this stops the game (decision O7); on reload nothing changes (decision O8).
 */
public final class WeaponLoadException extends Exception {
    private final List<String> problems;

    public WeaponLoadException(List<String> problems) {
        this("weapon files", problems);
    }

    /**
     * @param files which files, for example {@code "combo files"}: every kind of data file reports its problems this
     *              way (R3 combos reuse it)
     */
    public WeaponLoadException(String files, List<String> problems) {
        super("Mob Armory " + files + " have " + problems.size() + " problem(s):\n  " + String.join("\n  ", problems));
        this.problems = List.copyOf(problems);
    }

    /** Each problem as "file: field: reason". */
    public List<String> problems() {
        return problems;
    }
}
