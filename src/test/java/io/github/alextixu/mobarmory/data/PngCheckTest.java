package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Component tests: which PNG files can be weapon textures (B4). */
class PngCheckTest {

    @ParameterizedTest(name = "{0}x{0} 可以用")
    @CsvSource({"16", "32", "64", "128", "256"})
    void squarePowersOfTwoAreFine(int size) {
        assertEquals(Optional.empty(), PngCheck.problem(TestPng.of(size, size)));
    }

    @ParameterizedTest(name = "{0}x{1} 不行：{2}")
    @CsvSource({"32, 16, square", "24, 24, '16, 32'", "8, 8, '16, 32'", "512, 512, '16, 32'"})
    void otherSizesAreRejected(int width, int height, String reason) {
        String problem = PngCheck.problem(TestPng.of(width, height)).orElseThrow();
        assertTrue(problem.contains(reason.split(",")[0].strip()), problem);
    }

    @Test
    @DisplayName("不是 PNG（例如 JPEG 或文字檔）不行")
    void notAPng() {
        assertTrue(PngCheck.problem("hello, this is not an image at all".getBytes(StandardCharsets.UTF_8)).orElseThrow().contains("not a PNG"));
        assertTrue(PngCheck.problem(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}).orElseThrow().contains("not a PNG"));
    }

    @Test
    @DisplayName("超過 256 KB 不行")
    void tooLarge() {
        byte[] big = new byte[PngCheck.MAX_BYTES + 1];
        System.arraycopy(TestPng.of(32, 32), 0, big, 0, 33);
        assertTrue(PngCheck.problem(big).orElseThrow().contains("256 KB"));
    }
}
