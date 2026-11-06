package io.github.alextixu.mobarmory.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.alextixu.mobarmory.core.mob.Behavior;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import io.github.alextixu.mobarmory.data.MobFileJson;
import java.util.Locale;

/**
 * The mob list the web page shows (R6). Each mob is written as its definition says:
 * <pre>{@code
 * {"id": "brute", "look": {"body": "zombie", "scale": 1.2},
 *  "stats": {"max_health": 40, "attack_damage": 4, "movement_speed": 0.25},
 *  "equipment": {"mainhand": "heavy_sword"}, "behaviors": ["wander", "attack_players"],
 *  "name": "Brute", "status": "active", "mob": { ...the mob file... }}
 * }</pre>
 * {@code stats} lists only the stats the definition sets; every other stat starts at its default. Slots are written
 * like the {@code slot} field of a weapon file. R4b kept every field of the read-only list and added the last three:
 * {@code status} is {@code active}, {@code pending_restart} or {@code file_missing} (like weapons), and {@code mob} is the
 * definition in the file format ({@link MobFileJson}), which is also what the page sends back to save.
 */
public final class MobJson {
    private MobJson() {
    }

    /** {@code {"mobs": [...]}}: the mobs in effect in startup order, then the ones waiting for a restart. */
    public static JsonObject list(MobLibrary.View view) {
        JsonArray mobs = new JsonArray();
        view.catalog().all().forEach(mob -> mobs.add(entry(mob, view)));
        view.pending().values().forEach(mob -> mobs.add(entry(mob, view)));
        JsonObject json = new JsonObject();
        json.add("mobs", mobs);
        return json;
    }

    /** One mob with its status and file content. */
    public static JsonObject entry(MobDefinition mob, MobLibrary.View view) {
        JsonObject json = toJson(mob);
        json.addProperty("name", mob.name());
        boolean registered = view.catalog().find(mob.id()).isPresent();
        String status = !registered ? "pending_restart" : view.fileMissing().contains(mob.id()) ? "file_missing" : "active";
        json.addProperty("status", status);
        json.add("mob", MobFileJson.toJson(mob));
        return json;
    }

    /** The fields of the read-only list (R6), unchanged. */
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
