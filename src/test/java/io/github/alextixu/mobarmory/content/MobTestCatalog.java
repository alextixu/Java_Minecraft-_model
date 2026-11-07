package io.github.alextixu.mobarmory.content;

import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.data.DefaultMobs;
import io.github.alextixu.mobarmory.data.DefaultWeapons;
import io.github.alextixu.mobarmory.data.GameTestConfig;
import io.github.alextixu.mobarmory.data.MobFileJson;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The mobs and weapons the mod ships (mobarmory/default_mobs/*.json, mobarmory/default_weapons/*.json), read the same
 * way the game reads them, plus the test-only goblin, a mob that exists only as a JSON file (R4b T2, O20). In the game,
 * mobs take their equipment from the weapon files in effect when they spawn; the shipped files are what a fresh install
 * has.
 * <p>
 * Each mob file comes with the stats of the place it is read (round 12): the shipped ones with the seven built-in stats,
 * like a fresh install; the goblin with the game test server's stats, which include {@code gametest_luck}.
 */
final class MobTestCatalog {
    static final MobCatalog MOBS = DefaultMobs.catalog(ModStats.CATALOG);
    static final MobDefinition BRUTE = MOBS.get(DefaultMobs.BRUTE);
    static final MobDefinition SKIRMISHER = MOBS.get(DefaultMobs.SKIRMISHER);
    /** {@code src/gametest/config/mobarmory/mobs/goblin.json}, copied into the game test server's config. */
    static final MobDefinition GOBLIN = read(GameTestConfig.GOBLIN, GameTestConfig.stats());
    static final EquipmentCatalog SHIPPED_WEAPONS = DefaultWeapons.catalog(ModStats.CATALOG);

    /** A mob file and the stats where it is read; shows as the mob's id in test names. */
    record MobFile(MobDefinition mob, StatCatalog stats) {
        MobFile {
            Objects.requireNonNull(mob, "mob");
            Objects.requireNonNull(stats, "stats");
        }

        @Override
        public String toString() {
            return mob.id().value();
        }
    }

    private MobTestCatalog() {
    }

    /** Every mob file the tests know: the shipped ones (built-in stats) and the goblin (the game test server's stats). */
    static Stream<MobFile> everyMobFile() {
        return Stream.concat(MOBS.all().stream().map(mob -> new MobFile(mob, ModStats.CATALOG)),
            Stream.of(new MobFile(GOBLIN, GameTestConfig.stats())));
    }

    private static MobDefinition read(Path file, StatCatalog stats) {
        String name = file.getFileName().toString();
        try {
            return MobFileJson.read(MobId.of(name.substring(0, name.length() - ".json".length())), Files.readString(file), stats);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (WeaponFormatException e) {
            throw new IllegalStateException(file + ": " + e.getMessage(), e);
        }
    }
}
