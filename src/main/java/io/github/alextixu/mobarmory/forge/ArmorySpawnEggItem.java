package io.github.alextixu.mobarmory.forge;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Spawn egg of one custom mob. Its name is built from the mob's name ("Brute Spawn Egg", O10), so a new mob needs
 * only its own name in the language file, not a second line for the egg.
 */
final class ArmorySpawnEggItem extends SpawnEggItem {
    static final String NAME_KEY = "item." + MobArmoryMod.MODID + ".spawn_egg";

    private final EntityType<?> type;

    ArmorySpawnEggItem(Properties properties, EntityType<?> type) {
        super(properties.spawnEgg(type));
        this.type = type;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(NAME_KEY, type.getDescription());
    }
}
