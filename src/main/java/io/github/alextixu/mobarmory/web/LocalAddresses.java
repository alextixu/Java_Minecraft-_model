package io.github.alextixu.mobarmory.web;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * The IP addresses of this computer, for a web admin that listens on every address (web.json host {@code 0.0.0.0} or
 * {@code ::}; web fixes B2-1, B2-3). Strategy: the game reads the network interfaces ({@link #system()}), tests give a
 * fixed list ({@link #of}), so no test depends on the machine's network.
 * <p>
 * Every implementation: never throws; the list may be empty.
 */
@FunctionalInterface
public interface LocalAddresses {

    /** Every address, in the order the source lists them. */
    List<InetAddress> all();

    /** The addresses of the network interfaces that are up, in the order the JDK lists them. */
    static LocalAddresses system() {
        return () -> {
            List<InetAddress> addresses = new ArrayList<>();
            Enumeration<NetworkInterface> networks;
            try {
                networks = NetworkInterface.getNetworkInterfaces();
            } catch (SocketException e) {
                return List.of();
            }
            if (networks == null) {
                return List.of();
            }
            for (NetworkInterface network : Collections.list(networks)) {
                try {
                    if (network.isUp()) {
                        addresses.addAll(Collections.list(network.getInetAddresses()));
                    }
                } catch (SocketException e) {
                    // this interface cannot be read; the others still count
                }
            }
            return List.copyOf(addresses);
        };
    }

    /** A fixed list (tests). */
    static LocalAddresses of(InetAddress... addresses) {
        List<InetAddress> fixed = List.of(addresses);
        return () -> fixed;
    }
}
