package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobEquipmentCheck;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * What a mob holds and wears when it spawns, from the weapons in effect right now (R4 decision O2, changed to B on
 * 11/07). The one place mobs look up equipment.
 * <p>
 * A piece can be given out if it is an item (its file was there at startup), its file is still there, and it works in
 * the slot the mob puts it in. Otherwise the mob spawns without it and a warning is logged: at startup for every mob,
 * and after that once per new problem, when a mob spawns (so a weapon deleted before {@code /mobarmory reload} is
 * reported the next time that mob spawns). A slot changed in a file waits for a restart, like the item itself (R2b B9).
 */
public final class MobEquipmentSource {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final EquipmentLibrary library;
    private final Function<EquipmentId, Item> items;
    private final Set<MobEquipmentCheck.Problem> reported = ConcurrentHashMap.newKeySet();

    /**
     * @param items the item of a registered piece
     */
    public MobEquipmentSource(EquipmentLibrary library, Function<EquipmentId, Item> items) {
        this.library = library;
        this.items = items;
    }

    /** Which listed pieces the mob would get if it spawned now, and which it would not. */
    public MobEquipmentCheck.Result check(MobDefinition mob) {
        EquipmentLibrary.View now = library.view();
        return MobEquipmentCheck.check(mob, id -> available(now, id));
    }

    /** The stacks a mob spawns with, showing each weapon's current look; logs what is left out. */
    public Map<EquipmentSlotKind, ItemStack> stacksFor(MobDefinition mob) {
        MobEquipmentCheck.Result result = check(mob);
        report(mob, result);
        Map<EquipmentSlotKind, ItemStack> stacks = new EnumMap<>(EquipmentSlotKind.class);
        result.given().forEach((slot, id) -> stacks.put(slot, WeaponLooks.stackOf(items.apply(id), library)));
        return stacks;
    }

    /** Startup: warns about every mob that will spawn without part of its equipment. */
    public void warnAll(Collection<MobDefinition> mobs) {
        mobs.forEach(mob -> report(mob, check(mob)));
    }

    private static Optional<EquipmentDefinition> available(EquipmentLibrary.View now, EquipmentId id) {
        return now.fileMissing().contains(id) ? Optional.empty() : now.catalog().find(id);
    }

    /** Logs each problem once; a problem that went away is forgotten, so it is logged again if it comes back. */
    private void report(MobDefinition mob, MobEquipmentCheck.Result result) {
        reported.removeIf(problem -> problem.mob().equals(mob.id()) && !result.problems().contains(problem));
        for (MobEquipmentCheck.Problem problem : result.problems()) {
            if (reported.add(problem)) {
                LOGGER.warn(problem.message());
            }
        }
    }
}
