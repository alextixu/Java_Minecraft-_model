package io.github.alextixu.mobarmory.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.mob.AttackPlayersBehavior;
import io.github.alextixu.mobarmory.core.mob.Behavior;
import io.github.alextixu.mobarmory.core.mob.LeapBehavior;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLook;
import io.github.alextixu.mobarmory.core.mob.NeutralBehavior;
import io.github.alextixu.mobarmory.core.mob.SpeedBehavior;
import io.github.alextixu.mobarmory.core.mob.WanderBehavior;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleFunction;

/**
 * The mob file format, version 1 (R4b, 23-r4b-mob-admin-open-questions.md O1):
 * <pre>{@code
 * {
 *   "format": 1,
 *   "name": "Brute",
 *   "look": { "body": "zombie", "scale": 1.2 },
 *   "stats": { "max_health": 40, "attack_damage": 4, "movement_speed": 0.25 },
 *   "equipment": { "mainhand": "heavy_sword" },
 *   "behaviors": [ { "type": "wander", "speed": 1.0 }, { "type": "attack_players", "speed": 1.0 } ]
 * }
 * }</pre>
 * The id is the file name. The web API uses the same format, so files and the page follow one set of rules (like the
 * weapons' {@link WeaponJson}, whose strict parser this reuses). Every mistake names its field, e.g.
 * {@code behaviors[1].speed}, so the web form can mark that box.
 * <p>
 * Behaviors are read through a table from {@code type} to its format, the same idea as the binder table in
 * {@code forge/BehaviorGoals}: a new behavior is one more entry, not another branch.
 */
public final class MobFileJson {
    public static final int FORMAT = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> TOP_LEVEL = Set.of("format", "name", "look", "stats", "equipment", "behaviors");

    /** How one kind of behavior is written in a file. */
    private interface BehaviorFormat {
        Behavior read(JsonObject json, String at) throws WeaponFormatException;

        void write(Behavior behavior, JsonObject json);
    }

    /** {@code {"type": ..., "speed": 1.0}}; the speed may be left out and is then 1.0. */
    private record SpeedFormat(DoubleFunction<Behavior> create) implements BehaviorFormat {
        @Override
        public Behavior read(JsonObject json, String at) throws WeaponFormatException {
            onlyFields(json, at, Set.of("type", "speed"));
            double speed = json.has("speed") ? number(json.get("speed"), at + ".speed").doubleValue() : 1.0;
            try {
                return create.apply(speed);
            } catch (IllegalArgumentException e) {
                throw new WeaponFormatException(at + ".speed", "must be above 0 and at most " + Behavior.MAX_SPEED + ", got " + speed);
            }
        }

        @Override
        public void write(Behavior behavior, JsonObject json) {
            json.add("speed", plain(((SpeedBehavior) behavior).speed()));
        }
    }

    /** {@code {"type": ...}} with nothing else. */
    private record PlainFormat(Behavior instance) implements BehaviorFormat {
        @Override
        public Behavior read(JsonObject json, String at) throws WeaponFormatException {
            onlyFields(json, at, Set.of("type"));
            return instance;
        }

        @Override
        public void write(Behavior behavior, JsonObject json) {
        }
    }

    /** Every behavior a file can name, in the order the web page lists them. */
    private static final Map<String, BehaviorFormat> BEHAVIORS = behaviors();

    private static Map<String, BehaviorFormat> behaviors() {
        Map<String, BehaviorFormat> formats = new LinkedHashMap<>();
        formats.put(WanderBehavior.NAME, new SpeedFormat(WanderBehavior::new));
        formats.put(AttackPlayersBehavior.NAME, new SpeedFormat(AttackPlayersBehavior::new));
        formats.put(NeutralBehavior.NAME, new SpeedFormat(NeutralBehavior::new));
        formats.put(LeapBehavior.NAME, new PlainFormat(new LeapBehavior()));
        return formats;
    }

    private MobFileJson() {
    }

    /** The behavior types a file can use, for the web form ({@code GET /api/v1/mob-options}). */
    public static List<String> behaviorTypes() {
        return List.copyOf(BEHAVIORS.keySet());
    }

