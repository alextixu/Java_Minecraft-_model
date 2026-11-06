package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Spawn egg of one custom mob. Its name is built from the mob's name ("Brute Spawn Egg", O10), which comes from the mob
 * file (R4b O13), so a new mob needs no language file. It looks like the vanilla egg of the same body (O14, made by
 * the runtime resource pack).
 */
final class ArmorySpawnEggItem extends SpawnEggItem {
    static final String NAME_KEY = "item." + MobArmoryMod.MODID + ".spawn_egg";

    private final MobId mob;
    private final MobLibrary library;

    ArmorySpawnEggItem(Properties properties, EntityType<?> type, MobId mob, MobLibrary library) {
        super(properties.spawnEgg(type));
        this.mob = mob;
        this.library = library;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(NAME_KEY, MobNames.of(mob, library));
    }
}
