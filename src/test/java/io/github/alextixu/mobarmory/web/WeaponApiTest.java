package io.github.alextixu.mobarmory.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: every route of the web API, with the real editor and no game (09, H4 to H9, B10, B11, B17). */
class WeaponApiTest {
    private final TestWeaponAdmin admin = new TestWeaponAdmin();
    private final WeaponApi api = new WeaponApi(admin);

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
