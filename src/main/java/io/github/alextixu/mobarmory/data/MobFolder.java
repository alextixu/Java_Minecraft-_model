package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.mob.MobId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The mob files in the game folder (R4b, decision Q4 A):
 * <pre>
 * config/mobarmory/
 *   mobs/brute.json ...   one file per kind of mob, the file name is the id
 * </pre>
 * The folder is a {@link JsonFolder}, listed and written the same way as the weapon, stat and combo files.
 */
public final class MobFolder implements MobStore {
    private final JsonFolder mobs;

    /** @param root usually {@code config/mobarmory} */
    public MobFolder(Path root) {
        this.mobs = new JsonFolder(root.resolve("mobs"));
    }

    public Path mobsFolder() {
        return mobs.path();
    }

    @Override
    public boolean exists() {
        return mobs.exists();
    }

    /** Empty when the folder does not exist yet. */
    @Override
    public List<MobFile> mobFiles() throws IOException {
        List<MobFile> files = new ArrayList<>();
        for (JsonFolder.DataFile file : mobs.files()) {
            files.add(new MobFile(file.fileName(), file.text()));
        }
        return files;
    }

    @Override
    public void writeMob(MobId id, String json) throws IOException {
        mobs.write(id.value(), json);
    }
}
