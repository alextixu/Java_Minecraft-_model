package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Builds and registers one item per piece found at startup (Factory). Adding equipment never needs a new class,
 * only a new weapon file and a restart (the item registry is frozen after startup).
 */
public final class EquipmentItemFactory {
    private static final List<String> ARMOR_PIECES = List.of("_helmet", "_chestplate", "_leggings", "_boots");

    private final DeferredRegister<Item> items;
    private final Map<EquipmentId, RegistryObject<Item>> byId = new LinkedHashMap<>();

    public EquipmentItemFactory(EquipmentLibrary library, StatAttributeBridge stats, String modId) {
        this.items = DeferredRegister.create(ForgeRegistries.ITEMS, modId);
        for (EquipmentId id : library.registered()) {
            EquipmentDefinition definition = library.current().get(id);
            String name = id.value();
            byId.put(id, items.register(name, () -> new EquipmentItem(properties(definition, items.key(name)), id, library, stats)));
        }
    }

    public void register(BusGroup modBusGroup) {
        items.register(modBusGroup);
    }

    public List<RegistryObject<Item>> items() {
        return List.copyOf(byId.values());
    }

    public Item item(EquipmentId id) {
        RegistryObject<Item> item = byId.get(id);
        if (item == null) {
            throw new NoSuchElementException("No item for equipment '" + id + "'");
        }
        return item.get();
    }

    /**
     * Whether something is drawn on the body is fixed when the item is registered (the {@code equippable}
     * component), so it follows the look the piece had at startup (decision O12).
     */
    private static Item.Properties properties(EquipmentDefinition definition, ResourceKey<Item> key) {
        Item.Properties properties = new Item.Properties().setId(key).stacksTo(1);
        if (definition.slot().isArmor()) {
            Equippable.Builder equippable = Equippable.builder(Slots.toMinecraft(definition.slot()));
            if (definition.look() instanceof EquipmentLook.VanillaItem item) {
                equippable.setAsset(armorAsset(item));
            }
            properties = properties.component(DataComponents.EQUIPPABLE, equippable.build());
        }
        return properties;
    }

    /** "minecraft:iron_chestplate" is drawn on the body with Minecraft's "iron" armor (O8). */
    private static ResourceKey<EquipmentAsset> armorAsset(EquipmentLook.VanillaItem item) {
        Identifier look = Identifier.parse(item.item());
        String material = look.getPath();
        for (String piece : ARMOR_PIECES) {
            if (material.endsWith(piece)) {
                material = material.substring(0, material.length() - piece.length());
                break;
            }
        }
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(look.getNamespace(), material));
    }
}
