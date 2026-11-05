package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import net.minecraft.world.entity.EquipmentSlot;

/** Maps the core's slot names to Minecraft's. Body and saddle slots (horses, wolves) are not used. */
final class Slots {

    private Slots() {
    }

    static EquipmentSlot toMinecraft(EquipmentSlotKind kind) {
        return switch (kind) {
            case MAINHAND -> EquipmentSlot.MAINHAND;
            case OFFHAND -> EquipmentSlot.OFFHAND;
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
    }

    /** Translation key of Minecraft's own tooltip header, e.g. "When in Main Hand:". */
    static String tooltipHeaderKey(EquipmentSlotKind kind) {
        return "item.modifiers." + toMinecraft(kind).getName();
    }
}
