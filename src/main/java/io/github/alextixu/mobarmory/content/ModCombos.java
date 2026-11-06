package io.github.alextixu.mobarmory.content;

import io.github.alextixu.mobarmory.core.combo.InputClassifier;

/**
 * The combo rules that are not part of any one combo: what counts as a swing, and when a swing is a running one
 * (R3, decisions Q4 A, Q6 A, O12 in 19-r3-combo-open-questions.md). The combos themselves are files in
 * {@code config/mobarmory/combos/}; a player adds a combo by adding a file (Q2 changed to JSON on 11/07).
 */
public final class ModCombos {
    /**
     * A swing at movement speed 0.12 or more is a running swing: 1.2x the default 0.1. Sprinting with an empty hand
     * reads 0.13; sprinting with a heavy sword only 0.091, too slow (B12).
     */
    public static final double RUN_SPEED = 0.12;

    public static final InputClassifier INPUTS = new InputClassifier(ModStats.MOVEMENT_SPEED.id(), RUN_SPEED);

    /**
     * Only a full-strength swing counts: the attack bar must be above 0.9, the same threshold as R1's crit (N2) and
     * Minecraft's own full-strength swing.
     */
    public static final float FULL_CHARGE = 0.9F;

    private ModCombos() {
    }
}
