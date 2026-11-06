package io.github.alextixu.mobarmory.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.stat.ModifierOperation;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatId;
import io.github.alextixu.mobarmory.core.stat.StatModifier;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The weapon file format, version 1 (decision O1 in 10-weapon-data-web-open-questions.md):
 * <pre>{@code
 * {
 *   "format": 1,
 *   "name": "Heavy Sword",
 *   "slot": "mainhand",
 *   "look": { "item": "minecraft:netherite_sword" },
 *   "modifiers": [
 *     { "stat": "attack_damage", "add": 6 },
 *     { "stat": "movement_speed", "percent": -30 }
 *   ]
 * }
 * }</pre>
 * The id is the file name. {@code percent} is written as a percentage ({@code -30} is -30%); chances such as
 * crit chance use {@code add} with a value from 0 to 1. The web API uses the same format, so there is one set
 * of rules for files and for the web page (Q1: no second validator).
 */
public final class WeaponJson {
    public static final int FORMAT = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> TOP_LEVEL = Set.of("format", "name", "slot", "look", "modifiers");

    private WeaponJson() {
    }

    /**
     * Reads a weapon file.
     *
     * @param id    the weapon id (the file name without {@code .json})
     * @param text  the file content
     * @param stats the registered stats; a modifier on any other stat is an error (B2)
     */
    public static EquipmentDefinition read(EquipmentId id, String text, StatCatalog stats) throws WeaponFormatException {
        return fromJson(id, parse(text), stats);
    }

