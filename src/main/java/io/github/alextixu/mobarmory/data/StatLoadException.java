package io.github.alextixu.mobarmory.data;

import java.util.List;

/**
 * One or more stat files are wrong. Every problem names its file, so one message lists everything to fix (Q9 A).
 * At startup this stops the game, like the weapon files (R2b O7).
 */
public final class StatLoadException extends Exception {
    private final List<String> problems;

    public StatLoadException(List<String> problems) {
        super("Mob Armory stat files (config/mobarmory/stats) have " + problems.size() + " problem(s):\n  "
            + String.join("\n  ", problems));
        this.problems = List.copyOf(problems);
    }

    /** Each problem as "file: field: reason". */
    public List<String> problems() {
        return problems;
    }
}