    /** The bodies a file can use, as written in a file ({@code zombie}, ...). */
    public static List<String> bodies() {
        List<String> bodies = new ArrayList<>();
        for (MobLook.Body body : MobLook.Body.values()) {
            bodies.add(body.name().toLowerCase(Locale.ROOT));
        }
        return bodies;
    }

    /**
     * Reads a mob file.
     *
     * @param id    the mob id (the file name without {@code .json})
     * @param stats every stat in the game; a mob may only set these (B2)
     */
    public static MobDefinition read(MobId id, String text, StatCatalog stats) throws WeaponFormatException {
        return fromJson(id, WeaponJson.parse(text), stats);
    }

    /**
     * Reads a mob from an already parsed object.
     *
     * @param ignored top-level keys that belong to the caller (the web API's {@code id}) and are skipped
     */
    public static MobDefinition fromJson(MobId id, JsonObject json, StatCatalog stats, String... ignored)
        throws WeaponFormatException {
        Set<String> skip = Set.of(ignored);
        for (String key : json.keySet()) {
            if (!TOP_LEVEL.contains(key) && !skip.contains(key)) {
                throw new WeaponFormatException(key, "unknown field (known: format, name, look, stats, equipment, behaviors)");
            }
        }
        if (json.has("format")) {
            BigDecimal format = number(json.get("format"), "format");
            if (format.compareTo(BigDecimal.valueOf(FORMAT)) != 0) {
                throw new WeaponFormatException("format", "this version of the mod reads format " + FORMAT + ", got " + format);
            }
        }
        String name = string(json.get("name"), "name");
        MobLook look = look(json.get("look"));
        Map<StatId, Double> statValues = stats(json.get("stats"), stats);
        Map<EquipmentSlotKind, EquipmentId> equipment = equipment(json.get("equipment"));
        List<Behavior> behaviors = behaviorList(json.get("behaviors"));
        try {
            return new MobDefinition(id, name, look, statValues, equipment, behaviors);
        } catch (IllegalArgumentException e) {
            String field = e.getMessage().contains("name must") ? "name" : "behaviors";
            String message = e.getMessage();
            String prefix = "Mob '" + id + "': ";
            throw new WeaponFormatException(field, message.startsWith(prefix) ? message.substring(prefix.length()) : message);
        }
    }

    /** The file content for a mob, pretty-printed; reading it back gives an equal definition. */
    public static String write(MobDefinition definition) {
        return GSON.toJson(toJson(definition)) + "\n";
    }

    /** The mob as a JSON object, in the same format as the file. */
    public static JsonObject toJson(MobDefinition definition) {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("name", definition.name());
        JsonObject look = new JsonObject();
        look.addProperty("body", definition.look().body().name().toLowerCase(Locale.ROOT));
        look.add("scale", plain(definition.look().scale()));
        json.add("look", look);
        JsonObject stats = new JsonObject();
        definition.stats().forEach((stat, value) -> stats.add(stat.value(), plain(value)));
        json.add("stats", stats);
        JsonObject equipment = new JsonObject();
        definition.equipment().forEach((slot, piece) -> equipment.addProperty(slot.name().toLowerCase(Locale.ROOT), piece.value()));
        json.add("equipment", equipment);
        JsonArray behaviors = new JsonArray();
        for (Behavior behavior : definition.behaviors()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", behavior.name());
            BehaviorFormat format = BEHAVIORS.get(behavior.name());
            if (format == null) {
                throw new IllegalArgumentException("Behavior '" + behavior.name() + "' has no file format");
            }
            format.write(behavior, entry);
            behaviors.add(entry);
        }
        json.add("behaviors", behaviors);
        return json;
    }

