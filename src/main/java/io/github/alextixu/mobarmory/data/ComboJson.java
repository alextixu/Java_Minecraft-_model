package io.github.alextixu.mobarmory.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.alextixu.mobarmory.core.combo.ComboDefinition;
import io.github.alextixu.mobarmory.core.combo.ComboEffect;
import io.github.alextixu.mobarmory.core.combo.ComboInput;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The combo file format, version 1 (R3; decision Q2 changed to JSON on 11/07):
 * <pre>{@code
 * {
 *   "format": 1,
 *   "name": "Lunge",
 *   "sequence": ["walk", "run"],
 *   "window_ticks": 30,
 *   "effect": { "type": "damage_multiplier", "value": 1.5 }
 * }
 * }</pre>
 * The id is the file name. {@code sequence} is the order of the two swings: {@code walk} or {@code run}, decided by
 * movement speed (Q4 A). {@code window_ticks} is how long the second swing may come after the first (20 ticks = 1
 * second; that many still counts). {@code effect} picks one of {@link ComboEffectTypes}. Same strict JSON and the same
 * kind of error message as the weapon files ({@link WeaponJson}): every error names its field.
 */
public final class ComboJson {
    public static final int FORMAT = 1;

    private static final Set<String> TOP_LEVEL = Set.of("format", "name", "sequence", "window_ticks", "effect");

    private ComboJson() {
    }

    /**
     * Reads a combo file.
     *
     * @param id      the combo id (the file name without {@code .json})
     * @param text    the file content
     * @param effects the effect kinds a file may use
     * @throws IllegalArgumentException if the id itself is not valid; the caller reports it as a bad file name
     */
    public static ComboDefinition read(String id, String text, ComboEffectTypes effects) throws WeaponFormatException {
        JsonObject json = WeaponJson.parse(text);
        for (String key : json.keySet()) {
            if (!TOP_LEVEL.contains(key)) {
                throw new WeaponFormatException(key, "unknown field (known: format, name, sequence, window_ticks, effect)");
            }
        }
        if (json.has("format")) {
            BigDecimal format = WeaponJson.number(json.get("format"), "format");
            if (format.compareTo(BigDecimal.valueOf(FORMAT)) != 0) {
                throw new WeaponFormatException("format", "this version of the mod reads format " + FORMAT + ", got " + format);
            }
        }
        String name = WeaponJson.string(json, "name", "name");
        List<ComboInput> sequence = sequence(json.get("sequence"));
        long window = windowTicks(json.get("window_ticks"));
        ComboEffect effect = effects.read(json.get("effect"));
        try {
            return new ComboDefinition(id, name, sequence.get(0), sequence.get(1), window, effect);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("name must")) {
                throw new WeaponFormatException("name", e.getMessage());
            }
            throw e;
        }
    }

    /** Exactly two swings in this version: the player's first and second hit. */
    private static List<ComboInput> sequence(JsonElement value) throws WeaponFormatException {
        if (value == null || value.isJsonNull()) {
            throw new WeaponFormatException("sequence", "missing");
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 2) {
            throw new WeaponFormatException("sequence", "must be a list of two swings, like [\"walk\", \"run\"]");
        }
        JsonArray steps = value.getAsJsonArray();
        List<ComboInput> inputs = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            String field = "sequence[" + i + "]";
            JsonElement step = steps.get(i);
            if (!step.isJsonPrimitive() || !step.getAsJsonPrimitive().isString()) {
                throw new WeaponFormatException(field, "must be \"walk\" or \"run\"");
            }
            inputs.add(input(step.getAsString(), field));
        }
        return inputs;
    }

    private static ComboInput input(String text, String field) throws WeaponFormatException {
        for (ComboInput input : ComboInput.values()) {
            if (input.name().toLowerCase(Locale.ROOT).equals(text)) {
                return input;
            }
        }
        throw new WeaponFormatException(field, "unknown swing '" + text + "' (known: walk, run)");
    }

    private static long windowTicks(JsonElement value) throws WeaponFormatException {
        BigDecimal ticks = WeaponJson.number(value, "window_ticks");
        if (ticks.stripTrailingZeros().scale() > 0
            || ticks.compareTo(BigDecimal.ONE) < 0
            || ticks.compareTo(BigDecimal.valueOf(ComboDefinition.MAX_WINDOW_TICKS)) > 0) {
            throw new WeaponFormatException("window_ticks",
                "must be a whole number of ticks from 1 to " + ComboDefinition.MAX_WINDOW_TICKS + " (20 ticks = 1 second), got " + ticks);
        }
        return ticks.longValueExact();
    }
}
