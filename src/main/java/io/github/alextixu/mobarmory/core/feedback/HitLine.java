package io.github.alextixu.mobarmory.core.feedback;

/**
 * One action bar line, still without the other party's name. The Forge layer adds the name as the first
 * argument, so the client shows it in the player's own language.
 *
 * @param translationKey language key; its arguments are: name, damage, health, max health
 * @param tone           how the line should look
 * @param damage         formatted damage, for example {@code "6.9"}
 * @param health         formatted health of the defender after the hit, for example {@code "13.1"} or {@code "0"}
 * @param maxHealth      formatted maximum health of the defender, for example {@code "20"}
 */
public record HitLine(String translationKey, Tone tone, String damage, String health, String maxHealth) {
}
