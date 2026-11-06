package io.github.alextixu.mobarmory.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.alextixu.mobarmory.core.mob.Behavior;
import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import java.util.Locale;

/**
 * The mob list the web page shows (R6, read only). Each mob is written as its definition says, with nothing added:
 * <pre>{@code
 * {"id": "brute", "look": {"body": "zombie", "scale": 1.2},
 *  "stats": {"max_health": 40, "attack_damage": 4, "movement_speed": 0.25},
 *  "equipment": {"mainhand": "heavy_sword"}, "behaviors": ["wander", "attack_players"]}
 * }</pre>
 * {@code stats} lists only the stats the definition sets; every other stat starts at its default. Slots are written
 * like the {@code slot} field of a weapon file.
 */
public final class MobJson {
    private MobJson() {
    }

    /** {@code {"mobs": [...]}} in catalog order. */
    public static JsonObject list(MobCatalog catalog) {
        JsonArray mobs = new JsonArray();
        catalog.all().forEach(mob -> mobs.add(toJson(mob)));
        JsonObject json = new JsonObject();
        json.add("mobs", mobs);
        return json;
    }

    public static JsonObject toJson(MobDefinition mob) {
        JsonObject json = new JsonObject();
        json.addProperty("id", mob.id().value());

        JsonObject look = new JsonObject();
        look.addProperty("body", mob.look().body().name().toLowerCase(Locale.ROOT));
        look.addProperty("scale", mob.look().scale());
        json.add("look", look);

        JsonObject stats = new JsonObject();
        mob.stats().forEach((stat, value) -> stats.addProperty(stat.value(), value));
        json.add("stats", stats);

        JsonObject equipment = new JsonObject();
        mob.equipment().forEach((slot, id) -> equipment.addProperty(slot.name().toLowerCase(Locale.ROOT), id.value()));
        json.add("equipment", equipment);

        JsonArray behaviors = new JsonArray();
        for (Behavior behavior : mob.behaviors()) {
            behaviors.add(behavior.name());
        }
        json.add("behaviors", behaviors);
        return json;
    }
}
