package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.stat.StatId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** A {@link StatStore} in memory, for tests. Passes the same contract as {@link StatFolder}. */
public class InMemoryStatStore implements StatStore {
    private final Map<String, String> files = new TreeMap<>();
    private boolean exists;

    /** Puts a file in as-is, even one with a bad name or bad content (the loader must catch those). */
    public InMemoryStatStore putFile(String fileName, String text) {
        exists = true;
        files.put(fileName, text);
        return this;
    }

    public void deleteFile(String fileName) {
        files.remove(fileName);
    }

    public Optional<String> file(String fileName) {
        return Optional.ofNullable(files.get(fileName));
    }

    @Override
    public boolean exists() {
        return exists;
    }

    @Override
    public List<StatFile> statFiles() {
        List<StatFile> list = new ArrayList<>();
        files.forEach((name, text) -> {
            if (name.endsWith(".json")) {
                list.add(new StatFile(name, text));
            }
        });
        return list;
    }

    @Override
    public void writeStat(StatId id, String json) {
        putFile(id.value() + ".json", json);
    }
}
