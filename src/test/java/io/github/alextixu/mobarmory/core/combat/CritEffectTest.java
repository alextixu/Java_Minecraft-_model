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

/** Component tests: the critical hit effect on its own. */
class CritEffectTest {
    private static final double EPS = 1e-9;
    private static final StatId CRIT = ModStats.CRIT_CHANCE.id();
    private final CritEffect effect = new CritEffect(ModStats.CRIT_CHANCE);

    @Test
    @DisplayName("H5 Given 攻擊者暴擊率 100%，這一下基礎傷害 10 When 打中目標 Then 目標受到 10 × 1.5 = 15 的傷害")
    void h5CertainCrit() {
        CombatOutcome outcome = effect.apply(melee(with(CRIT, 1.0), StatReader.NONE, 10), always(0.999));

        assertEquals(15.0, outcome.damage(), EPS);
        assertTrue(outcome.critical());
        assertFalse(outcome.cancelled());
    }

    @Test
    @DisplayName("B9 Given 攻擊者暴擊率判定成功，同時又是原版的跳劈暴擊 When 計算傷害 Then 不疊加，只乘一次 1.5（15，不是 22.5）")
    void b9NoDoubleCrit() {
        var ctx = new CombatContext(with(CRIT, 1.0), StatReader.NONE, 10, DamageKind.MELEE, true);

        assertEquals(15.0, effect.apply(ctx, always(0.0)).damage(), EPS);
    }

    @Test
    @DisplayName("原版跳劈本身就算暴擊：暴擊率 0 也乘 1.5")
    void vanillaCritAlone() {
        var ctx = new CombatContext(StatReader.NONE, StatReader.NONE, 10, DamageKind.MELEE, true);
        CombatOutcome outcome = effect.apply(ctx, always(0.999));

        assertEquals(15.0, outcome.damage(), EPS);
        assertTrue(outcome.critical());
    }

    @Test
    @DisplayName("暴擊率 25%（短刀）：亂數 0.2 會暴擊，0.3 不會")
    void chanceBoundary() {
        var ctx = melee(with(CRIT, 0.25), StatReader.NONE, 10);

        assertTrue(effect.apply(ctx, always(0.2)).critical());
        assertFalse(effect.apply(ctx, always(0.3)).critical());
        assertEquals(10.0, effect.apply(ctx, always(0.3)).damage(), EPS);
    }

    @Test
    @DisplayName("N2 Given 攻擊者暴擊率 100%，但這一下沒蓄滿 When 打中目標 Then 不判定暴擊，傷害維持 10")
    void n2WeakSwingNeverCrits() {
        var ctx = new CombatContext(with(CRIT, 1.0), StatReader.NONE, 10, DamageKind.MELEE, false, false);
        CombatOutcome outcome = effect.apply(ctx, always(0.0));

        assertFalse(outcome.critical());
        assertEquals(10.0, outcome.damage(), EPS);
    }

    @Test
    @DisplayName("N2：蓄滿的一擊照常判定；沒有蓄力的攻擊者（生物、投射物）一律算蓄滿")
    void n2FullStrengthRolls() {
        var charged = new CombatContext(with(CRIT, 1.0), StatReader.NONE, 10, DamageKind.MELEE, false, true);
        var mob = new CombatContext(with(CRIT, 1.0), StatReader.NONE, 10, DamageKind.MELEE, false);

        assertTrue(effect.apply(charged, always(0.0)).critical());
        assertTrue(mob.fullStrength());
        assertTrue(effect.apply(mob, always(0.0)).critical());
    }

    @Test
    @DisplayName("讀的是攻擊者的暴擊率，不是防禦者的")
    void readsAttacker() {
        var ctx = melee(StatReader.NONE, with(CRIT, 1.0), 10);

        assertFalse(effect.apply(ctx, always(0.0)).critical());
    }
}
