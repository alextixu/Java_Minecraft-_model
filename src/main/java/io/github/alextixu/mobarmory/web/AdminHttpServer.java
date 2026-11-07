package io.github.alextixu.mobarmory.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The web admin's HTTP server: JDK's built-in {@link HttpServer}, no extra libraries (decision O4). Serves the page
 * from the mod jar ({@code mobarmory/web/}) and the API: {@link MobApi} for mobs (R4b), {@link WeaponApi} for the rest.
 * <p>
 * Who may change things (decision Q6 A, B13):
 * <ul>
 *   <li>by default it listens on 127.0.0.1 only, so other computers cannot connect;</li>
 *   <li>the {@code Host} header must name this server, which stops other websites reaching it through DNS tricks
 *       ({@link AllowedHosts}; with host 0.0.0.0 that includes every IP address of this computer);</li>
 *   <li>changes must come from this page ({@code Origin}) and as JSON or PNG, which a browser will not send from
 *       another website without asking first; this server never says yes (no CORS headers);</li>
 *   <li>with a token in web.json, every API call must carry it ({@code Authorization: Bearer ...}); an address open
 *       to other computers needs a token unless web.json says {@code allowNetworkWithoutToken} (web fixes B3, see
 *       {@link WebSettings#checkSafe()}).</li>
 * </ul>
 */
public final class AdminHttpServer implements AutoCloseable {
    static final int MAX_JSON_BYTES = 64 * 1024;
    static final int MAX_PNG_BYTES = 256 * 1024;
    private static final Map<String, String> STATIC = Map.of(
        "/", "index.html",
        "/index.html", "index.html",
        "/app.js", "app.js",
        "/style.css", "style.css");
    private static final Map<String, String> TYPES = Map.of(
        "html", "text/html; charset=utf-8",
        "js", "text/javascript; charset=utf-8",
        "css", "text/css; charset=utf-8");
    private static final String CSP = "default-src 'self'; img-src 'self' data: blob:; style-src 'self'; script-src 'self'; "
        + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    private final HttpServer server;
    private final ExecutorService executor;
    private final WeaponApi api;
    private final MobApi mobApi;
    private final WebSettings settings;
    private final AllowedHosts allowedHosts;
    /** The host in {@link #uri()}: an address people can click (web fixes B2-3). */
    private final String shownHost;

    private AdminHttpServer(HttpServer server, ExecutorService executor, WeaponApi api, MobApi mobApi, WebSettings settings,
                            List<InetAddress> local) {
        this.server = server;
        this.executor = executor;
        this.api = api;
        this.mobApi = mobApi;
        this.settings = settings;
        this.allowedHosts = AllowedHosts.of(settings, server.getAddress().getPort(), local);
        this.shownHost = shownHost(settings, local);
    }

    /**
     * Starts listening. Port 0 picks a free port (tests). With host {@code 0.0.0.0} or {@code ::}, this computer's
     * addresses are read once now; a network change afterwards needs the world (server) to start again.
     *
     * @throws IllegalStateException if the address is open to other computers without a token
     * @throws IOException           if the port is taken (B14)
     */
    public static AdminHttpServer start(WeaponAdmin admin, MobAdmin mobs, WebSettings settings) throws IOException {
        return start(admin, mobs, settings, LocalAddresses.system(), new InetSocketAddress(settings.host(), settings.port()));
    }

    /**
     * The same, with this computer's addresses and the address to listen on given. Tests use it to act as a server
     * listening on every address while really listening on 127.0.0.1, so they neither depend on the network nor open
     * a port to it.
     */
    static AdminHttpServer start(WeaponAdmin admin, MobAdmin mobs, WebSettings settings, LocalAddresses local,
                                 InetSocketAddress listenOn) throws IOException {
        settings.checkSafe();
        HttpServer server = HttpServer.create(listenOn, 0);
        AtomicInteger threads = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "mobarmory-web-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        AdminHttpServer instance = new AdminHttpServer(server, executor, new WeaponApi(admin), new MobApi(mobs), settings, local.all());
        server.createContext("/", instance::handle);
        server.setExecutor(executor);
        server.start();
        return instance;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** The address to open in a browser. Never carries the token, so it is safe to log (web fixes B1-3). */
    public URI uri() {
        return URI.create("http://" + shownHost + ":" + port() + "/");
    }

    /**
     * The link game masters click in chat: {@link #uri()}, plus {@code ?token=...} (URL encoded) when web.json sets a
     * token, so the page works at once (web fixes B1-1, B1-2). Never log it.
     */
    public URI linkWithToken() {
        if (settings.token().isEmpty()) {
            return uri();
        }
        return URI.create(uri() + "?token=" + URLEncoder.encode(settings.token(), StandardCharsets.UTF_8));
    }

    /**
     * The host people can click (web fixes B2-3): 127.0.0.1 for a local-only server; for one listening on every
     * address, a private (site-local) IPv4 address of this computer, else its first address that is not loopback,
     * else 127.0.0.1; otherwise the host in web.json, as before.
     */
    static String shownHost(WebSettings settings, List<InetAddress> local) {
        if (settings.isLocalOnly()) {
            return "127.0.0.1";
        }
        if (!settings.listensOnAll()) {
            return settings.host();
        }
        return local.stream().filter(address -> address instanceof Inet4Address && address.isSiteLocalAddress()).findFirst()
            .or(() -> local.stream().filter(address -> !address.isLoopbackAddress() && !address.isAnyLocalAddress()).findFirst())
            .map(address -> address instanceof Inet6Address ? "[" + AllowedHosts.text(address) + "]" : AllowedHosts.text(address))
            .orElse("127.0.0.1");
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            Headers request = exchange.getRequestHeaders();
            Headers response = exchange.getResponseHeaders();
            response.set("X-Content-Type-Options", "nosniff");
            response.set("Referrer-Policy", "no-referrer");
            response.set("Cache-Control", "no-store");

            if (!allowedHosts.allows(request.getFirst("Host"))) {
                send(exchange, WeaponApi.Response.error(403, "forbidden_host", "This server only answers requests addressed to it"));
                return;
            }
            if (!path.startsWith("/api/")) {
                serveStatic(exchange, method, path);
                return;
            }
            if (!settings.token().isEmpty() && !tokenMatches(request.getFirst("Authorization"))) {
                send(exchange, WeaponApi.Response.error(401, "token_required", "Missing or wrong token"));
                return;
            }
            boolean changes = !method.equals("GET") && !method.equals("HEAD");
            if (changes) {
                String origin = request.getFirst("Origin");
                if (origin != null && !allowedHosts.allows(stripScheme(origin))) {
                    send(exchange, WeaponApi.Response.error(403, "forbidden_origin", "Changes are only accepted from this page"));
                    return;
                }
                boolean png = path.startsWith("/api/v1/textures/");
                String expected = png ? "image/png" : "application/json";
                String type = request.getFirst("Content-Type");
                if (type == null || !type.toLowerCase().startsWith(expected)) {
                    send(exchange, WeaponApi.Response.error(415, "wrong_content_type", "Send " + expected));
                    return;
                }
            }
            byte[] body = readLimited(exchange.getRequestBody(), path.startsWith("/api/v1/textures/") ? MAX_PNG_BYTES : MAX_JSON_BYTES);
            if (body == null) {
                send(exchange, WeaponApi.Response.error(413, "too_large", "The request is too large"));
                return;
            }
            send(exchange, MobApi.handles(path) ? mobApi.handle(method, path, body) : api.handle(method, path, body));
        } catch (RuntimeException e) {
            // Never let one bad request take the server down; the page shows the message.
            try {
                send(exchange, WeaponApi.Response.error(500, "internal", e.toString()));
            } catch (IOException ignored) {
                // the client is gone
            }
        }
    }

    private void serveStatic(HttpExchange exchange, String method, String path) throws IOException {
        String file = STATIC.get(path);
        if (file == null || !(method.equals("GET") || method.equals("HEAD"))) {
            send(exchange, WeaponApi.Response.error(404, "not_found", "No such page: " + path));
            return;
        }
        try (InputStream in = AdminHttpServer.class.getResourceAsStream("/mobarmory/web/" + file)) {
            if (in == null) {
                send(exchange, WeaponApi.Response.error(500, "missing_page", "The mod is missing mobarmory/web/" + file));
                return;
            }
            String extension = file.substring(file.lastIndexOf('.') + 1);
            exchange.getResponseHeaders().set("Content-Security-Policy", CSP);
            send(exchange, new WeaponApi.Response(200, TYPES.get(extension), in.readAllBytes()));
        }
    }

    private boolean tokenMatches(String authorization) {
        String expected = "Bearer " + settings.token();
        return authorization != null && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), authorization.strip().getBytes(StandardCharsets.UTF_8));
    }

    private static String stripScheme(String origin) {
        String lower = origin.toLowerCase();
        return lower.startsWith("http://") ? lower.substring("http://".length()) : lower;
    }

    /** The body, or null if it is larger than {@code limit}. */
    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        byte[] bytes = in.readNBytes(limit + 1);
        return bytes.length > limit ? null : bytes;
    }

    private static void send(HttpExchange exchange, WeaponApi.Response response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        boolean head = exchange.getRequestMethod().equals("HEAD");
        exchange.sendResponseHeaders(response.status(), head ? -1 : response.body().length == 0 ? -1 : response.body().length);
        if (!head && response.body().length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response.body());
            }
        }
    }
}
