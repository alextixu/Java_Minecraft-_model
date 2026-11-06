package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.equipment.LoadoutCalculator;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;

/**
 * Applies equipment stats when an entity's equipment changes (server side).
 * <p>
 * Each stat gets at most one modifier with the fixed id {@code mobarmory:equipment}, worth
 * {@code target - base}. Because the id is fixed and the target is recomputed from scratch, running this any
 * number of times (respawn, relog, every slot change) can never stack a piece twice (B11, B12).
 */
public final class EquipmentStatApplier {
    public static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(MobArmoryMod.MODID, "equipment");

    private final LoadoutCalculator calculator;
    private final StatAttributeBridge stats;

    /** Reads the definitions in effect at each recomputation, so a changed definition applies on the next one. */
    public EquipmentStatApplier(EquipmentLibrary library, StatAttributeBridge stats) {
        this.calculator = new LoadoutCalculator(library::current);
        this.stats = stats;
    }

    public void register() {
        LivingEquipmentChangeEvent.BUS.addListener(event -> refresh(event.getEntity()));
    }

    /** Recomputes every stat from what the entity holds and wears right now. */
    public void refresh(LivingEntity entity) {
        EntityStatReader reader = new EntityStatReader(entity, stats);
        Map<StatId, Double> targets = calculator.compute(loadoutOf(entity), reader::base);
        for (StatDefinition stat : stats.catalog().all()) {
            AttributeInstance instance = entity.getAttribute(stats.holder(stat.id()));
            if (instance == null) {
                continue;
            }
            Double target = targets.get(stat.id());
            if (target == null) {
                instance.removeModifier(MODIFIER_ID);
            } else {
                double delta = target - instance.getBaseValue();
                instance.addOrUpdateTransientModifier(
                    new AttributeModifier(MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    /** Which of our pieces sits in each slot. Empty slots and vanilla items are left out (B13). */
    static Map<EquipmentSlotKind, EquipmentId> loadoutOf(LivingEntity entity) {
        Map<EquipmentSlotKind, EquipmentId> loadout = new EnumMap<>(EquipmentSlotKind.class);
        for (EquipmentSlotKind kind : EquipmentSlotKind.values()) {
            ItemStack stack = entity.getItemBySlot(Slots.toMinecraft(kind));
            if (stack.getItem() instanceof EquipmentItem item) {
                loadout.put(kind, item.equipmentId());
            }
        }
        return loadout;
    }
}
