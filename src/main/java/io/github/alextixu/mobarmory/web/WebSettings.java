package io.github.alextixu.mobarmory.web;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * {@code config/mobarmory/web.json} (decisions O5, Q6):
 * <pre>{@code { "enabled": true, "host": "127.0.0.1", "port": 8765, "token": "", "allowNetworkWithoutToken": false }}</pre>
 * Only this computer can reach the default address. Opening it to other computers requires a token, or the web
 * admin does not start (decision Q6 A), unless web.json says {@code "allowNetworkWithoutToken": true}: the user's
 * decision of 2026-11-08 (web fixes B3), so the LAN can open it by IP until another way to manage it exists. That
 * switch is off unless written, and every start with it on and no token logs a warning.
 */
public record WebSettings(boolean enabled, String host, int port, String token, boolean allowNetworkWithoutToken) {
    public static final WebSettings DEFAULT = new WebSettings(true, "127.0.0.1", 8765, "");

    public WebSettings {
        host = host == null || host.isBlank() ? DEFAULT.host() : host.strip();
        token = token == null ? "" : token.strip();
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("web.json: port must be 0 to 65535, got " + port);
        }
    }

    /** Settings with the switch off, as before web fixes B3. */
    public WebSettings(boolean enabled, String host, int port, String token) {
        this(enabled, host, port, token, false);
    }

    /** True when only this computer can connect. */
    public boolean isLocalOnly() {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * True when the host is {@code 0.0.0.0} or {@code ::}: the server listens on every address of this computer (web
     * fixes B2-1). Only an IP address literal counts; a name is never looked up here.
     */
    public boolean listensOnAll() {
        try {
            return InetAddress.ofLiteral(host).isAnyLocalAddress();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * @throws IllegalStateException if the address is reachable from other computers, there is no token, and web.json
     *                               does not explicitly allow that (web fixes B3-1, B3-2)
     */
    public void checkSafe() {
        if (!isLocalOnly() && token.isEmpty() && !allowNetworkWithoutToken) {
            throw new IllegalStateException("web.json: host '" + host + "' can be reached from other computers; set a "
                + "\"token\" (a long random password), use 127.0.0.1, or set \"allowNetworkWithoutToken\": true");
        }
    }

    /**
     * The warning to log at every start when anyone on the network can change weapons and mobs without a token (web
     * fixes B3-3); empty otherwise.
     */
    public Optional<String> securityWarning() {
        if (isLocalOnly() || !token.isEmpty() || !allowNetworkWithoutToken) {
            return Optional.empty();
        }
        return Optional.of("web.json: allowNetworkWithoutToken is on and there is no token: anyone who can reach "
            + host + ":" + port + " can change weapons and mobs. Set a \"token\" to require one.");
    }

    /** Reads the file, creating it with the defaults if it does not exist. */
    public static WebSettings load(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, DEFAULT.toJson(), StandardCharsets.UTF_8);
            return DEFAULT;
        }
        JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        if (!root.isJsonObject()) {
            throw new IOException(file + " must be a JSON object");
        }
        JsonObject json = root.getAsJsonObject();
        try {
            return new WebSettings(
                !json.has("enabled") || json.get("enabled").getAsBoolean(),
                json.has("host") ? json.get("host").getAsString() : DEFAULT.host(),
                json.has("port") ? json.get("port").getAsInt() : DEFAULT.port(),
                json.has("token") ? json.get("token").getAsString() : "",
                json.has("allowNetworkWithoutToken") && json.get("allowNetworkWithoutToken").getAsBoolean());
        } catch (RuntimeException e) {
            throw new IOException(file + ": " + e.getMessage(), e);
        }
    }

    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("host", host);
        json.addProperty("port", port);
        json.addProperty("token", token);
        json.addProperty("allowNetworkWithoutToken", allowNetworkWithoutToken);
        return new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n";
    }
}
