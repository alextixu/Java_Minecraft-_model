package io.github.alextixu.mobarmory.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
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
    private final TestMobAdmin mobs = new TestMobAdmin();
    private AdminHttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    private AdminHttpServer start(String token) throws IOException {
        server = AdminHttpServer.start(admin, mobs, new WebSettings(true, "127.0.0.1", 0, token));
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

    /**
     * A server that acts as if web.json said 0.0.0.0 on a computer whose LAN address is 192.168.1.52, while it really
     * listens on 127.0.0.1 only (web fixes B2): no test depends on the network or opens a port to it.
     */
    private AdminHttpServer startOnAllAddresses(String token) throws IOException {
        return startOnAllAddresses(token, false);
    }

    private AdminHttpServer startOnAllAddresses(String token, boolean allowNetworkWithoutToken) throws IOException {
        server = AdminHttpServer.start(admin, mobs, new WebSettings(true, "0.0.0.0", 0, token, allowNetworkWithoutToken),
            LocalAddresses.of(InetAddress.ofLiteral("127.0.0.1"), InetAddress.ofLiteral("192.168.1.52")),
            new InetSocketAddress("127.0.0.1", 0));
        return server;
    }

    /** A raw request, to send a Host header that HttpClient would not let us forge. */
    private String rawStatusLine(String host, String... headers) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            OutputStream out = socket.getOutputStream();
            String extra = String.join("", java.util.Arrays.stream(headers).map(header -> header + "\r\n").toList());
            out.write(("GET /api/v1/weapons HTTP/1.1\r\nHost: " + host + "\r\n" + extra + "Connection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
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
        assertTrue(page.body().contains("Mob Armory 後台"), "R4b: the page is for weapons and mobs");
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
    @DisplayName("H13 H4 透過 HTTP 讀怪物清單（JSON），再從這個網頁存狂戰士（R4b：原本的唯讀清單改成可以存檔）")
    void mobsOverHttp() throws Exception {
        start("");
        HttpResponse<String> list = send(HttpRequest.newBuilder(uri("/api/v1/mobs")));

        assertEquals(200, list.statusCode(), list.body());
        assertTrue(list.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(list.body().contains("\"brute\"") && list.body().contains("\"skirmisher\""), list.body());

        String brute = io.github.alextixu.mobarmory.data.DefaultMobs.text("brute").replace("\"attack_damage\": 4", "\"attack_damage\": 8");
        HttpResponse<String> change = send(HttpRequest.newBuilder(uri("/api/v1/mobs/brute"))
            .header("Content-Type", "application/json").header("Origin", "http://127.0.0.1:" + server.port())
            .PUT(HttpRequest.BodyPublishers.ofString(brute)));
        assertEquals(200, change.statusCode(), change.body());
        assertEquals(8.0, mobs.library.current().get(io.github.alextixu.mobarmory.data.DefaultMobs.BRUTE).stats()
            .get(io.github.alextixu.mobarmory.core.stat.StatId.of("attack_damage")));

        HttpResponse<String> otherSite = send(HttpRequest.newBuilder(uri("/api/v1/mobs/brute"))
            .header("Content-Type", "application/json").header("Origin", "http://evil.example")
            .PUT(HttpRequest.BodyPublishers.ofString(brute.replace("\"attack_damage\": 8", "\"attack_damage\": 1"))));
        assertEquals(403, otherSite.statusCode(), "the same Origin check as weapons (B13)");
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
    @DisplayName("B2-1 Given host 0.0.0.0、有 token When 區網用 192.168.1.52 連 Then Host 檢查通過，帶 token 讀得到，從這個網址存檔也行")
    void lanAddressWorksWhenListeningOnAll() throws Exception {
        startOnAllAddresses("s3cret-token");
        String lan = "192.168.1.52:" + server.port();

        assertTrue(rawStatusLine(lan, "Authorization: Bearer s3cret-token").contains(" 200 "));
        assertTrue(rawStatusLine(lan).contains(" 401 "), "the Host passes; the token is still required");
        HttpResponse<String> saved = send(put("/api/v1/weapons/heavy_sword", "application/json", SWORD)
            .header("Authorization", "Bearer s3cret-token").header("Origin", "http://" + lan));
        assertEquals(200, saved.statusCode(), "Origin is checked against the same list: " + saved.body());
    }

    @Test
    @DisplayName("B2-2 Given host 0.0.0.0 When Host 或 Origin 是別的名稱 Then 一樣 403")
    void otherNamesStillRejectedWhenListeningOnAll() throws Exception {
        startOnAllAddresses("s3cret-token");
        String status = rawStatusLine("evil.example:" + server.port(), "Authorization: Bearer s3cret-token");
        assertTrue(status.contains(" 403 "), status);
        assertTrue(rawStatusLine("10.9.9.9:" + server.port(), "Authorization: Bearer s3cret-token").contains(" 403 "),
            "an IP address that is not this computer's");
        HttpResponse<String> otherSite = send(put("/api/v1/weapons/heavy_sword", "application/json", SWORD)
            .header("Authorization", "Bearer s3cret-token").header("Origin", "http://evil.example"));
        assertEquals(403, otherSite.statusCode());
    }

    @Test
    @DisplayName("B2-3 Given host 0.0.0.0 Then uri()（log 和聊天欄用的）顯示這台電腦的區網 IP，不是 0.0.0.0")
    void shownAddressWhenListeningOnAll() throws Exception {
        startOnAllAddresses("s3cret-token");
        assertEquals(URI.create("http://192.168.1.52:" + server.port() + "/"), server.uri());
    }

    @Test
    @DisplayName("B1-1 B1-3 Given 設了 token Then 聊天欄的連結帶 URL 編碼的 token、點了就能用；uri()（log 用的）不帶 token")
    void linkCarriesTheToken() throws Exception {
        String token = "s3cret/+&=token";
        start(token);
        URI link = server.linkWithToken();

        assertEquals(URI.create(server.uri() + "?token=s3cret%2F%2B%26%3Dtoken"), link);
        String fromLink = URLDecoder.decode(link.getRawQuery().substring("token=".length()), StandardCharsets.UTF_8);
        assertEquals(token, fromLink, "the page reads back exactly the token");
        assertEquals(200, send(HttpRequest.newBuilder(uri("/api/v1/weapons")).header("Authorization", "Bearer " + fromLink)).statusCode(),
            "no 401 with the token from the link");
        assertFalse(server.uri().toString().contains("token"), "the address that is logged never carries the token");
    }

    @Test
    @DisplayName("B1-2 Given 沒設 token Then 連結和以前一樣，沒有 ?token=")
    void linkWithoutToken() throws Exception {
        start("");
        assertEquals(server.uri(), server.linkWithToken());
        assertEquals(URI.create("http://127.0.0.1:" + server.port() + "/"), server.linkWithToken());
    }

    @Test
    @DisplayName("B1-3 B1-4 網頁：讀到網址上的 token 後從網址列拿掉（照樣存 sessionStorage）；401 的提示寫出兩種方法")
    void pageHandlesTheToken() throws Exception {
        start("");
        String script = send(HttpRequest.newBuilder(uri("/app.js"))).body();

        assertTrue(script.contains("history.replaceState("), "the token leaves the address bar");
        assertTrue(script.contains("params.delete('token')"), "only the token parameter is removed");
        assertTrue(script.contains("sessionStorage.setItem('mobarmory-token', fromUrl)"), "a reload still has the token");
        String hint = script.lines().filter(line -> line.contains("error.status === 401")).findFirst().orElse("");
        assertTrue(hint.contains("聊天欄") && hint.contains("OP"), "way 1: the link in chat: " + hint);
        assertTrue(hint.contains("?token=") && hint.contains("config/mobarmory/web.json"), "way 2: add ?token= from web.json: " + hint);
    }

    @Test
    @DisplayName("B3-1 Given host 0.0.0.0、沒有 token、allowNetworkWithoutToken 打開 When 區網用 IP 開 Then 照常啟動，不用 token 就能讀、能存")
    void lanWithoutTokenWhenAllowed() throws Exception {
        startOnAllAddresses("", true);
        String lan = "192.168.1.52:" + server.port();

        assertTrue(rawStatusLine(lan).contains(" 200 "), "no Authorization header at all");
        HttpResponse<String> saved = send(put("/api/v1/weapons/heavy_sword", "application/json", SWORD).header("Origin", "http://" + lan));
        assertEquals(200, saved.statusCode(), saved.body());
        assertEquals(server.uri(), server.linkWithToken(), "no token, so the chat link has none either");
    }

    @Test
    @DisplayName("B3-2 Given 開關沒打開（預設） When host 對外、沒有 token Then 和以前一樣不啟動")
    void switchOffStillRefusesToStart() {
        assertThrows(IllegalStateException.class, () -> startOnAllAddresses("", false));
        assertThrows(IllegalStateException.class, () -> new WebSettings(true, "192.168.1.52", 8765, "").checkSafe());
        assertFalse(WebSettings.DEFAULT.allowNetworkWithoutToken());
    }

    @Test
    @DisplayName("B3-3 Given 開關打開、沒有 token、host 對外 Then 有一行警告（每次啟動寫進 log）；其他情況沒有")
    void warningOnlyWhenOpenWithoutToken() {
        String warning = new WebSettings(true, "0.0.0.0", 8765, "", true).securityWarning().orElseThrow();
        assertTrue(warning.contains("allowNetworkWithoutToken") && warning.contains("0.0.0.0:8765"), warning);
        assertTrue(new WebSettings(true, "192.168.1.52", 8765, "", true).securityWarning().isPresent());
        assertTrue(new WebSettings(true, "0.0.0.0", 8765, "s3cret", true).securityWarning().isEmpty(), "a token is set");
        assertTrue(new WebSettings(true, "127.0.0.1", 8765, "", true).securityWarning().isEmpty(), "only this computer");
        assertTrue(new WebSettings(true, "0.0.0.0", 8765, "", false).securityWarning().isEmpty(), "switch off: it does not start");
    }

    @Test
    @DisplayName("B3-4 Given 開關打開、沒有 token When Host 或 Origin 是別的名稱 Then 一樣 403")
    void hostAndOriginStillCheckedWithoutToken() throws Exception {
        startOnAllAddresses("", true);
        assertTrue(rawStatusLine("evil.example:" + server.port()).contains(" 403 "));
        HttpResponse<String> otherSite = send(put("/api/v1/weapons/heavy_sword", "application/json", SWORD)
            .header("Origin", "http://evil.example"));
        assertEquals(403, otherSite.statusCode());
    }

    @Test
    @DisplayName("B3-5 Given 開關打開、也有 token Then 照 token 的規則：沒帶 401，帶對 200")
    void tokenStillWinsWhenSet() throws Exception {
        startOnAllAddresses("s3cret-token", true);
        String lan = "192.168.1.52:" + server.port();
        assertTrue(rawStatusLine(lan).contains(" 401 "));
        assertTrue(rawStatusLine(lan, "Authorization: Bearer s3cret-token").contains(" 200 "));
    }

    @Test
    @DisplayName("B3-1 B3-2 web.json：沒寫開關就是關；寫 true 才打開；預設的檔案會寫出這個開關（false）")
    void switchInTheFile(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("web.json");
        WebSettings.load(file);
        assertTrue(java.nio.file.Files.readString(file).contains("\"allowNetworkWithoutToken\": false"));
        assertFalse(WebSettings.load(writeSettings(file, "{\"host\": \"0.0.0.0\"}")).allowNetworkWithoutToken());
        assertTrue(WebSettings.load(writeSettings(file, "{\"host\": \"0.0.0.0\", \"allowNetworkWithoutToken\": true}")).allowNetworkWithoutToken());
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
        assertThrows(IOException.class, () -> AdminHttpServer.start(admin, mobs, new WebSettings(true, "127.0.0.1", server.port(), "")));
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
