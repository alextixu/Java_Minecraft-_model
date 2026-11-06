package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.stats;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import io.github.alextixu.mobarmory.web.AdminHttpServer;
import io.github.alextixu.mobarmory.web.WebSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * System test for the web admin inside the real game (R6, 08 section 2: the technical risk that Forge's module
 * layer might hide {@code jdk.httpserver}). A browser-like request goes through the HTTP server, hops to the server
 * thread, and changes the sword in a player's hand. Runs in its own batch ({@code mobarmory:web_admin}).
 */
final class WebAdminGameTests {
    private WebAdminGameTests() {
    }

    static void registerAll() {
        test("web_admin_saves_through_the_game", WebAdminGameTests::savesThroughTheGame);
    }

    /** H5 end to end: PUT /api/v1/weapons/heavy_sword with attack +16, and the held sword hits for 17. */
    static void savesThroughTheGame(GameTestHelper helper) {
        AdminHttpServer server;
        try {
            server = AdminHttpServer.start(MobArmoryMod.WEAPON_ADMIN, MobArmoryMod.MOBS::catalog, new WebSettings(true, "127.0.0.1", 0, ""));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ServerPlayer player = LiveEditGameTests.playerHoldingSword(helper);
        checkNear(helper, 7.0, player.getAttributeValue(stats().holder(ModStats.ATTACK_DAMAGE.id())), "given: attack with the shipped sword");
        String body = """
            {"format": 1, "name": "Heavy Sword", "slot": "mainhand", "look": {"item": "minecraft:netherite_sword"},
             "modifiers": [{"stat": "attack_damage", "add": 16}, {"stat": "movement_speed", "percent": -30},
                           {"stat": "attack_speed", "percent": -40}]}""";
        // Sent from another thread while this one keeps ticking: the request waits for the server thread to apply it.
        CompletableFuture<HttpResponse<String>> response = HttpClient.newHttpClient().sendAsync(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/api/v1/weapons/heavy_sword"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());

        helper.startSequence()
            .thenWaitUntil(() -> check(helper, response.isDone(), "waiting for the web server to answer"))
            .thenExecute(() -> {
                try {
                    HttpResponse<String> answer = response.join();
                    check(helper, answer.statusCode() == 200, "web save answered " + answer.statusCode() + ": " + answer.body());
                    checkNear(helper, 17.0, player.getAttributeValue(stats().holder(ModStats.ATTACK_DAMAGE.id())),
                        "then: attack right after the web save");
                } finally {
                    server.close();
                    LiveEditGameTests.restore();
                }
            })
            .thenSucceed();
    }
}
