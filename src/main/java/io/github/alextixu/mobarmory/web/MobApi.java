package io.github.alextixu.mobarmory.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.mob.Behavior;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import io.github.alextixu.mobarmory.core.mob.MobLook;
import io.github.alextixu.mobarmory.core.mob.MobReloadReport;
import io.github.alextixu.mobarmory.data.MobFileJson;
import io.github.alextixu.mobarmory.data.MobLoadException;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import io.github.alextixu.mobarmory.data.WeaponJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The mob part of the web API, version 1 (R4b, 21-r4b-mob-admin-boundary-note.md section 3). Turns a request into a
 * response using only {@link MobAdmin}; no HTTP server and no Minecraft, so every route is tested directly. Mobs travel
 * in the same JSON format as the files ({@link MobFileJson}); errors have the same shape as {@link WeaponApi}'s.
 * <ul>
 *   <li>{@code GET /api/v1/mobs}: the list (R6's read-only list plus {@code name}, {@code status}, {@code mob});
 *       {@code POST}: a new mob, usable after a restart (202)</li>
 *   <li>{@code GET}, {@code PUT /api/v1/mobs/{id}}: one mob</li>
 *   <li>{@code POST /api/v1/mobs/reload}: read the mob files again (the page's reload button also calls the weapons'
 *       {@code /api/v1/reload})</li>
 *   <li>{@code GET /api/v1/mob-options}: what the form offers</li>
 * </ul>
 */
public final class MobApi {
    private static final Pattern MOB = Pattern.compile("/api/v1/mobs/([^/]+)");

    private final MobAdmin admin;

    public MobApi(MobAdmin admin) {
        this.admin = admin;
    }

    /** True for the paths this API answers; the HTTP server sends every other path to {@link WeaponApi}. */
    public static boolean handles(String path) {
        return path.equals("/api/v1/mobs") || path.startsWith("/api/v1/mobs/") || path.equals("/api/v1/mob-options");
    }

    public WeaponApi.Response handle(String method, String path, byte[] body) {
        try {
            return route(method, path, body);
        } catch (WeaponFormatException e) {
            return WeaponApi.Response.error(400, "invalid", e.getMessage(), Map.of(e.field().isEmpty() ? "body" : e.field(), e.reason()));
        } catch (NoSuchElementException e) {
            return WeaponApi.Response.error(404, "not_found", e.getMessage());
        } catch (MobLoadException e) {
            JsonObject json = new JsonObject();
            json.addProperty("error", "invalid_files");
            json.addProperty("message", e.getMessage());
            json.add("fields", new JsonObject());
            JsonArray problems = new JsonArray();
            e.problems().forEach(problems::add);
            json.add("problems", problems);
            return WeaponApi.Response.json(400, json);
        } catch (IOException e) {
            return WeaponApi.Response.error(500, "io", "Could not save: " + e.getMessage());
        }
    }

    private WeaponApi.Response route(String method, String path, byte[] body)
        throws IOException, WeaponFormatException, MobLoadException {
        if (path.equals("/api/v1/mob-options")) {
            return method.equals("GET") ? WeaponApi.Response.json(200, options()) : notAllowed();
        }
        if (path.equals("/api/v1/mobs")) {
            return switch (method) {
                case "GET" -> WeaponApi.Response.json(200, MobJson.list(admin.view()));
                case "POST" -> create(body);
                default -> notAllowed();
            };
        }
        if (path.equals("/api/v1/mobs/reload") && method.equals("POST")) {
            return WeaponApi.Response.json(200, report(admin.reload()));
        }
        Matcher mob = MOB.matcher(path);
        if (mob.matches()) {
            MobId id = id(mob.group(1));
            return switch (method) {
                case "GET" -> WeaponApi.Response.json(200, entry(id));
                case "PUT" -> update(id, body);
                default -> notAllowed();
            };
        }
        return WeaponApi.Response.error(404, "not_found", "No such page: " + path);
    }

    private static WeaponApi.Response notAllowed() {
        return WeaponApi.Response.error(405, "method_not_allowed", "This address does not accept that request");
    }

    private static MobId id(String text) throws WeaponFormatException {
        try {
            return MobId.of(text);
        } catch (IllegalArgumentException e) {
            throw new WeaponFormatException("id", e.getMessage());
        }
    }

    private JsonObject entry(MobId id) {
        MobLibrary.View view = admin.view();
        Optional<MobDefinition> definition = view.catalog().find(id).or(() -> Optional.ofNullable(view.pending().get(id)));
        return MobJson.entry(definition.orElseThrow(() -> new NoSuchElementException("Unknown mob '" + id + "'")), view);
    }

    private JsonObject body(byte[] body) throws WeaponFormatException {
        return WeaponJson.parse(new String(body, StandardCharsets.UTF_8));
    }

    private WeaponApi.Response update(MobId id, byte[] bytes) throws IOException, WeaponFormatException {
        MobDefinition changed = MobFileJson.fromJson(id, body(bytes), admin.stats(), "id");
        admin.update(changed);
        return WeaponApi.Response.json(200, MobJson.entry(changed, admin.view()));
    }

    private WeaponApi.Response create(byte[] bytes) throws IOException, WeaponFormatException {
        JsonObject json = body(bytes);
        if (!json.has("id") || !json.get("id").isJsonPrimitive()) {
            throw new WeaponFormatException("id", "missing");
        }
        MobId id = id(json.get("id").getAsString());
        MobDefinition definition = MobFileJson.fromJson(id, json, admin.stats(), "id");
        admin.create(definition);
        return WeaponApi.Response.json(202, MobJson.entry(definition, admin.view()));
    }

    /** What the form can offer: bodies, behavior types, the speed and size limits, slots. */
    private static JsonObject options() {
        JsonObject json = new JsonObject();
        json.add("bodies", strings(MobFileJson.bodies()));
        json.add("behaviors", strings(MobFileJson.behaviorTypes()));
        json.addProperty("maxSpeed", Behavior.MAX_SPEED);
        json.addProperty("minScale", MobLook.MIN_SCALE);
        json.addProperty("maxScale", MobLook.MAX_SCALE);
        JsonArray slots = new JsonArray();
        for (EquipmentSlotKind slot : EquipmentSlotKind.values()) {
            slots.add(slot.name().toLowerCase(Locale.ROOT));
        }
        json.add("slots", slots);
        return json;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonObject report(MobReloadReport report) {
        JsonObject json = new JsonObject();
        json.add("changed", ids(report.changed()));
        json.add("pendingRestart", ids(report.pendingRestart()));
        json.add("fileMissing", ids(report.fileMissing()));
        json.add("bodyWaits", ids(report.bodyWaits()));
        return json;
    }

    private static JsonArray ids(List<MobId> ids) {
        JsonArray array = new JsonArray();
        ids.forEach(id -> array.add(id.value()));
        return array;
    }
}
