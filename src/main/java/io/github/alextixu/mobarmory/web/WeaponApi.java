package io.github.alextixu.mobarmory.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.data.BuiltInTextures;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import io.github.alextixu.mobarmory.data.WeaponJson;
import io.github.alextixu.mobarmory.data.WeaponLoadException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The web API, version 1 (08-weapon-data-web-boundary-note.md, section 3). Turns a request into a response using
 * only {@link WeaponAdmin} and {@link MobAdmin}; no HTTP server and no Minecraft, so every route is tested directly.
 * <p>
 * Weapons travel in the same JSON format as the files ({@link WeaponJson}), so the page and the files follow one
 * set of rules. Mobs are read only ({@link MobJson}). Errors are always
 * {@code {"error": ..., "message": ..., "fields": {...}}}.
 */
public final class WeaponApi {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern WEAPON = Pattern.compile("/api/v1/weapons/([^/]+)");
    private static final Pattern TEXTURE = Pattern.compile("/api/v1/textures/([^/]+)");

    /** One HTTP response. */
    public record Response(int status, String contentType, byte[] body) {
        static Response json(int status, JsonElement json) {
            return new Response(status, "application/json; charset=utf-8", GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
        }

        static Response error(int status, String code, String message) {
            return error(status, code, message, Map.of());
        }

        static Response error(int status, String code, String message, Map<String, String> fields) {
            JsonObject json = new JsonObject();
            json.addProperty("error", code);
            json.addProperty("message", message);
            JsonObject fieldErrors = new JsonObject();
            fields.forEach(fieldErrors::addProperty);
            json.add("fields", fieldErrors);
            return json(status, json);
        }
    }

    private final WeaponAdmin admin;
    private final MobAdmin mobs;

    public WeaponApi(WeaponAdmin admin, MobAdmin mobs) {
        this.admin = admin;
        this.mobs = mobs;
    }

    /**
     * @param method HTTP method
     * @param path   path without the query string, e.g. {@code /api/v1/weapons/heavy_sword}
     * @param body   request body (already size-limited by the server)
     */
    public Response handle(String method, String path, byte[] body) {
        try {
            return route(method, path, body);
        } catch (WeaponFormatException e) {
            return Response.error(400, "invalid", e.getMessage(), Map.of(e.field().isEmpty() ? "body" : e.field(), e.reason()));
        } catch (NoSuchElementException e) {
            return Response.error(404, "not_found", e.getMessage());
        } catch (WeaponLoadException e) {
            JsonObject json = errorJson("invalid_files", e.getMessage());
            JsonArray problems = new JsonArray();
            e.problems().forEach(problems::add);
            json.add("problems", problems);
            return Response.json(400, json);
        } catch (IOException e) {
            return Response.error(500, "io", "Could not save: " + e.getMessage());
        }
    }

    private Response route(String method, String path, byte[] body)
        throws IOException, WeaponFormatException, WeaponLoadException {
        if (path.equals("/api/v1/mobs")) {
            // Read only: mobs are defined in content/ModMobs and change only with a new build (R4).
            return method.equals("GET") ? Response.json(200, MobJson.list(mobs.mobs())) : notAllowed();
        }
        if (path.equals("/api/v1/stats")) {
            return method.equals("GET") ? Response.json(200, stats()) : notAllowed();
        }
        if (path.equals("/api/v1/looks")) {
            return method.equals("GET") ? Response.json(200, looks()) : notAllowed();
        }
        if (path.equals("/api/v1/reload")) {
            return method.equals("POST") ? Response.json(200, report(admin.reload())) : notAllowed();
        }
        if (path.equals("/api/v1/weapons")) {
            return switch (method) {
                case "GET" -> Response.json(200, weapons());
                case "POST" -> create(body);
                default -> notAllowed();
            };
        }
        Matcher weapon = WEAPON.matcher(path);
        if (weapon.matches()) {
            EquipmentId id = id(weapon.group(1));
            return switch (method) {
                case "GET" -> Response.json(200, weapon(id));
                case "PUT" -> update(id, body);
                default -> notAllowed();
            };
        }
        Matcher texture = TEXTURE.matcher(path);
        if (texture.matches()) {
            String name = texture.group(1);
            return switch (method) {
                case "GET" -> admin.texture(name)
                    .map(png -> new Response(200, "image/png", png))
                    .orElseGet(() -> Response.error(404, "not_found", "No texture '" + name + "'"));
                case "PUT" -> {
                    admin.saveTexture(name, body);
                    JsonObject json = new JsonObject();
                    json.addProperty("name", name);
                    yield Response.json(201, json);
                }
                default -> notAllowed();
            };
        }
        return Response.error(404, "not_found", "No such page: " + path);
    }

