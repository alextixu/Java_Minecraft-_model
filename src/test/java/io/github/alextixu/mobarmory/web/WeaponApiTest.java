package io.github.alextixu.mobarmory.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.alextixu.mobarmory.content.ModMobs;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobLook;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: every route of the web API, with the real editor and no game (09, H4 to H9, B10, B11, B17). */
class WeaponApiTest {
    private final TestWeaponAdmin admin = new TestWeaponAdmin();
    private final WeaponApi api = new WeaponApi(admin, ModMobs::catalog);

    private static final String SWORD_16 = """
        {"format": 1, "name": "Heavy Sword", "slot": "mainhand", "look": {"item": "minecraft:netherite_sword"},
         "modifiers": [{"stat": "attack_damage", "add": %s}, {"stat": "movement_speed", "percent": -30},
                       {"stat": "attack_speed", "percent": -40}]}""";


    private WeaponApi.Response call(String method, String path, String body) {
        return api.handle(method, path, body.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject json(WeaponApi.Response response) {
        return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonElement jsonAny(WeaponApi.Response response) {
        return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("H4 列出武器：4 件預設、每件有 id、版本、狀態「生效中」、和檔案同格式的內容")
    void listsWeapons() {
        WeaponApi.Response response = call("GET", "/api/v1/weapons", "");
        var weapons = json(response).getAsJsonArray("weapons");

        assertEquals(200, response.status());
        assertEquals(4, weapons.size());
        JsonObject first = weapons.get(0).getAsJsonObject();
        assertEquals("active", first.get("status").getAsString());
        assertTrue(first.getAsJsonObject("weapon").has("modifiers"));
    }

    @Test
    @DisplayName("H5 Given 重劍版本 1 When PUT 攻擊力 16 Then 200、版本 2，再讀一次就是 16")
    void saveChangesTheWeapon() {
        WeaponApi.Response saved = call("PUT", "/api/v1/weapons/heavy_sword", SWORD_16.formatted("16"));

        assertEquals(200, saved.status(), new String(saved.body(), StandardCharsets.UTF_8));
        JsonObject again = json(call("GET", "/api/v1/weapons/heavy_sword", ""));
        assertEquals(16, again.getAsJsonObject("weapon").getAsJsonArray("modifiers").get(0).getAsJsonObject().get("add").getAsInt());
    }

    @Test
    @DisplayName("B11、O10 兩次存檔 → 都是 200，後存的蓋掉先存的")
    void lastSaveWins() {
        assertEquals(200, call("PUT", "/api/v1/weapons/heavy_sword", SWORD_16.formatted("16")).status());
        assertEquals(200, call("PUT", "/api/v1/weapons/heavy_sword", SWORD_16.formatted("99")).status());

        JsonObject again = json(call("GET", "/api/v1/weapons/heavy_sword", ""));
        assertEquals(99, again.getAsJsonObject("weapon").getAsJsonArray("modifiers").get(0).getAsJsonObject().get("add").getAsInt());
    }

    @Test
    @DisplayName("B10 數字寫成 \"abc\" → 400，fields 指出 modifiers[0].add，什麼都沒變")
    void badNumberIs400WithField() {
        WeaponApi.Response response = call("PUT", "/api/v1/weapons/heavy_sword", SWORD_16.formatted("\"abc\""));

        assertEquals(400, response.status());
        assertTrue(json(response).getAsJsonObject("fields").has("modifiers[0].add"), json(response).toString());
        assertEquals(6.0, admin.library.current().get(EquipmentId.of("heavy_sword")).modifiers().get(0).amount());
    }

    @Test
    @DisplayName("B9 想改欄位 → 400 並指出 slot")
    void slotChange() {
        WeaponApi.Response slot = call("PUT", "/api/v1/weapons/heavy_sword", SWORD_16.formatted("6").replace("mainhand", "chest"));

        assertEquals(400, slot.status());
        assertTrue(json(slot).getAsJsonObject("fields").has("slot"));
    }

    @Test
    @DisplayName("不存在的武器 → 404；id 格式不對 → 400")
    void unknownAndBadIds() {
        assertEquals(404, call("GET", "/api/v1/weapons/spear", "").status());
        assertEquals(404, call("PUT", "/api/v1/weapons/spear", SWORD_16.formatted("1")).status());
        assertEquals(400, call("GET", "/api/v1/weapons/Heavy%20Sword", "").status());
    }

    @Test
    @DisplayName("H9 上傳貼圖再建立新武器 → 202 待重開，清單裡看得到；同一個 id 再建一次 → 400")
    void uploadAndCreate() {
        ByteBuffer png = ByteBuffer.allocate(33);
        png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}).putInt(13).put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(32).putInt(32);
        assertEquals(201, api.handle("PUT", "/api/v1/textures/spear_custom", png.array()).status());
        assertEquals("image/png", api.handle("GET", "/api/v1/textures/spear_custom", new byte[0]).contentType());

        String spear = "{\"id\": \"spear\", \"name\": \"長矛\", \"slot\": \"mainhand\", \"look\": {\"texture\": \"spear_custom\"},"
            + " \"modifiers\": [{\"stat\": \"attack_damage\", \"add\": 5}]}";
        WeaponApi.Response created = call("POST", "/api/v1/weapons", spear);

        assertEquals(202, created.status(), new String(created.body(), StandardCharsets.UTF_8));
        assertEquals("pending_restart", json(created).get("status").getAsString());
        assertEquals(5, json(call("GET", "/api/v1/weapons", "")).getAsJsonArray("weapons").size());
        assertEquals(400, call("POST", "/api/v1/weapons", spear).status());
        assertEquals(400, api.handle("PUT", "/api/v1/textures/bad", "not a png".getBytes(StandardCharsets.UTF_8)).status());
        assertEquals(400, api.handle("PUT", "/api/v1/textures/dagger_iron", png.array()).status(), "built-in names are taken");
        var looks = json(call("GET", "/api/v1/looks", ""));
        assertTrue(looks.getAsJsonArray("textures").toString().contains("spear_custom"), looks.toString());
        assertTrue(looks.getAsJsonArray("builtInTextures").toString().contains("dagger_iron"), looks.toString());
    }

    @Test
    @DisplayName("重新載入：正常時回報告；有壞檔時 400，problems 列出檔名")
    void reload() {
        assertEquals(200, call("POST", "/api/v1/reload", "").status());

        admin.store.putFile("dagger.json", "{ broken");
        WeaponApi.Response broken = call("POST", "/api/v1/reload", "");

        assertEquals(400, broken.status());
        assertTrue(json(broken).getAsJsonArray("problems").get(0).getAsString().contains("dagger.json"));
    }

    @Test
    @DisplayName("H10 列出怪物：每一隻都有 id、外觀、它設定的數值、裝備、行為，和 content/ModMobs 的定義一樣")
    void listsMobsAsDefined() {
        WeaponApi.Response response = call("GET", "/api/v1/mobs", "");
        var mobs = json(response).getAsJsonArray("mobs");

        assertEquals(200, response.status());
        assertEquals(ModMobs.ALL.size(), mobs.size());
        for (int i = 0; i < ModMobs.ALL.size(); i++) {
            MobDefinition definition = ModMobs.ALL.get(i);
            JsonObject mob = mobs.get(i).getAsJsonObject();
            assertEquals(definition.id().value(), mob.get("id").getAsString());
            assertEquals(definition.look().scale(), mob.getAsJsonObject("look").get("scale").getAsDouble());
            JsonObject stats = mob.getAsJsonObject("stats");
            assertEquals(definition.stats().size(), stats.size(), definition.id() + ": only the stats it sets");
            definition.stats().forEach((stat, value) -> assertEquals(value, stats.get(stat.value()).getAsDouble(), stat.value()));
            JsonObject equipment = mob.getAsJsonObject("equipment");
            assertEquals(definition.equipment().size(), equipment.size());
            definition.equipment().forEach((slot, id) ->
                assertEquals(id.value(), equipment.get(slot.name().toLowerCase(Locale.ROOT)).getAsString()));
            assertEquals(definition.behaviors().size(), mob.getAsJsonArray("behaviors").size());
        }

        JsonObject brute = mobs.get(0).getAsJsonObject();
        assertEquals("zombie", brute.getAsJsonObject("look").get("body").getAsString());
        assertEquals(40, brute.getAsJsonObject("stats").get("max_health").getAsInt());
        assertEquals("heavy_sword", brute.getAsJsonObject("equipment").get("mainhand").getAsString());
        assertEquals("wander", brute.getAsJsonArray("behaviors").get(0).getAsString());
        assertEquals("attack_players", brute.getAsJsonArray("behaviors").get(1).getAsString());
    }

    @Test
    @DisplayName("H10 邊界：沒有裝備、沒有行為的怪物 → equipment 是 {}、behaviors 是 []（站著不動，R4 B6）")
    void mobWithNothingListsEmpty() {
        MobCatalog bare = MobCatalog.builder(ModStats.CATALOG)
            .add(MobDefinition.builder("statue").look(MobLook.of(MobLook.Body.STRAY)).build())
            .build();
        WeaponApi.Response response = new WeaponApi(admin, () -> bare).handle("GET", "/api/v1/mobs", new byte[0]);
        JsonObject statue = json(response).getAsJsonArray("mobs").get(0).getAsJsonObject();

        assertEquals("stray", statue.getAsJsonObject("look").get("body").getAsString());
        assertEquals(0, statue.getAsJsonObject("stats").size());
        assertEquals(0, statue.getAsJsonObject("equipment").size());
        assertEquals(0, statue.getAsJsonArray("behaviors").size());
    }

    @Test
    @DisplayName("B17 怪物只能看：POST、PUT、DELETE → 405，清單沒變")
    void mobsAreReadOnly() {
        String before = new String(call("GET", "/api/v1/mobs", "").body(), StandardCharsets.UTF_8);

        assertEquals(405, call("POST", "/api/v1/mobs", "{}").status());
        assertEquals(405, call("PUT", "/api/v1/mobs", "{}").status());
        assertEquals(405, call("DELETE", "/api/v1/mobs", "").status());
        assertEquals(before, new String(call("GET", "/api/v1/mobs", "").body(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("數值登記表和外觀清單（表單用）；暴擊率標成機率")
    void statsAndLooks() {
        var stats = jsonAny(call("GET", "/api/v1/stats", "")).getAsJsonArray();
        assertEquals(ModStats.CATALOG.size(), stats.size(), "every registered stat, including R4's max_health");
        boolean critIsChance = false;
        for (JsonElement stat : stats) {
            if (stat.getAsJsonObject().get("id").getAsString().equals("crit_chance")) {
                critIsChance = stat.getAsJsonObject().get("chance").getAsBoolean();
            }
        }
        assertTrue(critIsChance);
        assertTrue(json(call("GET", "/api/v1/looks", "")).getAsJsonArray("items").size() > 0);
    }

    @Test
    @DisplayName("不支援的方法 → 405；不存在的路徑 → 404")
    void methodsAndPaths() {
        assertEquals(405, call("DELETE", "/api/v1/weapons/heavy_sword", "").status());
        assertEquals(405, call("POST", "/api/v1/stats", "").status());
        assertEquals(404, call("GET", "/api/v2/anything", "").status());
    }
}
