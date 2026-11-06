package io.github.alextixu.mobarmory.data;

import java.util.List;

/**
 * One or more mob files are wrong. Every problem names its file, so one message lists everything to fix (B1 to B9).
 * At startup this stops the game (R4b decision O2); on reload nothing changes (B15).
 */
public final class MobLoadException extends Exception {
    private final List<String> problems;

    public MobLoadException(List<String> problems) {
        super("Mob Armory mob files have " + problems.size() + " problem(s):\n  " + String.join("\n  ", problems));
        this.problems = List.copyOf(problems);
    }

    /** Each problem as "file: field: reason". */
    public List<String> problems() {
        return problems;
    }
}
