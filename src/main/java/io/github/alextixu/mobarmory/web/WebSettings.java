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

/**
 * {@code config/mobarmory/web.json} (decisions O5, Q6):
 * <pre>{@code { "enabled": true, "host": "127.0.0.1", "port": 8765, "token": "" }}</pre>
 * Only this computer can reach the default address. Opening it to other computers requires a token, or the web
 * admin does not start (decision Q6 A).
 */
public record WebSettings(boolean enabled, String host, int port, String token) {
    public static final WebSettings DEFAULT = new WebSettings(true, "127.0.0.1", 8765, "");

    public WebSettings {
        host = host == null || host.isBlank() ? DEFAULT.host() : host.strip();
        token = token == null ? "" : token.strip();
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("web.json: port must be 0 to 65535, got " + port);
        }
    }

    /** True when only this computer can connect. */
    public boolean isLocalOnly() {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** @throws IllegalStateException if the address is reachable from other computers and there is no token */
    public void checkSafe() {
        if (!isLocalOnly() && token.isEmpty()) {
            throw new IllegalStateException("web.json: host '" + host + "' can be reached from other computers; set a "
                + "\"token\" (a long random password) or use 127.0.0.1");
        }
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
                json.has("token") ? json.get("token").getAsString() : "");
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
        return new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n";
    }
}
