package io.github.alextixu.mobarmory.core.equipment;

/** Someone changed the piece after the version a change was based on (B11, decision O10). */
public final class StaleVersionException extends RuntimeException {
    private final long currentVersion;

    public StaleVersionException(EquipmentId id, long expected, long current) {
        super("Equipment '" + id + "' was changed by someone else (version " + current + ", the change was based on "
            + expected + "); reload and try again");
        this.currentVersion = current;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
