package io.github.alextixu.mobarmory.core.equipment;

/** Where a piece of equipment is held or worn. A piece only counts in the slot its definition names (O1). */
public enum EquipmentSlotKind {
    MAINHAND,
    OFFHAND,
    HEAD,
    CHEST,
    LEGS,
    FEET;

    public boolean isArmor() {
        return this == HEAD || this == CHEST || this == LEGS || this == FEET;
    }
}