    /** Parses strict JSON; the error names the line and column (B1). */
    public static JsonObject parse(String text) throws WeaponFormatException {
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1); // Windows Notepad writes a byte order mark
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement root = GSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new WeaponFormatException("", "unexpected text after the closing }");
            }
            if (root == null || !root.isJsonObject()) {
                throw new WeaponFormatException("", "must be a JSON object { ... }");
            }
            return root.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new WeaponFormatException("", "not valid JSON: " + e.getMessage());
        }
    }

    /**
     * Reads a weapon from an already parsed object.
     *
     * @param ignored top-level keys that belong to the caller (the web API's {@code version}) and are skipped
     */
    public static EquipmentDefinition fromJson(EquipmentId id, JsonObject json, StatCatalog stats, String... ignored)
        throws WeaponFormatException {
        Set<String> skip = Set.of(ignored);
        for (String key : json.keySet()) {
            if (!TOP_LEVEL.contains(key) && !skip.contains(key)) {
                throw new WeaponFormatException(key, "unknown field (known: format, name, slot, look, modifiers)");
            }
        }
        if (json.has("format")) {
            BigDecimal format = number(json.get("format"), "format");
            if (format.compareTo(BigDecimal.valueOf(FORMAT)) != 0) {
                throw new WeaponFormatException("format", "this version of the mod reads format " + FORMAT + ", got " + format);
            }
        }
        String name = string(json, "name", "name");
        EquipmentSlotKind slot = slot(string(json, "slot", "slot"));
        EquipmentLook look = look(json.get("look"));
        List<StatModifier> modifiers = modifiers(json.get("modifiers"), stats);
        try {
            return new EquipmentDefinition(id, name, slot, modifiers, look);
        } catch (IllegalArgumentException e) {
            String field = e.getMessage().contains("more than once") ? "modifiers" : "name";
            throw new WeaponFormatException(field, e.getMessage());
        }
    }

    /** The file content for a weapon, pretty-printed; reading it back gives an equal definition. */
    public static String write(EquipmentDefinition definition) {
        return GSON.toJson(toJson(definition)) + "\n";
    }

    /** The weapon as a JSON object, in the same format as the file. */
    public static JsonObject toJson(EquipmentDefinition definition) {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("name", definition.name());
        json.addProperty("slot", definition.slot().name().toLowerCase(Locale.ROOT));
        JsonObject look = new JsonObject();
        switch (definition.look()) {
            case EquipmentLook.VanillaItem item -> look.addProperty("item", item.item());
            case EquipmentLook.Texture texture -> look.addProperty("texture", texture.texture());
        }
        json.add("look", look);
        JsonArray modifiers = new JsonArray();
        for (StatModifier modifier : definition.modifiers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("stat", modifier.stat().value());
            if (modifier.operation() == ModifierOperation.MULTIPLY) {
                entry.add("percent", plain(BigDecimal.valueOf(modifier.amount()).movePointRight(2)));
            } else {
                entry.add("add", plain(BigDecimal.valueOf(modifier.amount())));
            }
            modifiers.add(entry);
        }
        json.add("modifiers", modifiers);
        return json;
    }

    /** {@code 6.0} is written as {@code 6} and {@code 30} never as {@code 3E+1}. */
    private static JsonPrimitive plain(BigDecimal value) {
        return new JsonPrimitive(new BigDecimal(value.stripTrailingZeros().toPlainString()));
    }

    private static String string(JsonObject json, String key, String field) throws WeaponFormatException {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) {
            throw new WeaponFormatException(field, "missing");
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new WeaponFormatException(field, "must be text in quotes");
        }
        return value.getAsString();
    }

    private static BigDecimal number(JsonElement value, String field) throws WeaponFormatException {
        if (value == null || value.isJsonNull()) {
            throw new WeaponFormatException(field, "missing");
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new WeaponFormatException(field, "must be a number, got " + value);
        }
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException e) {
            throw new WeaponFormatException(field, "must be a number, got " + value);
        }
    }

    private static EquipmentSlotKind slot(String text) throws WeaponFormatException {
        for (EquipmentSlotKind kind : EquipmentSlotKind.values()) {
            if (kind.name().toLowerCase(Locale.ROOT).equals(text)) {
                return kind;
            }
        }
        throw new WeaponFormatException("slot", "must be one of mainhand, offhand, head, chest, legs, feet; got '" + text + "'");
    }

    private static EquipmentLook look(JsonElement value) throws WeaponFormatException {
        if (value == null || !value.isJsonObject()) {
            throw new WeaponFormatException("look", "missing; write { \"item\": \"minecraft:iron_sword\" } or { \"texture\": \"my_texture\" }");
        }
        JsonObject look = value.getAsJsonObject();
        if (look.size() != 1 || !(look.has("item") || look.has("texture"))) {
            throw new WeaponFormatException("look", "must have exactly one of \"item\" or \"texture\"");
        }
        try {
            return look.has("item")
                ? EquipmentLook.item(string(look, "item", "look.item"))
                : EquipmentLook.texture(string(look, "texture", "look.texture"));
        } catch (IllegalArgumentException e) {
            throw new WeaponFormatException(look.has("item") ? "look.item" : "look.texture", e.getMessage());
        }
    }

    private static List<StatModifier> modifiers(JsonElement value, StatCatalog stats) throws WeaponFormatException {
        List<StatModifier> modifiers = new ArrayList<>();
        if (value == null || value.isJsonNull()) {
            return modifiers;
        }
        if (!value.isJsonArray()) {
            throw new WeaponFormatException("modifiers", "must be a list [ ... ]");
        }
        JsonArray array = value.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            String at = "modifiers[" + i + "]";
            if (!array.get(i).isJsonObject()) {
                throw new WeaponFormatException(at, "must be an object { \"stat\": ..., \"add\": ... }");
            }
            modifiers.add(modifier(array.get(i).getAsJsonObject(), stats, at));
        }
        return modifiers;
    }

    private static StatModifier modifier(JsonObject json, StatCatalog stats, String at) throws WeaponFormatException {
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!Set.of("stat", "add", "percent").contains(entry.getKey())) {
                throw new WeaponFormatException(at + "." + entry.getKey(), "unknown field (known: stat, add, percent)");
            }
        }
        StatId stat;
        try {
            stat = StatId.of(string(json, "stat", at + ".stat"));
        } catch (IllegalArgumentException e) {
            throw new WeaponFormatException(at + ".stat", e.getMessage());
        }
        if (!stats.contains(stat)) {
            throw new WeaponFormatException(at + ".stat", "unknown stat '" + stat + "' (known: " + known(stats) + ")");
        }
        if (json.has("add") == json.has("percent")) {
            throw new WeaponFormatException(at, "needs exactly one of \"add\" or \"percent\"");
        }
        boolean percent = json.has("percent");
        String field = at + (percent ? ".percent" : ".add");
        BigDecimal amount = number(json.get(percent ? "percent" : "add"), field);
        double value = percent ? amount.movePointLeft(2).doubleValue() : amount.doubleValue();
        try {
            return percent ? StatModifier.percent(stat, value) : StatModifier.add(stat, value);
        } catch (IllegalArgumentException e) {
            throw new WeaponFormatException(field, e.getMessage());
        }
    }

    private static String known(StatCatalog stats) {
        return String.join(", ", stats.all().stream().map(stat -> stat.id().value()).toList());
    }
}
