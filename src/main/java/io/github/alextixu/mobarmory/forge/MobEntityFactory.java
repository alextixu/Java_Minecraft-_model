package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Builds everything a mob kind needs from its definition (Factory, Q2 A): an entity type
 * ({@code /summon mobarmory:<id>}), its base attributes and a spawn egg. Adding a mob never needs a new class, and
 * since R4b no Java at all: every mob in {@code config/mobarmory/mobs/} at startup gets one (T2). Entity types can only
 * be registered at startup, so a new kind of mob needs a restart (platform limit).
 */
public final class MobEntityFactory {
    private final MobLibrary library;
    private final StatAttributeBridge stats;
    private final DeferredRegister<EntityType<?>> types;
    private final DeferredRegister<Item> eggs;
    private final Map<MobId, RegistryObject<EntityType<ArmoryMob>>> typeById = new LinkedHashMap<>();
    private final Map<MobId, RegistryObject<Item>> eggById = new LinkedHashMap<>();

    /**
     * @param library      the mobs loaded at startup; exactly these become entity types
     * @param outfit       what a mob holds and wears when it spawns ({@link MobEquipmentSource#stacksFor} in the game)
     * @param statsChanged recomputes an entity's equipment after its base stats changed (R2's applier in the game)
     * @throws IllegalStateException if a mob uses a behavior without a binder (B4)
     */
    public MobEntityFactory(MobLibrary library, StatAttributeBridge stats, BehaviorGoals behaviors,
                            Function<MobDefinition, Map<EquipmentSlotKind, ItemStack>> outfit,
                            Consumer<LivingEntity> statsChanged, String modId) {
        MobCatalog catalog = library.current();
        behaviors.verify(catalog);
        this.library = library;
        this.stats = stats;
        ArmoryMob.Parts parts = new ArmoryMob.Parts(library, stats, behaviors, outfit, statsChanged);
        this.types = DeferredRegister.create(Registries.ENTITY_TYPE, modId);
        this.eggs = DeferredRegister.create(ForgeRegistries.ITEMS, modId);
        for (MobDefinition definition : catalog.all()) {
            String name = definition.id().value();
            RegistryObject<EntityType<ArmoryMob>> type = types.register(name, () -> EntityType.Builder
                .<ArmoryMob>of((entityType, level) -> new ArmoryMob(entityType, level, definition.id(), parts),
                    MobCategory.MONSTER)
                .sized(0.6F, 1.95F)          // a vanilla zombie's size; the look's scale attribute resizes it
                .eyeHeight(1.74F)
                .clientTrackingRange(8)
                .notInPeaceful()             // O6, B11
                .noLootTable()               // O7, B12: experience only
                .build(types.key(name)));
            typeById.put(definition.id(), type);
            String egg = name + "_spawn_egg";
            // Entity types are registered before items, so the type exists when the egg is made.
            eggById.put(definition.id(), eggs.register(egg,
                () -> new ArmorySpawnEggItem(new Item.Properties().setId(eggs.key(egg)), type.get(), definition.id(), library)));
        }
    }

    public void register(BusGroup modBusGroup) {
        types.register(modBusGroup);
        eggs.register(modBusGroup);
        EntityAttributeCreationEvent.BUS.addListener(this::onCreateAttributes);
    }

    /** The mobs in effect now; the same kinds as at startup, with their current definitions. */
    public MobCatalog catalog() {
        return library.current();
    }

    public MobLibrary library() {
        return library;
    }

    public EntityType<ArmoryMob> type(MobId id) {
        return lookUp(typeById, id).get();
    }

    public Item spawnEgg(MobId id) {
        return lookUp(eggById, id).get();
    }

    public List<RegistryObject<Item>> spawnEggs() {
        return List.copyOf(eggById.values());
    }

    private void onCreateAttributes(EntityAttributeCreationEvent event) {
        for (MobDefinition definition : library.current().all()) {
            event.put(type(definition.id()), attributes(definition));
        }
    }

    /**
     * A monster's attributes with every stat at the definition's value or the catalog default (O4, B7), so our
     * custom stats are already there and R1's binder leaves them alone. Equipment comes on top through R2. These are
     * the startup values; each mob takes the values in effect when it spawns or loads ({@link ArmoryMob#applyDefinitionStats}).
     */
    private AttributeSupplier attributes(MobDefinition definition) {
        AttributeSupplier.Builder builder = Monster.createMonsterAttributes();
        definition.spawnStats(stats.catalog()).forEach((id, value) -> builder.add(stats.holder(id), value));
        builder.add(Attributes.FOLLOW_RANGE, ArmoryMob.FOLLOW_RANGE);
        builder.add(Attributes.SCALE, definition.look().scale());
        return builder.build();
    }

    private static <T> T lookUp(Map<MobId, T> map, MobId id) {
        T value = map.get(id);
        if (value == null) {
            throw new NoSuchElementException("No mob '" + id + "'");
        }
        return value;
    }
}
