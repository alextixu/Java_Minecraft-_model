package io.github.alextixu.mobarmory.core.combo;

import java.util.Objects;
import java.util.Optional;

/**
 * One player's swings, as a tiny state machine (decision Q2): it remembers the last swing that counted and when, and
 * matches it with the next one against the combos in effect. The Forge layer decides which swings count (Q6 A: a
 * full-strength melee swing that landed on its target) and passes the game tick.
 * <ul>
 *   <li>A swing that completes a combo within that combo's window (that many ticks still counts, O5) triggers it.
 *       The tracker then starts over, so that swing does not start the next combo (O6).</li>
 *   <li>Anything else makes this swing the new first one: no earlier swing, too late, a tick that went backwards, or
 *       an order that is no combo, such as WALK then WALK with the default files (O7).</li>
 * </ul>
 * The catalog is passed on every swing, so a {@code /mobarmory reload} takes effect on the very next swing.
 * Not thread-safe: one tracker per player, used on the server thread only (O10: kept in memory, never saved).
 */
public final class ComboTracker {
    private ComboInput last;
    private long lastTick;

    /**
     * Records a swing that counted.
     *
     * @return the combo this swing completes, if any
     */
    public Optional<ComboDefinition> record(ComboCatalog combos, ComboInput input, long tick) {
        Objects.requireNonNull(combos, "combos");
        Objects.requireNonNull(input, "input");
        if (last != null && tick >= lastTick) {
            long elapsed = tick - lastTick;
            Optional<ComboDefinition> combo = combos.find(last, input).filter(found -> elapsed <= found.windowTicks());
            if (combo.isPresent()) {
                last = null;
                return combo;
            }
        }
        last = input;
        lastTick = tick;
        return Optional.empty();
    }

    /** Forgets the last swing, as if the player had not swung yet. */
    public void reset() {
        last = null;
    }
}
