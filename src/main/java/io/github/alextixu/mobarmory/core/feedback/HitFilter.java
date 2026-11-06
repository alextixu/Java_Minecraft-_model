package io.github.alextixu.mobarmory.core.feedback;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Which hits are shown, and to whom (decisions Q5, Q7, O4, O6 in 13-damage-display-open-questions.md). */
public final class HitFilter {

    private HitFilter() {
    }

    /**
     * True if the hit is worth showing at all:
     * <ul>
     *   <li>only damage caused by a mob, melee or projectile; never falling, fire or drowning (Q7 A, B5, B18);</li>
     *   <li>a dodge always counts (the caller only reports a dodge when the hit could have hurt, O5);</li>
     *   <li>a landed hit counts only if it took health. A hit inside the damage cooldown that took nothing, or one
     *       fully soaked up by absorption hearts, shows nothing (O4, O6). NaN damage is not shown (B17).</li>
     * </ul>
     */
    public static boolean shows(HitReport report) {
        if (!report.kind().causedByMob() || report.attacker() == Party.NONE) {
            return false;
        }
        return report.result() == HitResult.DODGED || report.damage() > 0;
    }

    /** The players who get the line: the attacking player and the player who was hit (Q5). Empty if not shown. */
    public static Set<Viewer> viewers(HitReport report) {
        if (!shows(report)) {
            return Set.of();
        }
        EnumSet<Viewer> viewers = EnumSet.noneOf(Viewer.class);
        if (report.attacker() == Party.PLAYER) {
            viewers.add(Viewer.ATTACKER);
        }
        if (report.defender() == Party.PLAYER) {
            viewers.add(Viewer.DEFENDER);
        }
        return Collections.unmodifiableSet(viewers);
    }
}
