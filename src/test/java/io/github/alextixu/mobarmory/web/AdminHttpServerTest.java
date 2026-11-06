package io.github.alextixu.mobarmory.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModMobs;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** HTTP-level tests: a real server on a free local port, the page, and who may change things (Q6, B13, B14). */
class AdminHttpServerTest {
    private static final String SWORD = """
        {"format": 1, "name": "Heavy Sword", "slot": "mainhand", "look": {"item": "minecraft:netherite_sword"},
         "modifiers": [{"stat": "attack_damage", "add": 16}, {"stat": "movement_speed", "percent": -30},
                       {"stat": "attack_speed", "percent": -40}]}""";

    private final HttpClient client = HttpClient.newHttpClient();
    private final TestWeaponAdmin admin = new TestWeaponAdmin();
    private AdminHttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    private AdminHttpServer start(String token) throws IOException {
        server = AdminHttpServer.start(admin, ModMobs::catalog, new WebSettings(true, "127.0.0.1", 0, token));
        return server;
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder put(String path, String contentType, String body) {
        return HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType).PUT(HttpRequest.BodyPublishers.ofString(body));
    }

    /** A raw request, to send a Host header that HttpClient would not let us forge. */
    private String rawStatusLine(String host) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET /api/v1/weapons HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().findFirst().orElse("");
        }
    }

    @Test
    @DisplayName("網頁本身：HTML、JS、CSS 都從模組 jar 送出，有安全標頭，不連外部")
    void servesThePage() throws Exception {
        start("");
        HttpResponse<String> page = send(HttpRequest.newBuilder(uri("/")));

        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Mob Armory 武器後台"));
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'self'"));
        assertEquals("nosniff", page.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertFalse(page.body().contains("http://") || page.body().contains("https://"), "the page must not load anything from outside");
        assertEquals(200, send(HttpRequest.newBuilder(uri("/app.js"))).statusCode());
        assertEquals(200, send(HttpRequest.newBuilder(uri("/style.css"))).statusCode());
        assertEquals(404, send(HttpRequest.newBuilder(uri("/../config/web.json"))).statusCode());
    }

    @Test
    @DisplayName("H4、H5 透過 HTTP 讀清單、存檔都正常（JSON）")
    void apiWorksOverHttp() throws Exception {
        start("");
        assertEquals(200, send(HttpRequest.newBuilder(uri("/api/v1/weapons"))).statusCode());

        HttpResponse<String> saved = send(put("/api/v1/weapons/heavy_sword", "application/json", SWORD)
            .header("Origin", "http://127.0.0.1:" + server.port()));

        assertEquals(200, saved.statusCode(), saved.body());
    }

    @Test
    @DisplayName("H10 透過 HTTP 讀怪物清單（JSON）；怪物只能看，送修改 → 405")
    void mobsAreReadOnlyOverHttp() throws Exception {
        start("");
        HttpResponse<String> mobs = send(HttpRequest.newBuilder(uri("/api/v1/mobs")));

        assertEquals(200, mobs.statusCode(), mobs.body());
        assertTrue(mobs.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(mobs.body().contains("\"brute\"") && mobs.body().contains("\"skirmisher\""), mobs.body());

        HttpResponse<String> change = send(HttpRequest.newBuilder(uri("/api/v1/mobs"))
            .header("Content-Type", "application/json").header("Origin", "http://127.0.0.1:" + server.port())
            .POST(HttpRequest.BodyPublishers.ofString("{}")));
        assertEquals(405, change.statusCode(), change.body());
    }

    @Test
    @DisplayName("B13 別的網站送來的修改（Origin 不是這個網頁）→ 403，什麼都沒改")
    void otherSitesCannotChangeThings() throws Exception {
        start("");
        HttpResponse<String> response = send(put("/api/v1/weapons/heavy_sword", "application/json", SWORD)
            .header("Origin", "http://evil.example"));

        assertEquals(403, response.statusCode());
        assertEquals(6.0, admin.library.current().get(io.github.alextixu.mobarmory.core.equipment.EquipmentId.of("heavy_sword"))
            .modifiers().get(0).amount());
    }

    @Test
    @DisplayName("B13 不是 JSON 的修改（例如表單送出的 text/plain，瀏覽器不會先詢問）→ 415")
    void onlyJsonChanges() throws Exception {
        start("");
        assertEquals(415, send(put("/api/v1/weapons/heavy_sword", "text/plain", SWORD)).statusCode());
        assertEquals(415, send(put("/api/v1/textures/x", "application/json", "{}")).statusCode());
    }

    @Test
    @DisplayName("B13 Host 不是這台伺服器（DNS rebinding）→ 403")
    void foreignHostIsRejected() throws Exception {
        start("");
        assertTrue(rawStatusLine("evil.example:" + server.port()).contains(" 403 "));
        assertTrue(rawStatusLine("localhost:" + server.port()).contains(" 200 "));
    }

    @Test
    @DisplayName("太大的請求 → 413")
    void tooLarge() throws Exception {
        start("");
        String big = "{\"name\": \"" + "x".repeat(AdminHttpServer.MAX_JSON_BYTES) + "\"}";
        assertEquals(413, send(put("/api/v1/weapons/heavy_sword", "application/json", big)).statusCode());
    }

    @Test
    @DisplayName("Q6 設了 token：API 沒帶或帶錯 → 401，帶對才行；網頁本身不用")
    void tokenWhenConfigured() throws Exception {
        start("s3cret-token");
        assertEquals(401, send(HttpRequest.newBuilder(uri("/api/v1/weapons"))).statusCode());
        assertEquals(401, send(HttpRequest.newBuilder(uri("/api/v1/weapons")).header("Authorization", "Bearer wrong")).statusCode());
        assertEquals(200, send(HttpRequest.newBuilder(uri("/api/v1/weapons")).header("Authorization", "Bearer s3cret-token")).statusCode());
        assertEquals(200, send(HttpRequest.newBuilder(uri("/"))).statusCode());
    }

    @Test
    @DisplayName("Q6 位址對外開放卻沒有 token → 不啟動；本機位址不用 token")
    void openAddressNeedsAToken() {
        assertThrows(IllegalStateException.class, () -> new WebSettings(true, "0.0.0.0", 8765, "").checkSafe());
        new WebSettings(true, "0.0.0.0", 8765, "long-random-token").checkSafe();
        new WebSettings(true, "127.0.0.1", 8765, "").checkSafe();
        assertTrue(new WebSettings(true, "localhost", 8765, "").isLocalOnly());
    }

    @Test
    @DisplayName("B14 埠已經被佔用 → 丟出 IOException（遊戲端只記警告，照常進行）")
    void portInUse() throws Exception {
        start("");
        assertThrows(IOException.class, () -> AdminHttpServer.start(admin, ModMobs::catalog, new WebSettings(true, "127.0.0.1", server.port(), "")));
    }

    @Test
    @DisplayName("O5 web.json 不存在時建立預設（127.0.0.1:8765，開啟，沒有 token）")
    void settingsFileDefaults(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("web.json");
        assertEquals(WebSettings.DEFAULT, WebSettings.load(file));
        assertEquals(WebSettings.DEFAULT, WebSettings.load(file));
        assertEquals(new WebSettings(false, "127.0.0.1", 9000, ""), WebSettings.load(writeSettings(file, "{\"enabled\": false, \"port\": 9000}")));
    }

    private static Path writeSettings(Path file, String json) throws IOException {
        java.nio.file.Files.writeString(file, json);
        return file;
    }
}
