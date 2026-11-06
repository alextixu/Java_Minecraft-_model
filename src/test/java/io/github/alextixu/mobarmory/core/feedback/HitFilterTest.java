package io.github.alextixu.mobarmory.core.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.combat.DamageKind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Component tests: which hits are shown and to whom (Q5, Q7, O4, O6). */
class HitFilterTest {

    private static HitReport landed(DamageKind kind, Party attacker, Party defender, double damage) {
        return HitReport.landed(kind, attacker, defender, false, damage, 20, 20);
    }

    @Test
    @DisplayName("H1 Given 玩家打牛 When 扣了血 Then 只有攻擊的玩家看得到")
    void h1PlayerHitsMob() {
        assertEquals(Set.of(Viewer.ATTACKER), HitFilter.viewers(landed(DamageKind.MELEE, Party.PLAYER, Party.MOB, 1)));
    }

    @Test
    @DisplayName("H8 Given 殭屍打玩家 When 扣了血 Then 只有被打的玩家看得到")
    void h8MobHitsPlayer() {
        assertEquals(Set.of(Viewer.DEFENDER), HitFilter.viewers(landed(DamageKind.MELEE, Party.MOB, Party.PLAYER, 3)));
    }

    @Test
    @DisplayName("H9 Given 玩家射箭 When 命中殭屍 Then 照樣顯示給射手")
    void h9Projectile() {
        assertEquals(Set.of(Viewer.ATTACKER), HitFilter.viewers(landed(DamageKind.PROJECTILE, Party.PLAYER, Party.MOB, 6)));
    }

    @Test
    @DisplayName("H7 Given 玩家迴避了殭屍 When 回報 MISS Then 被打的玩家看得到（迴避沒有扣血也要顯示）")
    void h7DodgeShows() {
        HitReport dodged = HitReport.dodged(DamageKind.MELEE, Party.MOB, Party.PLAYER, 20, 20);

        assertEquals(Set.of(Viewer.DEFENDER), HitFilter.viewers(dodged));
    }

    @Test
    @DisplayName("B15 Given 玩家 A 打玩家 B When 扣了血 Then A、B 都看得到")
    void b15PlayerHitsPlayer() {
        assertEquals(EnumSet.allOf(Viewer.class), HitFilter.viewers(landed(DamageKind.MELEE, Party.PLAYER, Party.PLAYER, 2)));
    }

    @Test
    @DisplayName("B6 Given 殭屍打牛（沒有玩家參與） When 扣了血 Then 結果算數，但沒有人收到")
    void b6NoPlayerInvolved() {
        HitReport report = landed(DamageKind.MELEE, Party.MOB, Party.MOB, 3);

        assertTrue(HitFilter.shows(report));
        assertTrue(HitFilter.viewers(report).isEmpty());
    }

    @Test
    @DisplayName("B5 Given 摔落、著火等環境傷害 When 扣了血 Then 不顯示")
    void b5Environment() {
        assertFalse(HitFilter.shows(landed(DamageKind.ENVIRONMENT, Party.NONE, Party.PLAYER, 4)));
    }

    @Test
    @DisplayName("B18 Given 射手已經登出（來源沒有生物） When 箭命中 Then 不顯示")
    void b18NoAttacker() {
        assertFalse(HitFilter.shows(landed(DamageKind.PROJECTILE, Party.NONE, Party.MOB, 4)));
    }

    @Test
    @DisplayName("B1、B7 Given 無敵時間內或吸收心全吸收，實際扣的血是 0 When 回報 Then 不顯示")
    void b1b7NothingTaken() {
        assertFalse(HitFilter.shows(landed(DamageKind.MELEE, Party.PLAYER, Party.MOB, 0)));
    }

    @Test
    @DisplayName("B17 Given 傷害是 NaN When 回報 Then 不顯示；無限大照樣顯示")
    void b17Extremes() {
        assertFalse(HitFilter.shows(landed(DamageKind.MELEE, Party.PLAYER, Party.MOB, Double.NaN)));
        assertTrue(HitFilter.shows(landed(DamageKind.MELEE, Party.PLAYER, Party.MOB, Double.POSITIVE_INFINITY)));
    }

    /** Every combination of attacker, defender and result, with damage 0 and 1, for one kind of damage. */
    private static List<HitReport> everyHit(DamageKind kind) {
        List<HitReport> reports = new ArrayList<>();
        for (Party attacker : Party.values()) {
            for (Party defender : List.of(Party.PLAYER, Party.MOB)) {
                for (HitResult result : HitResult.values()) {
                    for (double damage : new double[] {0, 1}) {
                        double dealt = result == HitResult.DODGED ? 0 : damage;
                        reports.add(new HitReport(kind, attacker, defender, result, dealt, 20 - dealt, 20));
                    }
                }
            }
        }
        return reports;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DamageKind.class)
    @DisplayName("所有組合：只給參與的玩家；環境傷害、沒有攻擊者、沒扣血的命中永遠沒有人看到")
    void everyCombination(DamageKind kind) {
        for (HitReport report : everyHit(kind)) {
            Set<Viewer> viewers = HitFilter.viewers(report);
            boolean mobCaused = report.kind() != DamageKind.ENVIRONMENT && report.attacker() != Party.NONE;
            boolean counts = report.result() == HitResult.DODGED || report.damage() > 0;

            if (!mobCaused || !counts) {
                assertTrue(viewers.isEmpty(), "nobody should see " + report);
                continue;
            }
            assertEquals(report.attacker() == Party.PLAYER, viewers.contains(Viewer.ATTACKER), "attacker side of " + report);
            assertEquals(report.defender() == Party.PLAYER, viewers.contains(Viewer.DEFENDER), "defender side of " + report);
        }
    }
}
