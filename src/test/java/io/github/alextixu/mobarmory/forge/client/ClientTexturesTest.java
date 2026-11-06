package io.github.alextixu.mobarmory.forge.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component test: the PNGs a client received from a server are forgotten when it leaves (review-03 P2-8). */
class ClientTexturesTest {
    @AfterEach
    void empty() {
        ClientTextures.clear();
    }

    @Test
    @DisplayName("P2-8 Given 收過伺服器的兩張 PNG When 登出（clear）Then 兩張都不見了，資源包改用本機的檔案")
    void clearForgetsEverything() {
        ClientTextures.put("server_blade", new byte[] {1, 2, 3});
        ClientTextures.put("other", new byte[] {4});
        assertArrayEquals(new byte[] {1, 2, 3}, ClientTextures.get("server_blade").orElseThrow());

        ClientTextures.clear();

        assertTrue(ClientTextures.get("server_blade").isEmpty());
        assertTrue(ClientTextures.get("other").isEmpty());
    }
}
