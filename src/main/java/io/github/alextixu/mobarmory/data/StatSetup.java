package io.github.alextixu.mobarmory.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The stats the game runs with, read once at startup from {@code config/mobarmory/stats/} (section E). Never changes
 * afterwards: attributes can only be registered at startup (Q8 A).
 *
 * @param catalog          every stat: the built-in ones first, then the player's, by file name (O3)
 * @param names            display names of new attributes that set one (Q7 A)
 * @param unsynced         new attributes whose files say {@code "sync": false} (O2)
 * @param builtInsFromCode built-in stats whose file was missing, so the built-in value is used (Q5 A)
 */
public record StatSetup(StatCatalog catalog, Map<StatId, String> names, Set<StatId> unsynced, List<StatId> builtInsFromCode) {
    public StatSetup {
        Objects.requireNonNull(catalog, "catalog");
        names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
        unsynced = Set.copyOf(unsynced);
        builtInsFromCode = List.copyOf(builtInsFromCode);
    }

    /** Whether clients receive this stat's value; true for every stat except new attributes that turned it off. */
    public boolean synced(StatId id) {
        return !unsynced.contains(id);
    }

    /**
     * The translation key of a new attribute's name. The Forge side registers the attribute with this key, and the
     * runtime resource pack supplies the name for it.
     */
    public static String translationKey(String modId, StatId id) {
        return "attribute.name." + modId + "." + id.value();
    }

    /**
     * A language file ({@code lang/en_us.json}) with the display name of every new attribute that set one. Minecraft
     * merges it with the mod's own language file and falls back to English in other languages.
     */
    public String languageFile(String modId) {
        JsonObject json = new JsonObject();
        names.forEach((id, name) -> json.addProperty(translationKey(modId, id), name));
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json);
    }
}
