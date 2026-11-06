package io.github.alextixu.mobarmory.core.mob;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Which pieces of its listed equipment a mob really gets (R4 decision O2, changed by the user on 11/07 to B).
 * <p>
 * Weapons are files that people edit and delete (R2b), so a mob that names a piece that is gone, or that now works in
 * another slot, is not a reason to stop the game: that piece is left out and reported, and the rest of the mob is
 * unchanged. Stats and behaviors are code and still fail at startup ({@link MobCatalog}).
 */
public final class MobEquipmentCheck {
    private MobEquipmentCheck() {
    }

    /**
     * One listed piece that is left out.
     *
     * @param worksIn the slot the piece works in now, or {@code null} if no such piece exists right now
     */
    public record Problem(MobId mob, EquipmentSlotKind slot, EquipmentId equipment, EquipmentSlotKind worksIn) {
        public Problem {
            Objects.requireNonNull(mob, "mob");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(equipment, "equipment");
        }

        public String message() {
            String why = worksIn == null
                ? "there is no such weapon now (its file is missing or was deleted)"
                : "it now works in " + name(worksIn);
            return "Mob '" + mob + "' spawns without '" + equipment + "' in " + name(slot) + ": " + why;
        }

        private static String name(EquipmentSlotKind slot) {
            return slot.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param given    the pieces the mob gets, by slot
     * @param problems the pieces it lists but does not get, in slot order
     */
    public record Result(Map<EquipmentSlotKind, EquipmentId> given, List<Problem> problems) {
        public Result {
            given = given.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(given));
            problems = List.copyOf(problems);
        }
    }

    /**
     * @param available looks up a piece that can be given out right now; empty if there is none
     */
    public static Result check(MobDefinition mob, Function<EquipmentId, Optional<EquipmentDefinition>> available) {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(available, "available");
        Map<EquipmentSlotKind, EquipmentId> given = new EnumMap<>(EquipmentSlotKind.class);
        List<Problem> problems = new ArrayList<>();
        mob.equipment().forEach((slot, id) -> {
            Optional<EquipmentDefinition> piece = available.apply(id);
            if (piece.isEmpty()) {
                problems.add(new Problem(mob.id(), slot, id, null));
            } else if (piece.get().slot() != slot) {
                problems.add(new Problem(mob.id(), slot, id, piece.get().slot()));
            } else {
                given.put(slot, id);
            }
        });
        return new Result(given, problems);
    }
}
