package io.github.alextixu.mobarmory.core.equipment;

import java.util.List;

/**
 * What a reload did (09-weapon-data-web-given-when-then.md, H3 and B6 to B9).
 *
 * @param changed        registered pieces whose definition changed; holders must be recomputed
 * @param pendingRestart new pieces that become items after a restart (B7)
 * @param fileMissing    registered pieces whose file is gone; they keep their last definition until a restart (B8)
 * @param slotWaits      pieces whose slot changed in the file; the new slot applies after a restart (B9)
 */
public record ReloadReport(List<EquipmentId> changed, List<EquipmentId> pendingRestart, List<EquipmentId> fileMissing,
                           List<EquipmentId> slotWaits) {
    public ReloadReport {
        changed = List.copyOf(changed);
        pendingRestart = List.copyOf(pendingRestart);
        fileMissing = List.copyOf(fileMissing);
        slotWaits = List.copyOf(slotWaits);
    }
}
