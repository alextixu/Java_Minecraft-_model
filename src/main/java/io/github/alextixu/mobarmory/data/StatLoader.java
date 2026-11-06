package io.github.alextixu.mobarmory.data;

import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatId;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads {@code config/mobarmory/stats/} into the stats the game runs with (section E, decisions in
 * 26-stat-files-open-questions.md).
 * <ul>
 *   <li>The built-in stats ({@code content/ModStats}: Java code refers to them) always exist. Their files may change
 *       the numbers, but not the kind: a new attribute stays one, a vanilla-backed stat keeps its attribute (Q4 A).
 *       A missing built-in file means the built-in value is used (Q5 A).</li>
 *   <li>Any other file adds a stat: a plain number of its own, or one of Minecraft's attributes (Q6 A).</li>
 *   <li>Every problem in every file is collected and reported together (Q9 A).</li>
 * </ul>
 */
public final class StatLoader {
    private final StatCatalog builtIns;

    /** @param builtIns the stats Java code relies on; also what the first start writes */
    public StatLoader(StatCatalog builtIns) {
        this.builtIns = builtIns;
    }

    /**
     * First start (O1): if the folder does not exist yet, writes one file per built-in stat. A folder that exists is
     * left alone, even if files were deleted from it.
     *
     * @return true if the files were written
     */
    public boolean installIfMissing(StatStore store) throws IOException {
        if (store.exists()) {
            return false;
        }
        for (StatDefinition builtIn : builtIns.all()) {
            store.writeStat(builtIn.id(), StatJson.write(StatEntry.of(builtIn)));
        }
        return true;
    }

    /**
     * Reads every file.
     *
     * @throws StatLoadException listing every problem; nothing is used
     */
    public StatSetup load(StatStore store) throws IOException, StatLoadException {
        List<String> problems = new ArrayList<>();
        Map<StatId, StatEntry> fromFiles = new LinkedHashMap<>();
        for (StatStore.StatFile file : store.statFiles()) {
            String fileName = file.fileName();
            StatId id;
            try {
                id = StatId.of(fileName.substring(0, fileName.length() - ".json".length()));
            } catch (IllegalArgumentException e) {
                problems.add(fileName + ": the file name must be a stat id (lower-case letters, digits and _, starting with a letter)");
                continue;
            }
            try {
                StatEntry entry = StatJson.read(id, file.text());
                Optional<String> kind = builtIns.find(id).flatMap(builtIn -> kindProblem(builtIn, entry.definition()));
                if (kind.isPresent()) {
                    problems.add(fileName + ": " + kind.get());
                } else {
                    fromFiles.put(id, entry);
                }
            } catch (IllegalArgumentException e) {
                problems.add(fileName + ": " + e.getMessage());
            }
        }

        List<StatEntry> entries = new ArrayList<>();
        List<StatId> builtInsFromCode = new ArrayList<>();
        for (StatDefinition builtIn : builtIns.all()) {
            StatEntry entry = fromFiles.remove(builtIn.id());
            if (entry == null) { // no file, or a file with a problem (then loading fails below anyway)
                builtInsFromCode.add(builtIn.id());
                entry = StatEntry.of(builtIn);
            }
            entries.add(entry);
        }
        entries.addAll(fromFiles.values());

        StatCatalog.Builder catalog = StatCatalog.builder();
        Map<StatId, String> names = new LinkedHashMap<>();
        Set<StatId> unsynced = new HashSet<>();
        for (StatEntry entry : entries) {
            try {
                catalog.add(entry.definition());
            } catch (IllegalArgumentException e) {
                problems.add(entry.definition().id() + ".json: " + e.getMessage());
            }
            entry.name().ifPresent(name -> names.put(entry.definition().id(), name));
            if (!entry.sync()) {
                unsynced.add(entry.definition().id());
            }
        }
        if (!problems.isEmpty()) {
            throw new StatLoadException(problems);
        }
        return new StatSetup(catalog.build(), names, unsynced, builtInsFromCode);
    }

    /** Q4 A: a built-in stat's file may change its numbers, never what kind of stat it is. */
    private static Optional<String> kindProblem(StatDefinition builtIn, StatDefinition fromFile) {
        if (builtIn.vanillaAttribute().equals(fromFile.vanillaAttribute())) {
            return Optional.empty();
        }
        String expected = builtIn.vanillaAttribute()
            .map(attribute -> "\"attribute\": \"" + attribute + "\"")
            .orElse("no \"attribute\" (it is a stat of its own)");
        return Optional.of("attribute: '" + builtIn.id() + "' is a built-in stat that Java code relies on, so it must keep "
            + expected);
    }
}
