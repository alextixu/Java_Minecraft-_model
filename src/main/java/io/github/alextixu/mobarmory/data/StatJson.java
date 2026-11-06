package io.github.alextixu.mobarmory.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.Optional;
import java.util.Set;

/**
 * The stat file format, version 1 (section E, 24-stat-files-boundary-note.md). One file per stat; the file name is
 * the id:
 * <pre>{@code
 * config/mobarmory/stats/luck.json:  { "format": 1, "name": "Luck", "default": 0, "min": 0, "max": 1, "sync": true }
 * config/mobarmory/stats/jump.json:  { "format": 1, "attribute": "minecraft:jump_strength", "default": 0.42,
 *                                      "min": 0, "max": 32 }
 * }</pre>
 * Without {@code attribute} the stat is a new attribute of its own ({@code mobarmory:<id>}), a plain number with no
 * combat effect unless Java code adds one. With {@code attribute} it is Minecraft's own attribute, effect included.
 * {@code floor} (0 to 1) keeps the value at or above that share of the base value. {@code name} and {@code sync} only
 * apply to a new attribute.
 * <p>
 * Every mistake is an {@link IllegalArgumentException} whose message starts with the field, e.g.
 * {@code "default: must be a number"}.
 */
public final class StatJson {
    public static final int FORMAT = 1;
    private static final Set<String> FIELDS = Set.of("format", "name", "attribute", "default", "min", "max", "floor", "sync");
    private static final int MAX_NAME_LENGTH = 64;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private StatJson() {
    }

    /**
     * Reads one stat file.
     *
     * @param id the id the file name gives
     * @throws IllegalArgumentException naming the field that is wrong
     */
    public static StatEntry read(StatId id, String text) {
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("file: must be a JSON object");
            }
            json = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("file: not valid JSON (" + e.getMessage() + ")");
        }
        for (String key : json.keySet()) {
            if (!FIELDS.contains(key)) {
                throw new IllegalArgumentException(key + ": unknown field (known: format, name, attribute, default, min, max, floor, sync)");
            }
        }
        if (!json.has("format") || number(json, "format") != FORMAT) {
            throw new IllegalArgumentException("format: must be " + FORMAT);
        }
        double defaultValue = number(json, "default");
        double min = number(json, "min");
        double max = number(json, "max");
        double floor = json.has("floor") ? number(json, "floor") : 0;
        Optional<String> attribute = json.has("attribute") ? Optional.of(string(json, "attribute")) : Optional.empty();
        Optional<String> name = json.has("name") ? Optional.of(name(json)) : Optional.empty();
        boolean sync = !json.has("sync") || bool(json, "sync");
        if (attribute.isPresent() && (json.has("name") || json.has("sync"))) {
            String field = json.has("name") ? "name" : "sync";
            throw new IllegalArgumentException(field + ": only a new attribute (no \"attribute\" field) can set this; "
                + attribute.get() + " keeps Minecraft's own name and syncing");
        }
        StatDefinition definition = new StatDefinition(id, defaultValue, min, max, floor, attribute);
        return new StatEntry(definition, name, sync);
    }

    /** The file content for a stat: fields in a fixed order, optional ones only when they differ from the default. */
    public static String write(StatEntry entry) {
        StatDefinition definition = entry.definition();
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        entry.name().ifPresent(name -> json.addProperty("name", name));
        definition.vanillaAttribute().ifPresent(attribute -> json.addProperty("attribute", attribute));
        json.add("default", number(definition.defaultValue()));
        json.add("min", number(definition.min()));
        json.add("max", number(definition.max()));
        if (definition.floorRatio() != 0) {
            json.add("floor", number(definition.floorRatio()));
        }
        if (!entry.sync()) {
            json.addProperty("sync", false);
        }
        return GSON.toJson(json) + "\n";
    }

    /** Whole numbers are written without ".0", so the files read like the examples in the README. */
    private static JsonPrimitive number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? new JsonPrimitive((long) value) : new JsonPrimitive(value);
    }

    private static double number(JsonObject json, String field) {
        JsonElement element = json.get(field);
        if (element == null) {
            throw new IllegalArgumentException(field + ": missing");
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + ": must be a number");
        }
        return element.getAsDouble();
    }

    private static String string(JsonObject json, String field) {
        JsonElement element = json.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + ": must be text");
        }
        return element.getAsString();
    }

    private static boolean bool(JsonObject json, String field) {
        JsonElement element = json.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + ": must be true or false");
        }
        return element.getAsBoolean();
    }

    private static String name(JsonObject json) {
        String name = string(json, "name").strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name: must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }
}
