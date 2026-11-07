package io.github.alextixu.mobarmory.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Which {@code Host} headers, and which {@code Origin}s without their {@code http://}, the web admin answers (decision
 * Q6 A, B13; web fixes B2-1, B2-2). Immutable value, built once when the server starts.
 * <ul>
 *   <li>Always: {@code 127.0.0.1}, {@code localhost}, {@code [::1]} and the host in web.json, each with the port,
 *       compared as text, exactly as before.</li>
 *   <li>When web.json listens on every address ({@code 0.0.0.0} or {@code ::}): also every IP address of this computer
 *       with the port, so {@code http://192.168.1.52:8765/} works from the LAN. Only addresses written as IP addresses
 *       are compared and no name is ever looked up, so a site that points its own name at this computer (DNS
 *       rebinding) still gets 403.</li>
 * </ul>
 */
final class AllowedHosts {
    private final String port;
    private final Set<String> names;
    private final Set<String> addresses;

    private AllowedHosts(int port, Set<String> names, Set<String> addresses) {
        this.port = Integer.toString(port);
        this.names = Set.copyOf(names);
        this.addresses = Set.copyOf(addresses);
    }

    /**
     * @param port  the port the server really listens on
     * @param local this computer's addresses; only used when web.json listens on every address
     */
    static AllowedHosts of(WebSettings settings, int port, List<InetAddress> local) {
        Set<String> names = new LinkedHashSet<>(List.of("127.0.0.1:" + port, "localhost:" + port, "[::1]:" + port));
        names.add(settings.host().toLowerCase(Locale.ROOT) + ":" + port);
        Set<String> addresses = new LinkedHashSet<>();
        if (settings.listensOnAll()) {
            local.forEach(address -> addresses.add(text(address)));
        }
        return new AllowedHosts(port, names, addresses);
    }

    /** @param host a {@code Host} header, or an {@code Origin} without {@code http://}; null is never allowed */
    boolean allows(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.strip().toLowerCase(Locale.ROOT);
        if (names.contains(lower)) {
            return true;
        }
        if (addresses.isEmpty()) {
            return false;
        }
        String address;
        String portText;
        if (lower.startsWith("[")) { // [IPv6]:port
            int end = lower.indexOf(']');
            if (end < 0 || !lower.startsWith(":", end + 1)) {
                return false;
            }
            address = lower.substring(1, end);
            portText = lower.substring(end + 2);
        } else { // IPv4:port; a bare IPv6 address has more than one colon and is refused
            int colon = lower.indexOf(':');
            if (colon < 0 || colon != lower.lastIndexOf(':')) {
                return false;
            }
            address = lower.substring(0, colon);
            portText = lower.substring(colon + 1);
        }
        return portText.equals(port) && literal(address).map(AllowedHosts::text).filter(addresses::contains).isPresent();
    }

    /** An address as text without an IPv6 zone, so {@code fe80::1%eth0} and {@code [fe80::1]} compare equal. */
    static String text(InetAddress address) {
        try {
            return InetAddress.getByAddress(address.getAddress()).getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalStateException("the bytes of an InetAddress are always a valid address", e);
        }
    }

    /** The address, if the text is an IP address literal; never a DNS lookup. */
    private static Optional<InetAddress> literal(String text) {
        try {
            return Optional.of(InetAddress.ofLiteral(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
