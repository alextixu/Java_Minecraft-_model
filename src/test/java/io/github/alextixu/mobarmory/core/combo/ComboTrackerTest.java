package io.github.alextixu.mobarmory.core.combo;

import static io.github.alextixu.mobarmory.core.combo.ComboInput.RUN;
import static io.github.alextixu.mobarmory.core.combo.ComboInput.WALK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.data.DefaultCombos;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Module tests: the order of swings decides the combo (18's H and B rows that need no game). Uses the shipped combo
 * files, so the numbers are the decided ones: Lunge (walk, run) x1.5, Follow-up (run, walk) x1.3, window 30 ticks.
 */
class ComboTrackerTest {
    private static final ComboCatalog DEFAULTS = DefaultCombos.catalog();
    private ComboTracker tracker;

    @BeforeEach
    void newPlayer() {
        tracker = new ComboTracker();
    }

    private Optional<String> swing(ComboInput input, long tick) {
        return tracker.record(DEFAULTS, input, tick).map(ComboDefinition::id);
    }

    @Test
    @DisplayName("H1 Given 走路打一下 When 1 秒內奔跑打一下 Then 第二下觸發 Lunge")
    void h1WalkThenRunIsLunge() {
        assertEquals(Optional.empty(), swing(WALK, 100));
        assertEquals(Optional.of("lunge"), swing(RUN, 120));
    }

    @Test
    @DisplayName("H2 Given 奔跑打一下 When 1 秒內走路打一下 Then 觸發 Follow-up，和 H1 順序相反、結果不同")
    void h2RunThenWalkIsFollowUp() {
        assertEquals(Optional.empty(), swing(RUN, 100));
        assertEquals(Optional.of("follow_up"), swing(WALK, 120));
        ComboDefinition lunge = DEFAULTS.find(WALK, RUN).orElseThrow();
        ComboDefinition followUp = DEFAULTS.find(RUN, WALK).orElseThrow();
        assertNotEquals(lunge.effect().apply(1.0), followUp.effect().apply(1.0), "different multipliers: 1.5 and 1.3");
    }

    @Test
    @DisplayName("H3 Given 新玩家 When 只打一下 Then 沒有 combo")
    void h3OneSwingIsNoCombo() {
        assertEquals(Optional.empty(), swing(RUN, 0));
    }

    @Test
    @DisplayName("B1 Given 走路打一下 When 2 秒（40 tick）後奔跑打 Then 不觸發，這一下變成新的第一下")
    void b1TooLateStartsOver() {
        swing(WALK, 100);
        assertEquals(Optional.empty(), swing(RUN, 140));
        assertEquals(Optional.of("follow_up"), swing(WALK, 150), "the late run swing is the new first one");
    }

    @Test
    @DisplayName("B2 Given 走路打一下 When 剛好 30 tick 後奔跑打 Then 觸發；31 tick 就不觸發（O5）")
    void b2WindowEdge() {
        swing(WALK, 100);
        assertEquals(Optional.of("lunge"), swing(RUN, 130));

        newPlayer();
        swing(WALK, 100);
        assertEquals(Optional.empty(), swing(RUN, 131));
    }

    @Test
    @DisplayName("B3 Given 走路打一下 When 再走路打一下 Then 不觸發，時間窗從第二下重新算（O7）")
    void b3WalkWalkReplacesTheFirst() {
        swing(WALK, 100);
        assertEquals(Optional.empty(), swing(WALK, 125));
        assertEquals(Optional.of("lunge"), swing(RUN, 150), "25 ticks after the second walk, 50 after the first");
    }

    @Test
    @DisplayName("B4 Given 奔跑打一下 When 再奔跑打一下 Then 不觸發（O7）")
    void b4RunRunIsNoCombo() {
        swing(RUN, 100);
        assertEquals(Optional.empty(), swing(RUN, 110));
        assertEquals(Optional.of("follow_up"), swing(WALK, 120));
    }

    @Test
    @DisplayName("B11 Given 走→跑觸發了 Lunge When 1 秒內再走路打 Then 不觸發 Follow-up：不連鎖（O6）")
    void b11NoChaining() {
        swing(WALK, 100);
        assertEquals(Optional.of("lunge"), swing(RUN, 110));
        assertEquals(Optional.empty(), swing(WALK, 120), "the run swing was used up by Lunge");
        assertEquals(Optional.of("lunge"), swing(RUN, 130), "the walk swing started a new combo");
    }

    @Test
    @DisplayName("同一個 tick 打兩下也在時間窗內")
    void sameTickCounts() {
        swing(WALK, 100);
        assertEquals(Optional.of("lunge"), swing(RUN, 100));
    }

    @Test
    @DisplayName("時間倒退不觸發，這一下變成新的第一下")
    void tickGoingBackwardsStartsOver() {
        swing(WALK, 100);
        assertEquals(Optional.empty(), swing(RUN, 90));
        assertEquals(Optional.of("follow_up"), swing(WALK, 95));
    }

    @Test
    @DisplayName("B16 reset 後（死亡、登出）沒有上一下")
    void resetForgetsTheLastSwing() {
        swing(WALK, 100);
        tracker.reset();
        assertEquals(Optional.empty(), swing(RUN, 105));
    }

    @Test
    @DisplayName("B15 每個玩家各一份：A 的上一下不會被 B 用到")
    void b15TrackersAreSeparate() {
        ComboTracker a = new ComboTracker();
        ComboTracker b = new ComboTracker();
        a.record(DEFAULTS, WALK, 100);
        assertEquals(Optional.empty(), b.record(DEFAULTS, RUN, 105));
        assertTrue(a.record(DEFAULTS, RUN, 110).isPresent());
    }

    @Test
    @DisplayName("每個 combo 用自己的時間窗：檔案寫 10 tick 的 combo，第 11 tick 就太晚")
    void eachComboHasItsOwnWindow() {
        ComboCatalog quick = ComboCatalog.of(List.of(
            new ComboDefinition("quick", "Quick", WALK, RUN, 10, new DamageMultiplier(2))));
        tracker.record(quick, WALK, 100);
        assertEquals(Optional.empty(), tracker.record(quick, RUN, 111));
        tracker.record(quick, WALK, 200);
        assertTrue(tracker.record(quick, RUN, 210).isPresent());
    }

    @Test
    @DisplayName("reload 換掉 combo 後，下一下就用新的清單：第一下仍然記得")
    void aReloadAppliesOnTheNextSwing() {
        swing(WALK, 100);
        ComboCatalog reloaded = ComboCatalog.of(List.of(
            new ComboDefinition("charge", "Charge", WALK, RUN, 30, new DamageMultiplier(2))));
        assertEquals(Optional.of("charge"), tracker.record(reloaded, RUN, 110).map(ComboDefinition::id));
    }

    @Test
    @DisplayName("沒有任何 combo 檔時，怎麼打都不會觸發")
    void noCombosNeverTrigger() {
        tracker.record(ComboCatalog.EMPTY, WALK, 100);
        assertEquals(Optional.empty(), tracker.record(ComboCatalog.EMPTY, RUN, 101));
    }
}
