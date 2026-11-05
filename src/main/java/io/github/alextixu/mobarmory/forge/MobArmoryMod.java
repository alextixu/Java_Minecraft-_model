package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.forge.gametest.MobArmoryGameTests;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Mod entry point. Only wires the layers together; no game logic lives here.
 * Content comes from {@code content/}, rules from {@code core/}, and the classes in this package adapt them to Forge.
 */
@Mod(MobArmoryMod.MODID)
public final class MobArmoryMod {
    public static final String MODID = "mobarmory";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Shared with the game tests, which need the same bridge the game uses. */
    public static final StatAttributeBridge STATS = new StatAttributeBridge(ModStats.CATALOG, MODID);

    public MobArmoryMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        STATS.register(modBusGroup);
        new EntityStatBinder(STATS).register();
        MobArmoryGameTests.register(modBusGroup);

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(event -> {
            STATS.verifyAll();
            LOGGER.info("Mob Armory: {} stats", ModStats.CATALOG.size());
        });
    }
}
