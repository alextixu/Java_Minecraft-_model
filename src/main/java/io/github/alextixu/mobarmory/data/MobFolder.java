package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.mob.MobId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The mob files in the game folder (R4b, decision Q4 A):
 * <pre>
 * config/mobarmory/
 *   mobs/brute.json ...   one file per kind of mob, the file name is the id
 * </pre>
 * Reading and writing go through the same helpers as the weapon and stat folders ({@link ConfigFiles}).
 */
public final class MobFolder implements MobStore {
    private final Path mobs;

    /** @param root usually {@code config/mobarmory} */
    public MobFolder(Path root) {
        this.mobs = root.resolve("mobs");
    }

    public Path mobsFolder() {
        return mobs;
    }

    @Override
    public boolean exists() {
        return Files.isDirectory(mobs);
    }

    @Override
    public List<MobFile> mobFiles() throws IOException {
        List<MobFile> files = new ArrayList<>();
        for (Path file : ConfigFiles.list(mobs, ".json")) {
            files.add(new MobFile(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8)));
        }
        return files;
    }

    @Override
    public void writeMob(MobId id, String json) throws IOException {
        ConfigFiles.writeAtomically(mobs, id.value() + ".json", json.getBytes(StandardCharsets.UTF_8));
    }
}
