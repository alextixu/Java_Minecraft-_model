package io.github.alextixu.mobarmory.data;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Checks that a file is a texture Minecraft can use as an item icon (B4): a PNG, square, 16 to 256 pixels and a
 * power of two (the hanhanpx pack has 16x16 and 32x32), at most 256 KB. Only the header is read; drawing the
 * pixels is left to Minecraft.
 */
public final class PngCheck {
    public static final int MIN_SIZE = 16;
    public static final int MAX_SIZE = 256;
    public static final int MAX_BYTES = 256 * 1024;

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngCheck() {
    }

    /** What is wrong with the file, or empty if it can be used. */
    public static Optional<String> problem(byte[] bytes) {
        if (bytes.length > MAX_BYTES) {
            return Optional.of("is larger than " + MAX_BYTES / 1024 + " KB");
        }
        if (bytes.length < 24 || !startsWithSignature(bytes) || !"IHDR".equals(new String(bytes, 12, 4, StandardCharsets.US_ASCII))) {
            return Optional.of("is not a PNG file");
        }
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        if (width != height) {
            return Optional.of("must be square, got " + width + "x" + height);
        }
        if (width < MIN_SIZE || width > MAX_SIZE || Integer.bitCount(width) != 1) {
            return Optional.of("must be 16, 32, 64, 128 or 256 pixels wide, got " + width + "x" + height);
        }
        return Optional.empty();
    }

    private static boolean startsWithSignature(byte[] bytes) {
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (bytes[i] != SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
            | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }
}
