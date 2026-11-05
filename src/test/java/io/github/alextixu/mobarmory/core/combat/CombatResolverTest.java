package io.github.alextixu.mobarmory.core.combat;

import static io.github.alextixu.mobarmory.core.combat.CombatTestSupport.always;
import static io.github.alextixu.mobarmory.core.combat.CombatTestSupport.melee;
import static io.github.alextixu.mobarmory.core.combat.CombatTestSupport.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.StatReader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Module tests: the prototype's effects run together through the resolver. */
class CombatResolverTest {
    private static final double EPS = 1e-9;
    private static final CombatEffects EFFECTS = ModStats.COMBAT_EFFECTS;

    @Test
    @DisplayName("雛型有一個防禦效果（迴避）和一個攻擊效果（暴擊）")
    void prototypeEffects() {
        assertEquals(List.of(ModStats.DODGE_CHANCE.id()), EFFECTS.defensive().stream().map(StatEffect::stat).toList());
        assertEquals(List.of(ModStats.CRIT_CHANCE.id()), EFFECTS.offensive().stream().map(StatEffect::stat).toList());
    }

    @Test
    @DisplayName("迴避成功時整串停止：後面的暴擊不會把 0 傷害又乘上去")
    void dodgeStopsTheChain() {
        var ctx = melee(with(ModStats.CRIT_CHANCE.id(), 1.0), with(ModStats.DODGE_CHANCE.id(), 0.75), 10);
        CombatOutcome outcome = CombatResolver.resolve(EFFECTS.all(), ctx, always(0.0));

        assertTrue(outcome.cancelled());
        assertEquals(0.0, outcome.damage(), EPS);
    }

    @Test
    @DisplayName("沒迴避時，暴擊照常生效：10 → 15")
    void critAfterFailedDodge() {
        var ctx = melee(with(ModStats.CRIT_CHANCE.id(), 1.0), StatReader.NONE, 10);
        CombatOutcome outcome = CombatResolver.resolve(EFFECTS.all(), ctx, always(0.0));

        assertEquals(15.0, outcome.damage(), EPS);
        assertTrue(outcome.critical());
    }

    @Test
    @DisplayName("沒有任何效果時，傷害不變")
    void noEffects() {
        CombatOutcome outcome = CombatResolver.resolve(List.of(), melee(StatReader.NONE, StatReader.NONE, 7), always(0.0));

        assertEquals(new CombatOutcome(7, false, false), outcome);
    }
}
