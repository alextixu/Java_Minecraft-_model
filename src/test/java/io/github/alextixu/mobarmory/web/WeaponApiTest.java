package io.github.alextixu.mobarmory.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: every route of the web API, with the real editor and no game (09, H4 to H9, B10, B11, B17). */
class WeaponApiTest {
    private final TestWeaponAdmin admin = new TestWeaponAdmin();
    private final WeaponApi api = new WeaponApi(admin);

    private static final String SWORD_16 = """
        {"version": %d, "format": 1, "name": "Heavy Sword", "slot": "mainhand", "look": {"item": "minecraft:netherite_sword"},
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
        assertEquals(1, first.get("version").getAsLong());
        assertTrue(first.getAsJsonObject("weapon").has("modifiers"));
    }

    @Test
    @DisplayName("H5 Given 重劍版本 1 When PUT 攻擊力 16 Then 200、版本 2，再讀一次就是 16")
    void saveChangesTheWeapon() {
        WeaponApi.Response saved = call("PUT", "/api/v1/weapons/heavy_sword", SWORD_16.formatted(1, "16"));

        assertEquals(200, saved.status(), new String(saved.body(), StandardCharsets.UTF_8));
        assertEquals(2, json(saved).get("version").getAsLong());
        JsonObject again = json(call("GET", "/api/v1/weapons/heavy_sword", ""));
        assertEquals(16, again.getAsJsonObject("weapon").getAsJsonArray("modifiers").get(0).getAsJsonObject().get("add").getAsInt());
    }

    @Test
    @DisplayName("B11 用舊版本號存 → 409，回傳目前的版本")
    void staleSaveIs409() {
        call("PUT", "/api/v1/weapons/heavy_sword", SWORD_16.formatted(1, "16"));
        WeaponApi.Response stale = call("PUT", "/api/v1/weapons/heavy_sword", SWORD_16.formatted(1, "99"));

        assertEquals(409, stale.status());
        assertEquals(2, json(stale).get("currentVersion").getAsLong());
    }

    @Test
    @DisplayName("B10 數字寫成 \"abc\" → 400，fields 指出 modifiers[0].add，什麼都沒變")
    void badNumberIs400WithField() {
        WeaponApi.Response response = call("PUT", "/api/v1/weapons/heavy_sword", SWORD_16.formatted(1, "\"abc\""));

        assertEquals(400, response.status());
        assertTrue(json(response).getAsJsonObject("fields").has("modifiers[0].add"), json(response).toString());
        assertEquals(1, admin.library.version(EquipmentId.of("heavy_sword")));
    }

    @Test
    @DisplayName("B10 沒帶版本號、或想改欄位 → 400 並指出欄位")
    void missingVersionAndSlotChange() {
        WeaponApi.Response noVersion = call("PUT", "/api/v1/weapons/heavy_sword",
            "{\"name\": \"x\", \"slot\": \"mainhand\", \"look\": {\"item\": \"minecraft:iron_sword\"}}");
        WeaponApi.Response slot = call("PUT", "/api/v1/weapons/heavy_sword", SWORD_16.formatted(1, "6").replace("mainhand", "chest"));

        assertTrue(json(noVersion).getAsJsonObject("fields").has("version"));
        assertEquals(400, slot.status());
        assertTrue(json(slot).getAsJsonObject("fields").has("slot"));
    }

    @Test
    @DisplayName("不存在的武器 → 404；id 格式不對 → 400")
    void unknownAndBadIds() {
        assertEquals(404, call("GET", "/api/v1/weapons/spear", "").status());
        assertEquals(404, call("PUT", "/api/v1/weapons/spear", SWORD_16.formatted(1, "1")).status());
        assertEquals(400, call("GET", "/api/v1/weapons/Heavy%20Sword", "").status());
    }

    @Test
    @DisplayName("H9 新增武器（外觀用內建貼圖）→ 202 待重開，清單裡看得到；同一個 id 再建一次 → 400；內建貼圖可以預覽，沒有上傳")
    void createWithBuiltInTexture() {
        String spear = """
            {"id": "spear", "name": "長矛", "slot": "mainhand", "look": {"texture": "spear_iron"},
             "modifiers": [{"stat": "attack_damage", "add": 5}]}""";
        WeaponApi.Response created = call("POST", "/api/v1/weapons", spear);

        assertEquals(202, created.status(), new String(created.body(), StandardCharsets.UTF_8));
        assertEquals("pending_restart", json(created).get("status").getAsString());
        assertEquals(5, json(call("GET", "/api/v1/weapons", "")).getAsJsonArray("weapons").size());
        assertEquals(400, call("POST", "/api/v1/weapons", spear).status());
        String upload = spear.replace("\"spear\"", "\"axe\"").replace("spear_iron", "my_upload");
        assertEquals(400, call("POST", "/api/v1/weapons", upload).status());
        assertEquals("image/png", call("GET", "/api/v1/textures/spear_iron", "").contentType());
        assertEquals(404, call("GET", "/api/v1/textures/my_upload", "").status());
        assertEquals(405, call("PUT", "/api/v1/textures/spear_iron", "x").status());
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
    @DisplayName("B17 怪物的 API 位置保留，回 501")
    void mobsAreReserved() {
        WeaponApi.Response response = call("GET", "/api/v1/mobs", "");
        assertEquals(501, response.status());
        assertEquals("not_implemented", json(response).get("error").getAsString());
    }

    @Test
    @DisplayName("數值登記表和外觀清單（表單用）；暴擊率標成機率")
    void statsAndLooks() {
        var stats = jsonAny(call("GET", "/api/v1/stats", "")).getAsJsonArray();
        assertEquals(6, stats.size());
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
