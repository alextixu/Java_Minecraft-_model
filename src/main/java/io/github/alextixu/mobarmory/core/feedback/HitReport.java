package io.github.alextixu.mobarmory.core.feedback;

import io.github.alextixu.mobarmory.core.combat.DamageKind;
import java.util.Objects;

/**
 * The result of one hit, as the displays see it. The server builds it after Minecraft has applied armor,
 * resistance and absorption, so {@link #damage} is the health the defender actually loses.
 * <p>
 * Numbers are taken as they come: another mod may produce NaN or infinite damage, and the display rules
 * ({@link HitFilter}, {@link HitText}) deal with that instead of this record throwing in the middle of combat.
 *
 * @param kind        melee, projectile or environment
 * @param attacker    who caused the hit; {@link Party#NONE} when nobody did
 * @param defender    who was hit
 * @param result      landed, landed as a critical hit, or dodged
 * @param damage      health the defender loses; 0 when dodged
 * @param healthAfter the defender's health after the hit; 0 or less means it died
 * @param maxHealth   the defender's maximum health
 */
public record HitReport(DamageKind kind, Party attacker, Party defender, HitResult result,
                        double damage, double healthAfter, double maxHealth) {

    public HitReport {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(result, "result");
    }

    /** A hit that took {@code damage} health from a defender that had {@code healthBefore}. */
    public static HitReport landed(DamageKind kind, Party attacker, Party defender, boolean critical,
                                   double damage, double healthBefore, double maxHealth) {
        HitResult result = critical ? HitResult.CRITICAL : HitResult.HIT;
        return new HitReport(kind, attacker, defender, result, damage, healthBefore - damage, maxHealth);
    }

    /** A hit the defender dodged; its health does not change. */
    public static HitReport dodged(DamageKind kind, Party attacker, Party defender, double health, double maxHealth) {
        return new HitReport(kind, attacker, defender, HitResult.DODGED, 0, health, maxHealth);
    }
}
