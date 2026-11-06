package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.mob.MobId;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** {@link MobStore} in memory, for tests; passes the same contract as {@link MobFolder}. */
public final class InMemoryMobStore implements MobStore {
    private final Map<String, String> files = new TreeMap<>();
    private boolean exists;
    /** When set, every write fails, like a read-only folder (B18). */
    public boolean failWrites;

    @Override
    public boolean exists() {
        return exists;
    }

    @Override
    public List<MobFile> mobFiles() {
        List<MobFile> list = new ArrayList<>();
        files.forEach((name, text) -> list.add(new MobFile(name, text)));
        return list;
    }

    @Override
    public void writeMob(MobId id, String json) throws IOException {
        if (failWrites) {
            throw new IOException("read-only folder");
        }
        exists = true;
        files.put(id.value() + ".json", json);
    }

    /** A file as a player would put it there, with any name. */
    public void put(String fileName, String text) {
        exists = true;
        files.put(fileName, text);
    }

    public void delete(String fileName) {
        files.remove(fileName);
    }

    public String text(String fileName) {
        return files.get(fileName);
    }
}
