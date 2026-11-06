package io.github.alextixu.mobarmory.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.data.DefaultMobs;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Component tests: every mob route of the web API, with the real editor and no game (22, H3, H4, H13, B2 to B7, B11,
 * B14 to B16, B18). The list's fields from R6's read-only version are checked unchanged.
 */
class MobApiTest {
    private final TestMobAdmin admin = new TestMobAdmin();
    private final MobApi api = new MobApi(admin);

    /** The brute's file with another attack. */
    private static final String BRUTE = """
        {"format": 1, "name": "Brute", "look": {"body": "zombie", "scale": 1.2},
         "stats": {"max_health": 40, "attack_damage": %s, "movement_speed": 0.25},
         "equipment": {"mainhand": "heavy_sword"},
         "behaviors": [{"type": "wander", "speed": 1}, {"type": "attack_players", "speed": 1}]}""";

    private static final String GOBLIN = """
        {"id": "goblin", "format": 1, "name": "Goblin", "look": {"body": "husk", "scale": 0.8},
         "stats": {"max_health": 12, "attack_damage": 3, "movement_speed": 0.3},
         "equipment": {"mainhand": "dagger"},
         "behaviors": [{"type": "wander"}, {"type": "neutral", "speed": 1.3}, {"type": "leap"}]}""";

