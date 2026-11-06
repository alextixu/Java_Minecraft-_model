package io.github.alextixu.mobarmory.core.combo;

import static io.github.alextixu.mobarmory.core.combo.ComboInput.RUN;
import static io.github.alextixu.mobarmory.core.combo.ComboInput.WALK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: a combo or a combo list that is wrong is refused, with a message that names the combo. */
class ComboCatalogTest {
    private static final ComboEffect TIMES_2 = new DamageMultiplier(2);

    private static ComboDefinition combo(String id, ComboInput first, ComboInput second) {
        return new ComboDefinition(id, "Name", first, second, 30, TIMES_2);
    }

    @Test
    @DisplayName("用順序找得到 combo，沒定義的順序找不到")
    void findsByOrder() {
        ComboDefinition lunge = combo("lunge", WALK, RUN);
        ComboCatalog catalog = ComboCatalog.of(List.of(lunge));
        assertEquals(Optional.of(lunge), catalog.find(WALK, RUN));
        assertEquals(Optional.empty(), catalog.find(RUN, WALK));
        assertEquals(Optional.empty(), catalog.find(WALK, WALK));
        assertEquals(List.of(lunge), catalog.all());
    }

    @Test
    @DisplayName("同一個 id 兩次：拒絕")
    void duplicateIdFails() {
        var e = assertThrows(IllegalArgumentException.class,
            () -> ComboCatalog.of(List.of(combo("lunge", WALK, RUN), combo("lunge", RUN, WALK))));
        assertTrue(e.getMessage().contains("lunge"), e.getMessage());
    }

    @Test
    @DisplayName("兩個 combo 同一個順序（第二個永遠不會觸發）：拒絕，訊息寫出兩個 id")
    void duplicateOrderFails() {
        var e = assertThrows(IllegalArgumentException.class,
            () -> ComboCatalog.of(List.of(combo("lunge", WALK, RUN), combo("dash", WALK, RUN))));
        assertTrue(e.getMessage().contains("lunge") && e.getMessage().contains("dash"), e.getMessage());
    }

    @Test
    @DisplayName("id：小寫英文開頭，再接小寫英文、數字、底線（和武器、數值的 id 一樣）；名稱：1～32 字；時間窗：1～1200 tick")
    void definitionRules() {
        assertEquals("spin_2", combo("spin_2", WALK, RUN).id());
        for (String bad : List.of("", "Lunge", "follow-up", "a b", "../x", "2hit", "_spin")) {
            assertThrows(IllegalArgumentException.class, () -> combo(bad, WALK, RUN), bad);
        }
        assertEquals("Follow-up", new ComboDefinition("f", "  Follow-up ", RUN, WALK, 30, TIMES_2).name());
        for (String bad : List.of("", "   ", "x".repeat(33), "tab\there")) {
            assertThrows(IllegalArgumentException.class, () -> new ComboDefinition("f", bad, RUN, WALK, 30, TIMES_2), bad);
        }
        assertEquals(1, new ComboDefinition("f", "F", RUN, WALK, 1, TIMES_2).windowTicks());
        assertEquals(1200, new ComboDefinition("f", "F", RUN, WALK, 1200, TIMES_2).windowTicks());
        for (long bad : new long[] {0, -1, 1201}) {
            assertThrows(IllegalArgumentException.class, () -> new ComboDefinition("f", "F", RUN, WALK, bad, TIMES_2));
        }
        assertThrows(NullPointerException.class, () -> new ComboDefinition("f", "F", RUN, WALK, 30, null));
        assertThrows(NullPointerException.class, () -> new ComboDefinition("f", "F", null, WALK, 30, TIMES_2));
    }

    @Test
    @DisplayName("傷害倍率：1.0 × 1.5 = 1.5、3 × 1.3 = 3.9；倍率一定是正數")
    void damageMultiplier() {
        assertEquals(1.5, new DamageMultiplier(1.5).apply(1.0), 1e-9);
        assertEquals(3.9, new DamageMultiplier(1.3).apply(3.0), 1e-9);
        for (double bad : new double[] {0, -1.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new DamageMultiplier(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("ComboLibrary 整份換掉：讀的人拿到的是新的那一份")
    void libraryReplacesTheWholeCatalog() {
        ComboLibrary library = new ComboLibrary(ComboCatalog.EMPTY);
        ComboCatalog next = ComboCatalog.of(List.of(combo("lunge", WALK, RUN)));
        library.replace(next);
        assertSame(next, library.current());
    }
}
