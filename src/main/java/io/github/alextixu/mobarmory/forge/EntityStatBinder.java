package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;

/**
 * Gives every kind of living entity every custom stat, at its default value (decision O5). New stats and,
 * later, new mobs (R4) are picked up automatically because this walks the catalog and every entity type.
 */
public final class EntityStatBinder {
    private final StatAttributeBridge bridge;

    public EntityStatBinder(StatAttributeBridge bridge) {
        this.bridge = bridge;
    }

    public void register() {
        EntityAttributeModificationEvent.BUS.addListener(this::onModifyAttributes);
    }

    private void onModifyAttributes(EntityAttributeModificationEvent event) {
        for (StatDefinition definition : bridge.catalog().custom()) {
            Holder<Attribute> attribute = bridge.holder(definition.id());
            for (EntityType<? extends LivingEntity> type : event.getTypes()) {
                if (!event.has(type, attribute)) {
                    event.add(type, attribute, definition.defaultValue());
                }
            }
        }
    }
}