    private WeaponApi.Response call(String method, String path, String body) {
        return api.handle(method, path, body.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject json(WeaponApi.Response response) {
        return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private MobDefinition brute() {
        return admin.library.current().get(DefaultMobs.BRUTE);
    }

    @Test
    @DisplayName("H13 列出怪物：R6 唯讀清單的欄位不變（id、外觀、它設定的數值、裝備、行為名稱），再加名稱、狀態「生效中」、檔案格式的內容")
    void listsMobsAsDefined() {
        WeaponApi.Response response = call("GET", "/api/v1/mobs", "");
        var mobs = json(response).getAsJsonArray("mobs");

        assertEquals(200, response.status());
        assertEquals(2, mobs.size());
        for (int i = 0; i < mobs.size(); i++) {
            MobDefinition definition = admin.library.current().all().get(i);
            JsonObject mob = mobs.get(i).getAsJsonObject();
            assertEquals(definition.id().value(), mob.get("id").getAsString());
            assertEquals(definition.look().scale(), mob.getAsJsonObject("look").get("scale").getAsDouble());
            JsonObject stats = mob.getAsJsonObject("stats");
            assertEquals(definition.stats().size(), stats.size(), definition.id() + ": only the stats it sets");
            definition.stats().forEach((stat, value) -> assertEquals(value, stats.get(stat.value()).getAsDouble(), stat.value()));
            JsonObject equipment = mob.getAsJsonObject("equipment");
            definition.equipment().forEach((slot, id) ->
                assertEquals(id.value(), equipment.get(slot.name().toLowerCase(Locale.ROOT)).getAsString()));
            assertEquals(definition.behaviors().size(), mob.getAsJsonArray("behaviors").size());
            assertEquals(definition.name(), mob.get("name").getAsString());
            assertEquals("active", mob.get("status").getAsString());
            assertEquals(definition.name(), mob.getAsJsonObject("mob").get("name").getAsString());
        }

        JsonObject brute = mobs.get(0).getAsJsonObject();
        assertEquals("zombie", brute.getAsJsonObject("look").get("body").getAsString());
        assertEquals(40, brute.getAsJsonObject("stats").get("max_health").getAsInt());
        assertEquals("heavy_sword", brute.getAsJsonObject("equipment").get("mainhand").getAsString());
        assertEquals("wander", brute.getAsJsonArray("behaviors").get(0).getAsString());
        assertEquals("attack_players", brute.getAsJsonArray("behaviors").get(1).getAsString());
        assertEquals("attack_players", brute.getAsJsonObject("mob").getAsJsonArray("behaviors").get(1)
            .getAsJsonObject().get("type").getAsString());
    }

    @Test
    @DisplayName("H4 Given 狂戰士攻擊 4 When PUT 攻擊 8 Then 200，檔案和遊戲裡的定義都是 8")
    void saveChangesTheMob() {
        WeaponApi.Response saved = call("PUT", "/api/v1/mobs/brute", BRUTE.formatted("8"));

        assertEquals(200, saved.status(), new String(saved.body(), StandardCharsets.UTF_8));
        assertEquals(8.0, brute().stats().get(io.github.alextixu.mobarmory.core.stat.StatId.of("attack_damage")));
        assertTrue(admin.store.text("brute.json").contains("\"attack_damage\": 8"), admin.store.text("brute.json"));
        assertEquals(8, json(call("GET", "/api/v1/mobs/brute", "")).getAsJsonObject("stats").get("attack_damage").getAsInt());
    }

    @Test
    @DisplayName("H3 新增怪物：POST goblin → 202、寫出 goblin.json、清單上標「待重開」")
    void newMobWaitsForARestart() {
        WeaponApi.Response created = call("POST", "/api/v1/mobs", GOBLIN);

        assertEquals(202, created.status(), new String(created.body(), StandardCharsets.UTF_8));
        assertEquals("pending_restart", json(created).get("status").getAsString());
        assertTrue(admin.store.text("goblin.json").contains("\"neutral\""));
        var mobs = json(call("GET", "/api/v1/mobs", "")).getAsJsonArray("mobs");
        assertEquals(3, mobs.size());
        assertEquals("pending_restart", mobs.get(2).getAsJsonObject().get("status").getAsString());
        assertFalse(admin.library.isRegistered(MobId.of("goblin")), "a new kind of mob needs a restart");
    }

    @Test
    @DisplayName("B16 新增已經有的 id（brute）→ 400，欄位 id")
    void newMobWithTakenIdFails() {
        WeaponApi.Response response = call("POST", "/api/v1/mobs", GOBLIN.replace("\"goblin\"", "\"brute\""));

        assertEquals(400, response.status());
        assertTrue(json(response).getAsJsonObject("fields").has("id"));
    }

    @Test
    @DisplayName("B2 不存在的數值、超出範圍 → 400，錯誤標在那一欄；什麼都沒改")
    void wrongStatsAreRefused() {
        String unknown = BRUTE.formatted("4").replace("\"max_health\": 40", "\"block_chance\": 0.5");
        WeaponApi.Response first = call("PUT", "/api/v1/mobs/brute", unknown);
        WeaponApi.Response second = call("PUT", "/api/v1/mobs/brute", BRUTE.formatted("4").replace("\"max_health\": 40", "\"max_health\": 5000"));

        assertEquals(400, first.status());
        assertTrue(json(first).getAsJsonObject("fields").has("stats.block_chance"), json(first).toString());
        assertEquals(400, second.status());
        assertTrue(json(second).getAsJsonObject("fields").has("stats.max_health"), json(second).toString());
        assertEquals(DefaultMobs.definition(DefaultMobs.BRUTE, ModStats.CATALOG), brute());
    }

    @Test
    @DisplayName("B3 B4 B7 身體 creeper、行為 fly、速度 10 → 400，各自標在欄位上")
    void wrongLookBehaviorOrSpeedIsRefused() {
        WeaponApi.Response body = call("PUT", "/api/v1/mobs/brute", BRUTE.formatted("4").replace("\"zombie\"", "\"creeper\""));
        WeaponApi.Response type = call("PUT", "/api/v1/mobs/brute", BRUTE.formatted("4").replace("\"wander\"", "\"fly\""));
        WeaponApi.Response speed = call("PUT", "/api/v1/mobs/brute",
            BRUTE.formatted("4").replace("{\"type\": \"attack_players\", \"speed\": 1}", "{\"type\": \"attack_players\", \"speed\": 10}"));

        assertTrue(json(body).getAsJsonObject("fields").has("look.body"), json(body).toString());
        assertTrue(json(type).getAsJsonObject("fields").has("behaviors[0].type"), json(type).toString());
        assertTrue(json(speed).getAsJsonObject("fields").has("behaviors[1].speed"), json(speed).toString());
    }

    @Test
    @DisplayName("B5 B6 攻擊玩家和中立同時寫、有衝刺沒有攻擊 → 400，欄位 behaviors")
    void behaviorCombinationsAreChecked() {
        WeaponApi.Response both = call("PUT", "/api/v1/mobs/brute",
            BRUTE.formatted("4").replace("{\"type\": \"wander\", \"speed\": 1}", "{\"type\": \"neutral\"}"));
        WeaponApi.Response leapAlone = call("PUT", "/api/v1/mobs/brute",
            BRUTE.formatted("4").replace("{\"type\": \"attack_players\", \"speed\": 1}", "{\"type\": \"leap\"}"));

        assertEquals(400, both.status());
        assertTrue(json(both).getAsJsonObject("fields").get("behaviors").getAsString().contains("keep only one"), json(both).toString());
        assertEquals(400, leapAlone.status());
        assertTrue(json(leapAlone).getAsJsonObject("fields").get("behaviors").getAsString().contains("needs attack_players or neutral"));
    }

    @Test
    @DisplayName("B11 網頁換身體（殭屍 → 骷髏）→ 400「重開後才能換」，欄位 look.body")
    void bodyChangeWaitsForARestart() {
        WeaponApi.Response response = call("PUT", "/api/v1/mobs/brute", BRUTE.formatted("4").replace("\"zombie\"", "\"skeleton\""));

        assertEquals(400, response.status());
        assertTrue(json(response).getAsJsonObject("fields").get("look.body").getAsString().contains("after a restart"));
    }

    @Test
    @DisplayName("B18 檔案寫不進去 → 500，遊戲裡的定義不變")
    void failedWriteChangesNothing() {
        admin.store.failWrites = true;
        WeaponApi.Response response = call("PUT", "/api/v1/mobs/brute", BRUTE.formatted("8"));

        assertEquals(500, response.status());
        assertEquals(DefaultMobs.definition(DefaultMobs.BRUTE, ModStats.CATALOG), brute());
    }

    @Test
    @DisplayName("沒有這隻怪物 → 404；不合法的 id → 400；清單不收 PUT、DELETE → 405")
    void wrongAddresses() {
        assertEquals(404, call("GET", "/api/v1/mobs/dragon", "").status());
        assertEquals(404, call("PUT", "/api/v1/mobs/dragon", BRUTE.formatted("4")).status());
        assertEquals(400, call("GET", "/api/v1/mobs/Big%20Orc", "").status());
        assertEquals(405, call("PUT", "/api/v1/mobs", "{}").status());
        assertEquals(405, call("DELETE", "/api/v1/mobs", "").status());
        assertEquals(405, call("DELETE", "/api/v1/mobs/brute", "").status());
    }

    @Test
    @DisplayName("H9 B14 B15 重新載入：手改的檔生效、新檔待重開；有一個壞檔就什麼都不改")
    void reload() {
        admin.store.put("brute.json", BRUTE.formatted("9"));
        admin.store.put("orc.json", GOBLIN.replace("\"id\": \"goblin\", ", "").replace("Goblin", "Orc"));
        JsonObject report = json(call("POST", "/api/v1/mobs/reload", ""));

        assertEquals("brute", report.getAsJsonArray("changed").get(0).getAsString());
        assertEquals("orc", report.getAsJsonArray("pendingRestart").get(0).getAsString());
        assertEquals(9.0, brute().stats().get(io.github.alextixu.mobarmory.core.stat.StatId.of("attack_damage")));

        admin.store.put("broken.json", "{\"format\": 1,");
        admin.store.put("brute.json", BRUTE.formatted("12"));
        WeaponApi.Response broken = call("POST", "/api/v1/mobs/reload", "");
        assertEquals(400, broken.status());
        assertTrue(json(broken).getAsJsonArray("problems").get(0).getAsString().contains("broken.json"));
        assertEquals(9.0, brute().stats().get(io.github.alextixu.mobarmory.core.stat.StatId.of("attack_damage")), "all or nothing");
    }

    @Test
    @DisplayName("表單用的選項：四種身體、四種行為、速度上限 3、大小上下限、欄位")
    void options() {
        JsonObject options = json(call("GET", "/api/v1/mob-options", ""));

        assertEquals("[\"zombie\",\"husk\",\"skeleton\",\"stray\"]", options.getAsJsonArray("bodies").toString());
        assertEquals("[\"wander\",\"attack_players\",\"neutral\",\"leap\"]", options.getAsJsonArray("behaviors").toString());
        assertEquals(3.0, options.get("maxSpeed").getAsDouble());
        assertEquals(6, options.getAsJsonArray("slots").size());
    }

    @Test
    @DisplayName("H13 邊界：沒有裝備、沒有行為的怪物 → equipment 是 {}、behaviors 是 []（站著不動，R4 B6）")
    void mobWithNothingListsEmpty() {
        JsonObject statue = MobJson.toJson(MobDefinition.builder("statue")
            .look(io.github.alextixu.mobarmory.core.mob.MobLook.of(io.github.alextixu.mobarmory.core.mob.MobLook.Body.STRAY)).build());

        assertEquals("stray", statue.getAsJsonObject("look").get("body").getAsString());
        assertEquals(0, statue.getAsJsonObject("stats").size());
        assertEquals(0, statue.getAsJsonObject("equipment").size());
        assertEquals(0, statue.getAsJsonArray("behaviors").size());
    }

    @Test
    @DisplayName("只有怪物的位址歸 MobApi，其他交給 WeaponApi")
    void handlesOnlyMobPaths() {
        assertTrue(MobApi.handles("/api/v1/mobs"));
        assertTrue(MobApi.handles("/api/v1/mobs/brute"));
        assertTrue(MobApi.handles("/api/v1/mob-options"));
        assertFalse(MobApi.handles("/api/v1/weapons"));
        assertFalse(MobApi.handles("/api/v1/reload"));
        assertFalse(MobApi.handles("/api/v1/mobsx"));
    }
}
