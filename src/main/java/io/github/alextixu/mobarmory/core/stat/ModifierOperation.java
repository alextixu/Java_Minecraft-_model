package io.github.alextixu.mobarmory.core.stat;

/**
 * How a {@link StatModifier} changes a stat. See {@link StackingRules} for how several modifiers combine.
 */
public enum ModifierOperation {
    /** Adds a fixed amount. Fixed amounts on the same stat are summed. Example: attack damage +6. */
    ADD,
    /** Multiplies by {@code 1 + amount}. Percentages on the same stat multiply each other. Example: movement speed -30% is {@code -0.30}. */
    MULTIPLY
}
