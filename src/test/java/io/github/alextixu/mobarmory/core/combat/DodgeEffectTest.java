package io.github.alextixu.mobarmory.core.combat;

import static io.github.alextixu.mobarmory.core.combat.CombatTestSupport.always;
import static io.github.alextixu.mobarmory.core.combat.CombatTestSupport.melee;
import static io.github.alextixu.mobarmory.core.combat.CombatTestSupport.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.StatReader;
import io.github.alextixu.mobarmory.core.stat.StatId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: the dodge effect on its own. */
class DodgeEffectTest {
    private static final double EPS = 1e-9;
    private static final StatId DODGE = ModStats.DODGE_CHANCE.id();
    private final DodgeEffect effect = new DodgeEffect(ModStats.DODGE_CHANCE);

    @Test
    @DisplayName("H6 Given 防禦者迴避率在上限、亂數設定成必定迴避 When 被殭屍近戰打中 Then 不受傷害")
    void h6DodgeAtCap() {
        CombatOutcome outcome = effect.apply(melee(StatReader.NONE, with(DODGE, 0.75), 5), always(0.0));

        assertTrue(outcome.cancelled());
        assertEquals(0.0, outcome.damage(), EPS);
    }

    @Test
    @DisplayName("B8 Given 玩家迴避率 75% When 受到摔落、火焰、溺水等環境傷害 Then 不能迴避")
    void b8EnvironmentCannotBeDodged() {
        var ctx = new CombatContext(StatReader.NONE, with(DODGE, 0.75), 4, DamageKind.ENVIRONMENT, false);
        CombatOutcome outcome = effect.apply(ctx, always(0.0));

        assertFalse(outcome.cancelled());
        assertEquals(4.0, outcome.damage(), EPS);
    }

    @Test
    @DisplayName("投射物（例如骷髏的箭）是生物造成的傷害，可以迴避")
    void projectileCanBeDodged() {
        var ctx = new CombatContext(StatReader.NONE, with(DODGE, 0.75), 4, DamageKind.PROJECTILE, false);

        assertTrue(effect.apply(ctx, always(0.0)).cancelled());
    }

    @Test
    @DisplayName("迴避率被夾在 75%：就算讀到 1.0，亂數 0.75 也不會迴避")
    void capIsEnforced() {
        var ctx = melee(StatReader.NONE, with(DODGE, 1.0), 5);

        assertFalse(effect.apply(ctx, always(0.75)).cancelled());
        assertTrue(effect.apply(ctx, always(0.7499)).cancelled());
    }

    @Test
    @DisplayName("迴避胸甲 15%：亂數 0.1 會迴避，0.2 不會")
    void evasionChestplateChance() {
        var ctx = melee(StatReader.NONE, with(DODGE, 0.15), 5);

        assertTrue(effect.apply(ctx, always(0.1)).cancelled());
        assertFalse(effect.apply(ctx, always(0.2)).cancelled());
    }
}
