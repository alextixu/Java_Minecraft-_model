package io.github.alextixu.mobarmory.gametest;

import net.minecraftforge.fml.common.Mod;

/**
 * Entry point of the test-only mod {@value #MODID}. Forge needs a {@code @Mod} class for every mod in a
 * mods.toml; the tests themselves register through {@link MobArmoryGameTests}' event subscriber.
 * This mod only exists in {@code gradlew runGameTestServer}.
 */
@Mod(MobArmoryGameTestMod.MODID)
public final class MobArmoryGameTestMod {
    public static final String MODID = "mobarmory_gametest";
}
