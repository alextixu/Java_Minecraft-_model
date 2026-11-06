package io.github.alextixu.mobarmory.core.combo;

/**
 * What kind of swing a hit was. Decided by {@link InputClassifier} from the attacker's movement speed when the swing
 * starts (decision Q4 A). A new kind of swing (a jumping or a sneaking swing) is a new value here and a new rule in
 * {@link InputClassifier}.
 */
public enum ComboInput {
    /** A swing while moving slower than the run speed. */
    WALK,
    /** A swing while moving at least as fast as the run speed. */
    RUN
}