    private static MobLook look(JsonElement value) throws WeaponFormatException {
        if (value == null || !value.isJsonObject()) {
            throw new WeaponFormatException("look", "missing; write { \"body\": \"zombie\", \"scale\": 1.0 }");
        }
        JsonObject look = value.getAsJsonObject();
        onlyFields(look, "look", Set.of("body", "scale"));
        String bodyText = string(look.get("body"), "look.body");
        MobLook.Body body = null;
        for (MobLook.Body candidate : MobLook.Body.values()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(bodyText)) {
                body = candidate;
            }
        }
        if (body == null) {
            throw new WeaponFormatException("look.body", "must be one of " + String.join(", ", bodies()) + "; got '" + bodyText + "'");
        }
        double scale = look.has("scale") ? number(look.get("scale"), "look.scale").doubleValue() : 1.0;
        try {
            return new MobLook(body, scale);
        } catch (IllegalArgumentException e) {
            throw new WeaponFormatException("look.scale", e.getMessage());
        }
    }

    private static Map<StatId, Double> stats(JsonElement value, StatCatalog catalog) throws WeaponFormatException {
        Map<StatId, Double> stats = new LinkedHashMap<>();
        if (value == null || value.isJsonNull()) {
            return stats;
        }
        if (!value.isJsonObject()) {
            throw new WeaponFormatException("stats", "must be an object { \"max_health\": 20, ... }");
        }
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            String field = "stats." + entry.getKey();
            StatId id;
            try {
                id = StatId.of(entry.getKey());
            } catch (IllegalArgumentException e) {
                throw new WeaponFormatException(field, e.getMessage());
            }
            StatDefinition stat = catalog.find(id).orElseThrow(() -> new WeaponFormatException(field,
                "unknown stat '" + id + "' (known: " + String.join(", ", catalog.all().stream().map(s -> s.id().value()).toList()) + ")"));
            double number = number(entry.getValue(), field).doubleValue();
            if (number < stat.min() || number > stat.max()) {
                throw new WeaponFormatException(field, "must be from " + plain(stat.min()) + " to " + plain(stat.max()) + ", got " + plain(number));
            }
            stats.put(id, number);
        }
        return stats;
    }

    private static Map<EquipmentSlotKind, EquipmentId> equipment(JsonElement value) throws WeaponFormatException {
        Map<EquipmentSlotKind, EquipmentId> equipment = new EnumMap<>(EquipmentSlotKind.class);
        if (value == null || value.isJsonNull()) {
            return equipment;
        }
        if (!value.isJsonObject()) {
            throw new WeaponFormatException("equipment", "must be an object { \"mainhand\": \"heavy_sword\" }");
        }
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            String field = "equipment." + entry.getKey();
            EquipmentSlotKind slot = null;
            for (EquipmentSlotKind kind : EquipmentSlotKind.values()) {
                if (kind.name().toLowerCase(Locale.ROOT).equals(entry.getKey())) {
                    slot = kind;
                }
            }
            if (slot == null) {
                throw new WeaponFormatException(field, "unknown slot (known: mainhand, offhand, head, chest, legs, feet)");
            }
            try {
                equipment.put(slot, EquipmentId.of(string(entry.getValue(), field)));
            } catch (IllegalArgumentException e) {
                throw new WeaponFormatException(field, e.getMessage());
            }
        }
        return equipment;
    }

    private static List<Behavior> behaviorList(JsonElement value) throws WeaponFormatException {
        List<Behavior> behaviors = new ArrayList<>();
        if (value == null || value.isJsonNull()) {
            return behaviors;
        }
        if (!value.isJsonArray()) {
            throw new WeaponFormatException("behaviors", "must be a list [ { \"type\": \"wander\" }, ... ]");
        }
        JsonArray array = value.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            String at = "behaviors[" + i + "]";
            if (!array.get(i).isJsonObject()) {
                throw new WeaponFormatException(at, "must be an object { \"type\": \"wander\" }");
            }
            JsonObject json = array.get(i).getAsJsonObject();
            String type = string(json.get("type"), at + ".type");
            BehaviorFormat format = BEHAVIORS.get(type);
            if (format == null) {
                throw new WeaponFormatException(at + ".type", "unknown behavior '" + type + "' (known: " + String.join(", ", behaviorTypes()) + ")");
            }
            behaviors.add(format.read(json, at));
        }
        return behaviors;
    }

    private static void onlyFields(JsonObject json, String at, Set<String> known) throws WeaponFormatException {
        for (String key : json.keySet()) {
            if (!known.contains(key)) {
                throw new WeaponFormatException(at + "." + key, "unknown field (known: " + String.join(", ", known.stream().sorted().toList()) + ")");
            }
        }
    }

    private static String string(JsonElement value, String field) throws WeaponFormatException {
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

    /** {@code 40.0} is written as {@code 40} and {@code 0.25} never as {@code 2.5E-1}. */
    private static JsonPrimitive plain(double value) {
        return new JsonPrimitive(new BigDecimal(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()));
    }
}
