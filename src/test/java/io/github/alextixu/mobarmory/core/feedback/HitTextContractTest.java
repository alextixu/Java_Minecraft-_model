package io.github.alextixu.mobarmory.core.feedback;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.combat.DamageKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract for every line {@link HitText} can produce: for any result, any viewer and any number another mod
 * might hand us, it never throws, never prints "NaN" or a minus sign inside a number, never shows 0.0 for a hit
 * that took health, and only uses language keys that exist in en_us.json.
 */
class HitTextContractTest {
    private static final Path LANG = Path.of("src/main/resources/assets/mobarmory/lang/en_us.json");
    private static final double[] ODD_NUMBERS = {
        0, 1e-9, 0.04, 0.05, 6.888, 20, 1e38, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, -1, -0.0,
    };

    record Side(Viewer viewer, HitResult result) {
        @Override
        public String toString() {
            return viewer + " " + result;
        }
    }

    /** One case per viewer and result; each case runs every odd number for damage, health and max health. */
    static List<Side> sides() {
        List<Side> sides = new ArrayList<>();
        for (Viewer viewer : Viewer.values()) {
            for (HitResult result : HitResult.values()) {
                sides.add(new Side(viewer, result));
            }
        }
        return sides;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("任何數值：不丟例外、不印 NaN、數字沒有負號、有扣血不會是 0.0")
    void anyLineIsReadable(Side side) {
        List<HitReport> reports = new ArrayList<>();
        for (double damage : ODD_NUMBERS) {
            for (double health : ODD_NUMBERS) {
                reports.add(new HitReport(DamageKind.MELEE, Party.PLAYER, Party.PLAYER, side.result(), damage, health, 20));
            }
            reports.add(new HitReport(DamageKind.MELEE, Party.PLAYER, Party.PLAYER, side.result(), 1, 10, damage));
        }
        for (HitReport report : reports) {
            HitLine line = assertDoesNotThrow(() -> HitText.line(report, side.viewer()), report::toString);

            for (String number : List.of(line.damage(), line.health(), line.maxHealth())) {
                assertFalse(number.isEmpty(), "empty number in " + line + " for " + report);
                assertFalse(number.contains("NaN"), "NaN in " + line + " for " + report);
                assertFalse(number.startsWith("-"), "minus sign in " + line + " for " + report);
            }
            double damage = report.damage();
            if (damage > 0 && Double.isFinite(damage)) {
                assertNotEquals("0.0", line.damage(), "a hit that took health shows 0.0: " + report);
                assertNotEquals("0", line.damage(), "a hit that took health shows 0: " + report);
            }
            assertTrue(line.translationKey().startsWith(HitText.KEY_PREFIX), line.translationKey());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.github.alextixu.mobarmory.core.feedback.HitText#allKeys")
    @DisplayName("每個 action bar 字串都在 en_us.json 裡（O8）")
    void everyKeyIsTranslated(String key) throws IOException {
        assertTrue(Files.readString(LANG).contains("\"" + key + "\""), "en_us.json is missing " + key);
    }
}
