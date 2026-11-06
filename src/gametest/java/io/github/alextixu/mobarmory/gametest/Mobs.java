package io.github.alextixu.mobarmory.gametest;

import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.data.DefaultMobs;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;

/**
 * The mobs the game tests use, as the game has them now (R4b: mobs are files in {@code config/mobarmory/mobs/}, read
 * into {@code MobArmoryMod.MOB_LIBRARY}; the test server starts with the two shipped files and {@code goblin.json}).
 */
final class Mobs {
    /** Only in the game test server's config ({@code src/gametest/config/mobarmory/mobs/goblin.json}); no Java (T2). */
    static final MobId GOBLIN = MobId.of("goblin");

    private Mobs() {
    }

    static MobDefinition brute() {
        return MobArmoryMod.MOB_LIBRARY.current().get(DefaultMobs.BRUTE);
    }

    static MobDefinition skirmisher() {
        return MobArmoryMod.MOB_LIBRARY.current().get(DefaultMobs.SKIRMISHER);
    }

    static MobDefinition goblin() {
        return MobArmoryMod.MOB_LIBRARY.current().get(GOBLIN);
    }
}
