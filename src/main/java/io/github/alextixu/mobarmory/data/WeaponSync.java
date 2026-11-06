package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the server tells each client about weapons (decision Q3 B, review-01 risk 4): the definitions in effect, in the
 * same JSON as the files. A remote client puts them into its own library, so names and tooltips match the server.
 * Numbers in play already match, because Minecraft syncs the attributes itself.
 */
public final class WeaponSync {
    private WeaponSync() {
    }

    /** Every weapon in effect, as id → weapon JSON. */
    public static Map<String, String> snapshot(EquipmentLibrary library) {
        Map<String, String> weapons = new LinkedHashMap<>();
        for (EquipmentDefinition definition : library.current().all()) {
            weapons.put(definition.id().value(), WeaponJson.write(definition));
        }
        return weapons;
    }

    /**
     * On a remote client: replaces its definitions with the server's. Weapons this client does not have as items
     * are ignored (they could not have joined otherwise); all or nothing, like a reload.
     *
     * @throws WeaponFormatException if the server sent something this version cannot read; nothing changes
     */
    public static ReloadReport apply(EquipmentLibrary library, Map<String, String> sent) throws WeaponFormatException {
        List<EquipmentDefinition> definitions = new ArrayList<>();
        for (Map.Entry<String, String> entry : sent.entrySet()) {
            EquipmentId id;
            try {
                id = EquipmentId.of(entry.getKey());
            } catch (IllegalArgumentException e) {
                throw new WeaponFormatException("id", e.getMessage());
            }
            definitions.add(WeaponJson.read(id, entry.getValue(), library.stats()));
        }
        try {
            return library.replaceAll(definitions);
        } catch (IllegalArgumentException e) {
            throw new WeaponFormatException("", e.getMessage());
        }
    }
}
