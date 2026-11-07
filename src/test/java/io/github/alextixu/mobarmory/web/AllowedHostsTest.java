package io.github.alextixu.mobarmory.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Who the web admin answers when web.json says 0.0.0.0, and which address it shows (web fixes B2-1 to B2-4). The
 * computer's addresses are given, so nothing here depends on the real network.
 */
class AllowedHostsTest {
    private static final int PORT = 8765;
    private static final InetAddress LAN_IPV4 = ip("192.168.1.52");
    private static final InetAddress LINK_LOCAL_WITH_ZONE = withZone("fe80::1", 7);
    /** What a LAN server might list: loopbacks, an IPv6 link-local address with its zone, the LAN address. */
    private static final List<InetAddress> LOCAL = List.of(ip("127.0.0.1"), ip("::1"), LINK_LOCAL_WITH_ZONE, LAN_IPV4);

    private static InetAddress ip(String literal) {
        return InetAddress.ofLiteral(literal);
    }

    private static InetAddress withZone(String literal, int zone) {
        try {
            return Inet6Address.getByAddress(null, ip(literal).getAddress(), zone);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private static WebSettings settings(String host) {
        return new WebSettings(true, host, PORT, "long-random-token");
    }

    @Test
    @DisplayName("B2-1 Given host 0.0.0.0（或 ::） When 用這台電腦任一個 IP 連 Then 放行（IPv6 不管 zone）")
    void everyAddressOfThisComputer() {
        for (String host : List.of("0.0.0.0", "::")) {
            AllowedHosts allowed = AllowedHosts.of(settings(host), PORT, LOCAL);
            assertTrue(allowed.allows("192.168.1.52:8765"), host);
            assertTrue(allowed.allows("[fe80::1]:8765"), host + ": the browser never sends the zone");
            assertTrue(allowed.allows("[FE80:0:0:0:0:0:0:1]:8765"), host + ": any way of writing the same address");
            assertTrue(allowed.allows("127.0.0.1:8765"), host);
            assertTrue(allowed.allows("localhost:8765"), host);
        }
    }

    @Test
    @DisplayName("B2-2 Given host 0.0.0.0 When Host 是別的名稱、別台電腦的 IP、別的埠 Then 一樣不放行（防 DNS rebinding）")
    void anythingElseIsStillRefused() {
        AllowedHosts allowed = AllowedHosts.of(settings("0.0.0.0"), PORT, LOCAL);
        assertFalse(allowed.allows("evil.example:8765"), "a name is never looked up");
        assertFalse(allowed.allows("10.9.9.9:8765"), "not an address of this computer");
        assertFalse(allowed.allows("192.168.1.52:9999"), "another port");
        assertFalse(allowed.allows("192.168.1.52"), "no port");
        assertFalse(allowed.allows("fe80::1:8765"), "IPv6 without brackets");
        assertFalse(allowed.allows("[fe80::1]"), "no port");
        assertFalse(allowed.allows(null));
    }

    @Test
    @DisplayName("B2-4 Given host 是 127.0.0.1 或指定的 IP Then 和以前一樣：只放行那幾個名稱，不看網卡")
    void otherSettingsAreUnchanged() {
        AllowedHosts local = AllowedHosts.of(settings("127.0.0.1"), PORT, LOCAL);
        assertTrue(local.allows("127.0.0.1:8765"));
        assertTrue(local.allows("LOCALHOST:8765"), "compared in lower case, as before");
        assertTrue(local.allows("[::1]:8765"));
        assertFalse(local.allows("192.168.1.52:8765"), "a local-only server does not answer the LAN address");

        AllowedHosts fixed = AllowedHosts.of(settings("192.168.1.52"), PORT, List.of(LAN_IPV4, ip("10.0.0.5")));
        assertTrue(fixed.allows("192.168.1.52:8765"));
        assertFalse(fixed.allows("10.0.0.5:8765"), "only the host in web.json, even if the computer has another address");
    }

    @Test
    @DisplayName("B2-3 Given host 0.0.0.0 Then 顯示的網址：先挑私有網段的 IPv4，沒有就第一個非 loopback，都沒有就 127.0.0.1")
    void shownAddress() {
        WebSettings all = settings("0.0.0.0");
        assertEquals("192.168.1.52", AdminHttpServer.shownHost(all,
            List.of(ip("127.0.0.1"), withZone("fe80::1", 7), ip("203.0.113.7"), LAN_IPV4)), "a private IPv4 address first");
        assertEquals("203.0.113.7", AdminHttpServer.shownHost(all, List.of(ip("127.0.0.1"), ip("::1"), ip("203.0.113.7"))));
        assertEquals("[fe80:0:0:0:0:0:0:1]", AdminHttpServer.shownHost(all, List.of(ip("127.0.0.1"), withZone("fe80::1", 7))),
            "IPv6 in brackets, without the zone");
        assertEquals("127.0.0.1", AdminHttpServer.shownHost(all, List.of(ip("127.0.0.1"), ip("::1"))));
        assertEquals("127.0.0.1", AdminHttpServer.shownHost(all, List.of()), "the network could not be read");
    }

    @Test
    @DisplayName("B2-4 顯示的網址：127.0.0.1、localhost 顯示 127.0.0.1，指定的 IP 照寫，和以前一樣")
    void shownAddressOfOtherSettings() {
        assertEquals("127.0.0.1", AdminHttpServer.shownHost(settings("127.0.0.1"), LOCAL));
        assertEquals("127.0.0.1", AdminHttpServer.shownHost(settings("localhost"), LOCAL));
        assertEquals("192.168.1.52", AdminHttpServer.shownHost(settings("192.168.1.52"), LOCAL));
    }

    @Test
    @DisplayName("B2-1 B2-4 什麼算「所有位址」：只有 0.0.0.0 和 ::，名稱不查 DNS；對外開放一樣要 token")
    void listensOnAll() {
        assertTrue(settings("0.0.0.0").listensOnAll());
        assertTrue(settings("::").listensOnAll());
        assertFalse(settings("127.0.0.1").listensOnAll());
        assertFalse(settings("192.168.1.52").listensOnAll());
        assertFalse(settings("server.example").listensOnAll());
        assertThrows(IllegalStateException.class, () -> new WebSettings(true, "::", PORT, "").checkSafe());
        assertThrows(IllegalStateException.class, () -> new WebSettings(true, "0.0.0.0", PORT, "").checkSafe());
    }

    @Test
    @DisplayName("網卡位址：讀這台電腦的網卡不會丟例外（讀不到就是空清單）")
    void systemAddressesNeverThrow() {
        assertDoesNotThrow(() -> LocalAddresses.system().all());
    }
}
