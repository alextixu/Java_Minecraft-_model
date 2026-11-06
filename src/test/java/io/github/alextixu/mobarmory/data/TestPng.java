package io.github.alextixu.mobarmory.data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** PNG headers for tests; {@link PngCheck} only reads the header. */
final class TestPng {
    private TestPng() {
    }

    static byte[] of(int width, int height) {
        ByteBuffer png = ByteBuffer.allocate(33);
        png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        png.putInt(13).put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(width).putInt(height);
        png.put(new byte[] {8, 6, 0, 0, 0}).putInt(0); // 8-bit RGBA, then a (dummy) checksum
        return png.array();
    }
}
