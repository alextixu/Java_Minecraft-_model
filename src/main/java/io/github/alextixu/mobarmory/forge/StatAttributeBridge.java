package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import io.github.alextixu.mobarmory.data.StatSetup;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Turns catalog stats into Minecraft attributes. Custom stats become new attributes, synced to clients unless their
 * stat file says otherwise; vanilla-backed stats point at Minecraft's own attribute. Since section E the catalog comes
 * from {@code config/mobarmory/stats/}, read before this is built.
 */
public final class StatAttributeBridge {
    private final StatCatalog catalog;
    private final DeferredRegister<Attribute> attributes;
    private final Map<StatId, RegistryObject<Attribute>> custom = new LinkedHashMap<>();
    private final Map<StatId, Holder<Attribute>> resolved = new LinkedHashMap<>();

    /** Every custom stat is synced to clients, as before section E. */
    public StatAttributeBridge(StatCatalog catalog, String modId) {
        this(catalog, modId, id -> true);
    }

    /**
     * @param synced whether clients receive a custom stat's value; a stat file can turn it off (section E, O2)
     */
    public StatAttributeBridge(StatCatalog catalog, String modId, Predicate<StatId> synced) {
        this.catalog = catalog;
        this.attributes = DeferredRegister.create(Registries.ATTRIBUTE, modId);
        for (StatDefinition definition : catalog.custom()) {
            boolean sync = synced.test(definition.id());
            custom.put(definition.id(), attributes.register(definition.id().value(), () -> new RangedAttribute(
                descriptionId(modId, definition), definition.defaultValue(), definition.min(), definition.max())
                .setSyncable(sync)));
        }
    }

    public void register(BusGroup modBusGroup) {
        attributes.register(modBusGroup);
    }

    public StatCatalog catalog() {
        return catalog;
    }

    /**
     * Translation key of a custom stat, e.g. {@code attribute.name.mobarmory.dodge_chance}. The same key the runtime
     * resource pack uses for a name from a stat file ({@link StatSetup#translationKey}).
     */
    public static String descriptionId(String modId, StatDefinition definition) {
        return StatSetup.translationKey(modId, definition.id());
    }

    /**
     * The attribute behind a stat. Only valid once registries are filled (after mod construction).
     *
     * @throws IllegalStateException if a vanilla-backed stat names an attribute Minecraft does not have
     */
    public synchronized Holder<Attribute> holder(StatId id) {
        Holder<Attribute> holder = resolved.get(id);
        if (holder == null) {
            holder = resolve(catalog.get(id));
            resolved.put(id, holder);
        }
        return holder;
    }

    /** Resolves every stat once, so a typo in a vanilla attribute name stops the game at startup (O2). */
    public void verifyAll() {
        catalog.all().forEach(definition -> holder(definition.id()));
    }

    private Holder<Attribute> resolve(StatDefinition definition) {
        if (definition.isCustom()) {
            return custom.get(definition.id()).getHolder().orElseThrow(() -> new IllegalStateException(
                "Stat '" + definition.id() + "' is not registered as an attribute yet"));
        }
        String key = definition.vanillaAttribute().orElseThrow();
        ResourceKey<Attribute> resourceKey = ResourceKey.create(Registries.ATTRIBUTE, Identifier.parse(key));
        return BuiltInRegistries.ATTRIBUTE.get(resourceKey).<Holder<Attribute>>map(reference -> reference)
            .orElseThrow(() -> new IllegalStateException(
                "Stat '" + definition.id() + "' maps to attribute " + key + ", which Minecraft does not have"));
    }
}