    private static Response notAllowed() {
        return Response.error(405, "method_not_allowed", "This address does not accept that request");
    }

    private static JsonObject errorJson(String code, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("error", code);
        json.addProperty("message", message);
        json.add("fields", new JsonObject());
        return json;
    }

    private static EquipmentId id(String text) throws WeaponFormatException {
        try {
            return EquipmentId.of(text);
        } catch (IllegalArgumentException e) {
            throw new WeaponFormatException("id", e.getMessage());
        }
    }

    private JsonArray stats() {
        JsonArray array = new JsonArray();
        for (StatDefinition stat : admin.stats().all()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", stat.id().value());
            json.addProperty("default", stat.defaultValue());
            json.addProperty("min", stat.min());
            json.addProperty("max", stat.max());
            json.addProperty("custom", stat.isCustom());
            json.addProperty("chance", stat.isCustom() && stat.max() <= 1.0);
            array.add(json);
        }
        return array;
    }

    private JsonObject looks() throws IOException {
        JsonObject json = new JsonObject();
        JsonArray items = new JsonArray();
        admin.vanillaLooks().forEach(items::add);
        JsonArray textures = new JsonArray();
        admin.textures().forEach(textures::add);
        JsonArray builtIn = new JsonArray();
        BuiltInTextures.NAMES.forEach(builtIn::add);
        json.add("items", items);
        json.add("textures", textures);
        json.add("builtInTextures", builtIn);
        return json;
    }

    private JsonObject weapons() {
        EquipmentLibrary.View view = admin.view();
        JsonArray weapons = new JsonArray();
        for (EquipmentDefinition definition : view.catalog().all()) {
            weapons.add(entry(definition, view));
        }
        for (EquipmentDefinition definition : view.pending().values()) {
            weapons.add(entry(definition, view));
        }
        JsonObject json = new JsonObject();
        json.add("weapons", weapons);
        return json;
    }

    private JsonObject weapon(EquipmentId id) {
        EquipmentLibrary.View view = admin.view();
        Optional<EquipmentDefinition> definition = view.catalog().find(id).or(() -> Optional.ofNullable(view.pending().get(id)));
        return entry(definition.orElseThrow(() -> new NoSuchElementException("Unknown weapon '" + id + "'")), view);
    }

    /** {@code {"id", "status", "weapon": {file format}}}; status is active, pending_restart or file_missing. */
    private static JsonObject entry(EquipmentDefinition definition, EquipmentLibrary.View view) {
        JsonObject json = new JsonObject();
        json.addProperty("id", definition.id().value());
        boolean registered = view.catalog().find(definition.id()).isPresent();
        String status = !registered ? "pending_restart" : view.fileMissing().contains(definition.id()) ? "file_missing" : "active";
        json.addProperty("status", status);
        json.add("weapon", WeaponJson.toJson(definition));
        return json;
    }

    private JsonObject body(byte[] body) throws WeaponFormatException {
        return WeaponJson.parse(new String(body, StandardCharsets.UTF_8));
    }

    private Response update(EquipmentId id, byte[] bytes) throws IOException, WeaponFormatException {
        EquipmentDefinition changed = WeaponJson.fromJson(id, body(bytes), admin.stats(), "id", "version");
        admin.update(changed);
        return Response.json(200, entry(changed, admin.view()));
    }

    private Response create(byte[] bytes) throws IOException, WeaponFormatException {
        JsonObject json = body(bytes);
        if (!json.has("id") || !json.get("id").isJsonPrimitive()) {
            throw new WeaponFormatException("id", "missing");
        }
        EquipmentId id = id(json.get("id").getAsString());
        EquipmentDefinition definition = WeaponJson.fromJson(id, json, admin.stats(), "id");
        admin.create(definition);
        return Response.json(202, entry(definition, admin.view()));
    }

    private static JsonObject report(ReloadReport report) {
        JsonObject json = new JsonObject();
        json.add("changed", ids(report.changed()));
        json.add("pendingRestart", ids(report.pendingRestart()));
        json.add("fileMissing", ids(report.fileMissing()));
        json.add("slotWaits", ids(report.slotWaits()));
        return json;
    }

    private static JsonArray ids(List<EquipmentId> ids) {
        JsonArray array = new JsonArray();
        ids.forEach(id -> array.add(id.value()));
        return array;
    }
}
