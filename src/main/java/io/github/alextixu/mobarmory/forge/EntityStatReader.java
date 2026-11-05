package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.StatReader;
import io.github.alextixu.mobarmory.core.stat.StatId;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/** Lets the Minecraft-free core read a living entity's stats. */
public final class EntityStatReader implements StatReader {
    private final LivingEntity entity;
    private final StatAttributeBridge bridge;

    public EntityStatReader(LivingEntity entity, StatAttributeBridge bridge) {
        this.entity = entity;
        this.bridge = bridge;
    }

    /** Current value after every modifier; the catalog default if this kind of entity lacks the attribute. */
    @Override
    public double get(StatId id) {
        AttributeInstance instance = entity.getAttribute(bridge.holder(id));
        return instance != null ? instance.getValue() : bridge.catalog().get(id).defaultValue();
    }

    /** The entity's own base value, before equipment and effects. */
    public double base(StatId id) {
        AttributeInstance instance = entity.getAttribute(bridge.holder(id));
        return instance != null ? instance.getBaseValue() : bridge.catalog().get(id).defaultValue();
    }
}
