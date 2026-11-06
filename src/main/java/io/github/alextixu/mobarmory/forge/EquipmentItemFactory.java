package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
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
 * Builds and registers one item per equipment definition (Factory). Adding equipment never needs a new class.
 */
public final class EquipmentItemFactory {
    private static final List<String> ARMOR_PIECES = List.of("_helmet", "_chestplate", "_leggings", "_boots");

    private final DeferredRegister<Item> items;
    private final Map<EquipmentId, RegistryObject<Item>> byId = new LinkedHashMap<>();

    public EquipmentItemFactory(EquipmentCatalog catalog, StatAttributeBridge stats, String modId) {
        this.items = DeferredRegister.create(ForgeRegistries.ITEMS, modId);
        for (EquipmentDefinition definition : catalog.all()) {
            String name = definition.id().value();
            byId.put(definition.id(), items.register(name,
                () -> new EquipmentItem(properties(definition, items.key(name)), definition.id(), catalog, stats)));
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

    private static Item.Properties properties(EquipmentDefinition definition, ResourceKey<Item> key) {
        Item.Properties properties = new Item.Properties().setId(key).stacksTo(1);
        if (definition.slot().isArmor()) {
            properties = properties.component(DataComponents.EQUIPPABLE,
                Equippable.builder(Slots.toMinecraft(definition.slot())).setAsset(armorAsset(definition)).build());
        }
        return properties;
    }

    /** "minecraft:iron_chestplate" is drawn on the body with Minecraft's "iron" armor (O8). */
    private static ResourceKey<EquipmentAsset> armorAsset(EquipmentDefinition definition) {
        Identifier look = Identifier.parse(((EquipmentLook.VanillaItem) definition.look()).item());
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
