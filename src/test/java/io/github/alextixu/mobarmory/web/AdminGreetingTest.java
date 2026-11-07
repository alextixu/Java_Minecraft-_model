package io.github.alextixu.mobarmory.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a player is told in chat about the web admin when they join (web fixes B1-1 to B1-3). */
class AdminGreetingTest {
    private static final String TOKEN = "s3cret token&more";
    private AdminHttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    private AdminHttpServer start(String token) throws IOException {
        server = AdminHttpServer.start(new TestWeaponAdmin(), new TestMobAdmin(), new WebSettings(true, "127.0.0.1", 0, token));
        return server;
    }

    @Test
    @DisplayName("B1-1 Given 設了 token、網頁開著 When OP 進世界 Then 點下去開的是帶 URL 編碼 token 的網址；聊天欄的文字不帶 token")
    void gameMasterGetsTheLinkWithTheToken() throws IOException {
        start(TOKEN);
        AdminGreeting.Link link = assertInstanceOf(AdminGreeting.Link.class, AdminGreeting.forPlayer(true, server, null));

        assertEquals(URI.create("http://127.0.0.1:" + server.port() + "/?token=s3cret+token%26more"), link.open());
        assertEquals(server.uri(), link.shown());
        assertFalse(link.shown().toString().contains("s3cret"), "the chat text is written to the player's log");
    }

    @Test
    @DisplayName("B1-2 Given 沒設 token When OP 進世界 Then 連結和以前一樣，沒有 ?token=")
    void noTokenNoQuery() throws IOException {
        start("");
        AdminGreeting.Link link = assertInstanceOf(AdminGreeting.Link.class, AdminGreeting.forPlayer(true, server, null));

        assertEquals(URI.create("http://127.0.0.1:" + server.port() + "/"), link.open());
        assertEquals(link.shown(), link.open());
    }

    @Test
    @DisplayName("B1-3 Given 設了 token When 不是 OP 的玩家進世界 Then 什麼都收不到（網頁開著或沒開成都一樣）")
    void othersGetNothing() throws IOException {
        start(TOKEN);
        assertInstanceOf(AdminGreeting.Silent.class, AdminGreeting.forPlayer(false, server, null));
        assertInstanceOf(AdminGreeting.Silent.class, AdminGreeting.forPlayer(false, null, "port 8765 is taken"));
    }

    @Test
    @DisplayName("網頁沒開成：OP 看到原因；網頁關掉（沒有原因）：什麼都不說，和以前一樣")
    void notRunning() {
        assertEquals(new AdminGreeting.Failed("port 8765 is taken"), AdminGreeting.forPlayer(true, null, "port 8765 is taken"));
        assertInstanceOf(AdminGreeting.Silent.class, AdminGreeting.forPlayer(true, null, null));
    }
}
