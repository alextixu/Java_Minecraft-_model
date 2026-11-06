package io.github.alextixu.mobarmory.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.alextixu.mobarmory.core.combo.ComboEffect;
import io.github.alextixu.mobarmory.core.combo.DamageMultiplier;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * The kinds of combo effect a combo file can pick by name (Strategy, R3 decision Q2). A file only chooses a kind and
 * fills in its parameters, for example {@code "effect": {"type": "damage_multiplier", "value": 1.5}}. A new kind of
 * effect is the only thing about combos that needs Java: one {@link ComboEffect} class in {@code core/combo} and one
 * entry in {@link #STANDARD}; no combo file, loader or listener changes (open/closed).
 */
public final class ComboEffectTypes {

    /** Reads one kind of effect from an effect object whose {@code type} is already known to be this kind. */
    @FunctionalInterface
    public interface Reader {
        ComboEffect read(JsonObject effect) throws WeaponFormatException;
    }

    /** The kinds this version of the mod knows. */
    public static final ComboEffectTypes STANDARD = new ComboEffectTypes(Map.of(
        "damage_multiplier", ComboEffectTypes::damageMultiplier));

    private final Map<String, Reader> readers;

    public ComboEffectTypes(Map<String, Reader> readers) {
        this.readers = new TreeMap<>(Objects.requireNonNull(readers, "readers"));
    }

    /** Names a file can use as {@code effect.type}, sorted. */
    public Set<String> names() {
        return readers.keySet();
    }

    /** Reads the {@code effect} object of a combo file; every error names its field, such as {@code effect.value}. */
    public ComboEffect read(JsonElement value) throws WeaponFormatException {
        if (value == null || value.isJsonNull()) {
            throw new WeaponFormatException("effect", "missing");
        }
        if (!value.isJsonObject()) {
            throw new WeaponFormatException("effect", "must be an object like {\"type\": \"damage_multiplier\", \"value\": 1.5}");
        }
        JsonObject effect = value.getAsJsonObject();
        String type = WeaponJson.string(effect, "type", "effect.type");
        Reader reader = readers.get(type);
        if (reader == null) {
            throw new WeaponFormatException("effect.type", "unknown effect '" + type + "' (known: "
                + String.join(", ", names()) + ")");
        }
        return reader.read(effect);
    }

    /** {@code {"type": "damage_multiplier", "value": 1.5}}: the second swing deals {@code value} times its damage. */
    static ComboEffect damageMultiplier(JsonObject effect) throws WeaponFormatException {
        onlyKnownFields(effect, "type", "value");
        BigDecimal value = WeaponJson.number(effect.get("value"), "effect.value");
        double factor = value.doubleValue();
        if (!(factor > 0) || Double.isInfinite(factor)) {
            throw new WeaponFormatException("effect.value", "must be a number above 0, got " + value);
        }
        return new DamageMultiplier(factor);
    }

    /** An effect may only have the fields its kind reads, so a typo such as {@code "valeu"} is reported, not ignored. */
    static void onlyKnownFields(JsonObject effect, String... known) throws WeaponFormatException {
        Set<String> allowed = Set.of(known);
        for (String key : effect.keySet()) {
            if (!allowed.contains(key)) {
                throw new WeaponFormatException("effect." + key, "unknown field (known: " + String.join(", ", known) + ")");
            }
        }
    }
}
