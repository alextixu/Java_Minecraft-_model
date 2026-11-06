package io.github.alextixu.mobarmory.core.mob;

import java.util.List;

/**
 * What a reload of the mob files did (22-r4b-mob-admin-given-when-then.md, H9 and B11 to B15). Same shape as the
 * weapons' {@code ReloadReport}.
 *
 * @param changed        registered mobs whose definition changed; mobs in the world are updated (Q6 A)
 * @param pendingRestart new mobs that can be spawned after a restart (B14)
 * @param fileMissing    registered mobs whose file is gone; they keep their last definition until a restart (B12)
 * @param bodyWaits      mobs whose body changed in the file; the new body shows after a restart (B11, O9)
 */
public record MobReloadReport(List<MobId> changed, List<MobId> pendingRestart, List<MobId> fileMissing,
                              List<MobId> bodyWaits) {
    public MobReloadReport {
        changed = List.copyOf(changed);
        pendingRestart = List.copyOf(pendingRestart);
        fileMissing = List.copyOf(fileMissing);
        bodyWaits = List.copyOf(bodyWaits);
    }
}
