package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.stat.ModifierOperation;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatModifier;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The item for one kind of equipment. It carries only the equipment id; name, stats and tooltip are read from
 * the definition in effect every time, so changing the definition (reload command, web page) changes every copy
 * at once (R2 decision Q6 A).
 */
public final class EquipmentItem extends Item {
    private final EquipmentId equipmentId;
    private final EquipmentLibrary library;
    private final StatAttributeBridge stats;

    public EquipmentItem(Item.Properties properties, EquipmentId equipmentId, EquipmentLibrary library, StatAttributeBridge stats) {
        super(properties);
        this.equipmentId = equipmentId;
        this.library = library;
        this.stats = stats;
    }

    public EquipmentId equipmentId() {
        return equipmentId;
    }

    /** The name from the weapon file, in whatever language it was written. */
    @Override
    public Component getName(ItemStack stack) {
        return library.current().find(equipmentId)
            .<Component>map(definition -> Component.literal(definition.name()))
            .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        library.current().find(equipmentId).ifPresent(definition -> appendModifiers(definition, builder));
    }

    /** Same layout as Minecraft's own attribute lines: a gray slot header, then blue bonuses and red penalties. */
    private void appendModifiers(EquipmentDefinition definition, Consumer<Component> builder) {
        builder.accept(Component.empty());
        builder.accept(Component.translatable(Slots.tooltipHeaderKey(definition.slot())).withStyle(ChatFormatting.GRAY));
        for (StatModifier modifier : definition.modifiers()) {
            builder.accept(line(modifier));
        }
    }

    private Component line(StatModifier modifier) {
        StatDefinition stat = library.stats().get(modifier.stat());
        boolean asPercent = modifier.operation() == ModifierOperation.MULTIPLY || isChance(stat);
        double shown = asPercent ? modifier.amount() * 100 : modifier.amount();
        String key = "attribute.modifier." + (shown >= 0 ? "plus." : "take.") + (asPercent ? 1 : 0);
        Component name = Component.translatable(stats.holder(stat.id()).value().getDescriptionId());
        return Component.translatable(key, ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(Math.abs(shown)), name)
            .withStyle(shown >= 0 ? ChatFormatting.BLUE : ChatFormatting.RED);
    }

    /** Custom stats capped at 1 are probabilities, so "+0.25" reads better as "+25%". */
    private static boolean isChance(StatDefinition stat) {
        return stat.isCustom() && stat.max() <= 1.0;
    }
}
