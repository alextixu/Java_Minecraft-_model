package io.github.alextixu.mobarmory.core.feedback;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a hit into an action bar line (decisions Q6, O1, O2, O3, O8, O10):
 * <ul>
 *   <li>the attacking player sees {@code <target> -<damage> ❤ <health>/<max>}, with {@code CRIT} after the
 *       damage for a critical hit, or {@code <target> MISS ❤ ...} for a dodge;</li>
 *   <li>the player who was hit sees {@code <attacker> hit you -<damage> ❤ <health>/<max>}, or
 *       {@code Dodged <attacker> ❤ ...};</li>
 *   <li>numbers are health points with one decimal, rounded half up. A hit that took any health never shows
 *       {@code 0.0} (at least {@code 0.1}); a dead defender shows {@code 0} health; a whole maximum shows no
 *       decimal ({@code 20}).</li>
 * </ul>
 * Jump crits and crit-chance crits look the same (O10). A hit that completed a combo (R3, O8) gets the combo's name
 * after the line, on both players' lines: {@code Cow -1.5 ❤ 7.5/10 · Lunge}. The words live in the language file
 * under {@value #KEY_PREFIX}.
 */
public final class HitText {
    public static final String KEY_PREFIX = "mobarmory.hit.";
    /** Language key of the combo suffix; its one argument is the combo's name. */
    public static final String COMBO_KEY = KEY_PREFIX + "combo";
    private static final BigDecimal SMALLEST = new BigDecimal("0.1");

    private HitText() {
    }

    public static HitLine line(HitReport report, Viewer viewer) {
        return new HitLine(key(report.result(), viewer), tone(report.result(), viewer),
            damage(report.damage()), health(report.healthAfter()), maxHealth(report.maxHealth()), report.combo());
    }

    /** Every language key {@link #line} can produce, so a test can check the language file has them all. */
    public static List<String> allKeys() {
        List<String> keys = new ArrayList<>();
        for (Viewer viewer : Viewer.values()) {
            for (HitResult result : HitResult.values()) {
                keys.add(key(result, viewer));
            }
        }
        keys.add(COMBO_KEY);
        return List.copyOf(keys);
    }

    static String key(HitResult result, Viewer viewer) {
        String side = viewer == Viewer.ATTACKER ? "dealt" : "taken";
        String suffix = switch (result) {
            case HIT -> "";
            case CRITICAL -> ".crit";
            case DODGED -> ".miss";
        };
        return KEY_PREFIX + side + suffix;
    }

    static Tone tone(HitResult result, Viewer viewer) {
        if (result == HitResult.DODGED) {
            return Tone.MISS;
        }
        if (viewer == Viewer.DEFENDER) {
            return Tone.HURT;
        }
        return result == HitResult.CRITICAL ? Tone.CRITICAL : Tone.NORMAL;
    }

    /** Damage dealt: one decimal, at least 0.1 when anything was dealt (O1). */
    static String damage(double value) {
        String special = special(value);
        return special != null ? special : atLeastSmallest(value);
    }

    /** Health left: {@code 0} once dead (O3), otherwise one decimal and at least 0.1 while alive. */
    static String health(double value) {
        String special = special(value);
        return special != null ? special : atLeastSmallest(value);
    }

    /** Maximum health: {@code 20} rather than {@code 20.0}; {@code 20.5} keeps its decimal. */
    static String maxHealth(double value) {
        String special = special(value);
        if (special != null) {
            return special;
        }
        BigDecimal rounded = oneDecimal(value);
        return rounded.stripTrailingZeros().scale() <= 0
            ? rounded.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
            : rounded.toPlainString();
    }

    /** NaN, infinity and nothing-or-less get a fixed text, so no number ever reads "NaN" or carries a minus sign. */
    private static String special(double value) {
        if (Double.isNaN(value)) {
            return "?";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "∞";
        }
        return value <= 0 ? "0" : null;
    }

    private static String atLeastSmallest(double value) {
        BigDecimal rounded = oneDecimal(value);
        return (rounded.signum() == 0 ? SMALLEST : rounded).toPlainString();
    }

    private static BigDecimal oneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
