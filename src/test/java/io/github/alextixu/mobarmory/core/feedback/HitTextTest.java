package io.github.alextixu.mobarmory.core.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.alextixu.mobarmory.core.combat.DamageKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Component tests: what the action bar line says (12-damage-display-given-when-then.md). */
class HitTextTest {

    private static HitReport playerHitsMob(boolean critical, double damage, double healthBefore, double max) {
        return HitReport.landed(DamageKind.MELEE, Party.PLAYER, Party.MOB, critical, damage, healthBefore, max);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("O1 傷害：小數 1 位、四捨五入；有扣血最少 0.1（12 的示範數字表）")
    @CsvSource({
        "7.0, 7.0",
        "6.888, 6.9",
        "2.94, 2.9",
        "5.18, 5.2",
        "4.428, 4.4",
        "10.332, 10.3",
        "0.25, 0.3",
        "0.05, 0.1",
        "0.04, 0.1",
        "0.0001, 0.1",
    })
    void damageRounding(double damage, String expected) {
        assertEquals(expected, HitText.damage(damage));
    }

    @Test
    @DisplayName("O1 傷害是 float 換成 double 時（例如 5.18f）照樣四捨五入成 5.2")
    void floatDamage() {
        assertEquals("5.2", HitText.damage(5.18f));
        assertEquals("6.9", HitText.damage(6.888f));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("剩餘血量：活著時小數 1 位且最少 0.1；打死時是 0（O3）")
    @CsvSource({
        "13.112, 13.1",
        "3.0, 3.0",
        "0.03, 0.1",
        "0.0, 0",
        "-4.0, 0",
    })
    void healthAfter(double health, String expected) {
        assertEquals(expected, HitText.health(health));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("最大血量：整數不寫小數（20），有小數時保留一位")
    @CsvSource({
        "20.0, 20",
        "10.0, 10",
        "1000.0, 1000",
        "20.5, 20.5",
        "20.04, 20",
    })
    void maxHealth(double max, String expected) {
        assertEquals(expected, HitText.maxHealth(max));
    }

    @Test
    @DisplayName("H2 Given 攻擊力 7 When 打牛（10 血） Then 「Cow -7.0 ❤ 3.0/10」")
    void h2PlainHit() {
        HitLine line = HitText.line(playerHitsMob(false, 7.0, 10, 10), Viewer.ATTACKER);

        assertEquals(new HitLine("mobarmory.hit.dealt", Tone.NORMAL, "7.0", "3.0", "10"), line);
    }

    @Test
    @DisplayName("H4 Given 攻擊力 3、暴擊 When 打牛 Then 金色「Cow -4.5 CRIT ❤ 5.5/10」")
    void h4Crit() {
        HitLine line = HitText.line(playerHitsMob(true, 4.5, 10, 10), Viewer.ATTACKER);

        assertEquals(new HitLine("mobarmory.hit.dealt.crit", Tone.CRITICAL, "4.5", "5.5", "10"), line);
    }

    @Test
    @DisplayName("H5 Given 牛迴避成功 When 玩家打牛 Then 灰色「Cow MISS ❤ 10.0/10」")
    void h5Miss() {
        HitReport report = HitReport.dodged(DamageKind.MELEE, Party.PLAYER, Party.MOB, 10, 10);

        assertEquals(new HitLine("mobarmory.hit.dealt.miss", Tone.MISS, "0", "10.0", "10"),
            HitText.line(report, Viewer.ATTACKER));
    }

    @Test
    @DisplayName("H6 Given 殭屍護甲 10 When 攻擊力 7 扣完護甲是 5.18 Then 「Zombie -5.2 ❤ 14.8/20」")
    void h6AfterArmor() {
        HitLine line = HitText.line(playerHitsMob(false, 5.18f, 20, 20), Viewer.ATTACKER);

        assertEquals("5.2", line.damage());
        assertEquals("14.8", line.health());
        assertEquals("20", line.maxHealth());
    }

    @Test
    @DisplayName("H7、H8 被打的玩家：紅色「Zombie hit you -x.x」；迴避成功時灰色「Dodged Zombie」")
    void defenderSide() {
        HitReport hurt = HitReport.landed(DamageKind.MELEE, Party.MOB, Party.PLAYER, false, 2.22, 20, 20);
        HitReport crit = HitReport.landed(DamageKind.MELEE, Party.MOB, Party.PLAYER, true, 3.33, 20, 20);
        HitReport dodged = HitReport.dodged(DamageKind.MELEE, Party.MOB, Party.PLAYER, 20, 20);

        assertEquals(new HitLine("mobarmory.hit.taken", Tone.HURT, "2.2", "17.8", "20"), HitText.line(hurt, Viewer.DEFENDER));
        assertEquals("mobarmory.hit.taken.crit", HitText.line(crit, Viewer.DEFENDER).translationKey());
        assertEquals(Tone.HURT, HitText.line(crit, Viewer.DEFENDER).tone());
        assertEquals(new HitLine("mobarmory.hit.taken.miss", Tone.MISS, "0", "20.0", "20"), HitText.line(dodged, Viewer.DEFENDER));
    }

    @Test
    @DisplayName("B11 Given 牛只剩 3 血 When 攻擊力 7 打死牛 Then 「Cow -7.0 ❤ 0/10」")
    void b11Overkill() {
        HitLine line = HitText.line(playerHitsMob(false, 7.0, 3, 10), Viewer.ATTACKER);

        assertEquals("7.0", line.damage());
        assertEquals("0", line.health());
    }

    @Test
    @DisplayName("B12 Given 最終傷害約 0.04 When 打中 Then 顯示 0.1，不顯示 0.0")
    void b12TinyDamage() {
        assertEquals("0.1", HitText.line(playerHitsMob(false, 0.04, 20, 20), Viewer.ATTACKER).damage());
    }

    @Test
    @DisplayName("B17 Given 傷害是無限大或 NaN When 組成一行 Then 不丟例外：無限大顯示 ∞，NaN 不會印出 NaN")
    void b17Extremes() {
        HitLine infinite = HitText.line(playerHitsMob(false, Double.POSITIVE_INFINITY, 20, 20), Viewer.ATTACKER);
        HitLine nan = HitText.line(playerHitsMob(false, Double.NaN, 20, 20), Viewer.ATTACKER);

        assertEquals("∞", infinite.damage());
        assertEquals("0", infinite.health());
        assertEquals("?", nan.damage());
        assertEquals("?", nan.health());
    }

    @Test
    @DisplayName("R3 O8 Given 第二下完成 Lunge When 組成一行 Then 攻擊者和被打者兩行都帶 combo 名稱，數字照舊")
    void comboNameOnBothLines() {
        HitReport report = HitReport.landed(DamageKind.MELEE, Party.PLAYER, Party.PLAYER, false, 1.5, 9, 10)
            .withCombo("Lunge");
        HitLine attacker = HitText.line(report, Viewer.ATTACKER);
        HitLine defender = HitText.line(report, Viewer.DEFENDER);

        assertEquals("Lunge", attacker.combo());
        assertEquals("Lunge", defender.combo());
        assertEquals("1.5", attacker.damage());
        assertEquals("7.5", attacker.health());
        assertEquals("mobarmory.hit.dealt", attacker.translationKey());
    }

    @Test
    @DisplayName("R3 沒有 combo 的一下：combo 是 null，一行和 D1 一模一樣；暴擊加 combo 照樣是 CRIT")
    void noComboIsTheD1Line() {
        HitReport plain = playerHitsMob(false, 1, 10, 10);
        assertEquals(null, HitText.line(plain, Viewer.ATTACKER).combo());
        assertEquals(new HitLine("mobarmory.hit.dealt", Tone.NORMAL, "1.0", "9.0", "10"), HitText.line(plain, Viewer.ATTACKER));

        HitLine critCombo = HitText.line(playerHitsMob(true, 2.25, 10, 10).withCombo("Lunge"), Viewer.ATTACKER);
        assertEquals("mobarmory.hit.dealt.crit", critCombo.translationKey());
        assertEquals(Tone.CRITICAL, critCombo.tone());
        assertEquals("2.3", critCombo.damage());
        assertEquals("Lunge", critCombo.combo());
    }
}
