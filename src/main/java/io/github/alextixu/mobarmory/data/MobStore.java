package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.mob.MobId;
import java.io.IOException;
import java.util.List;

/**
 * Where mob files live (R4b). The game uses {@link MobFolder} ({@code config/mobarmory/mobs/}); tests use an in-memory
 * store. Both pass the same contract tests. Smaller than {@link WeaponStore}: mobs have no textures (I).
 */
public interface MobStore {

    /** One mob file. */
    record MobFile(String fileName, String text) {
    }

    /** True once the mobs folder exists. On the very first start it does not, and the defaults are installed. */
    boolean exists() throws IOException;

    /** Every {@code *.json} mob file, sorted by file name. */
    List<MobFile> mobFiles() throws IOException;

    /** Writes one mob file so that it holds either the old or the new content, never half of it. Creates the folder. */
    void writeMob(MobId id, String json) throws IOException;
}
