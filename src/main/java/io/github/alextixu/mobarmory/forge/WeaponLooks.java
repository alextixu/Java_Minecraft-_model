package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * How a weapon looks on screen (decisions Q3 B, Q5 B, O11).
 * <p>
 * Minecraft draws an item with the model named by its {@code item_model} component. An item's default is fixed when it
 * is registered, but each stack can carry its own, and Minecraft sends that to every client. So every stack of our
 * weapons carries the model of its current look: a vanilla item's model, or {@code mobarmory:look/<texture>} for a
 * texture in the jar. When the look changes, the server updates the stacks and every player sees the new look; no
 * resource reload is needed. The shared definition stays the only source; the stack only mirrors it.
 */
public final class WeaponLooks {
    private WeaponLooks() {
    }

    /** The item model for a look. */
    public static Identifier modelId(EquipmentLook look) {
        return switch (look) {
            case EquipmentLook.VanillaItem item -> Identifier.parse(item.item());
            case EquipmentLook.Texture texture -> Identifier.fromNamespaceAndPath(MobArmoryMod.MODID, "look/" + texture.texture());
        };
    }

    /**
     * Makes the stack show its weapon's current look.
     *
     * @return true if the stack changed
     */
    public static boolean apply(ItemStack stack, EquipmentLibrary library) {
        if (!(stack.getItem() instanceof EquipmentItem item)) {
            return false;
        }
        Optional<EquipmentDefinition> definition = library.current().find(item.equipmentId());
        if (definition.isEmpty()) {
            return false;
        }
        Identifier wanted = modelId(definition.get().look());
        if (wanted.equals(stack.get(DataComponents.ITEM_MODEL))) {
            return false;
        }
        stack.set(DataComponents.ITEM_MODEL, wanted);
        return true;
    }

    /** A new stack that already shows the current look (creative tab, tab icon). */
    public static ItemStack stackOf(Item item, EquipmentLibrary library) {
        ItemStack stack = new ItemStack(item);
        apply(stack, library);
        return stack;
    }

    /**
     * After a look changed: updates what every loaded entity holds or wears. Items elsewhere in a player's inventory
     * follow on the next tick ({@link EquipmentItem#inventoryTick}).
     */
    public static void applyToEntities(MinecraftServer server, EquipmentLibrary library) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living) {
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        apply(living.getItemBySlot(slot), library);
                    }
                }
            }
        }
    }
}
