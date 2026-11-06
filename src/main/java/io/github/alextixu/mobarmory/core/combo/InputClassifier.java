package io.github.alextixu.mobarmory.core.combo;

import io.github.alextixu.mobarmory.core.StatReader;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.Objects;

/**
 * Decides whether a swing is a walking or a running swing from the attacker's movement speed: "combo 要和數值結合…
 * 所以要讀得到移動速度" (decisions Q4 A, O12). The speed is read through {@link StatReader}, so it already includes
 * everything that changes it: equipment (a heavy sword slows you down), potions, and Minecraft's own sprint bonus
 * (x1.3 while sprinting). A heavy weapon can therefore be too slow for a running swing (B12).
 * <p>
 * Read it when the swing starts: Minecraft stops a sprint, and removes its speed bonus, as soon as a sprinting hit
 * lands (17, risk T1).
 */
public final class InputClassifier {
    private final StatId speed;
    private final double runSpeed;

    /**
     * @param speed    the movement speed stat
     * @param runSpeed the speed from which a swing counts as running, inclusive
     */
    public InputClassifier(StatId speed, double runSpeed) {
        this.speed = Objects.requireNonNull(speed, "speed");
        if (!(runSpeed > 0) || Double.isInfinite(runSpeed)) {
            throw new IllegalArgumentException("run speed must be a positive number, got " + runSpeed);
        }
        this.runSpeed = runSpeed;
    }

    /** {@link ComboInput#RUN} at or above the run speed; {@link ComboInput#WALK} below it, and when the speed is not a number. */
    public ComboInput classify(StatReader stats) {
        return stats.get(speed) >= runSpeed ? ComboInput.RUN : ComboInput.WALK;
    }

    public double runSpeed() {
        return runSpeed;
    }
}
