package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.content.ModEquipment;
import io.github.alextixu.mobarmory.content.ModStats;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Mod entry point. Only wires the layers together; no game logic lives here.
 * Content comes from {@code content/}, rules from {@code core/}, and the classes in this package adapt them to Forge.
 */
@Mod(MobArmoryMod.MODID)
public final class MobArmoryMod {
    public static final String MODID = "mobarmory";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Public so the game tests (src/gametest) exercise the same objects the game uses.
    public static final StatAttributeBridge STATS = new StatAttributeBridge(ModStats.CATALOG, MODID);
    public static final EquipmentItemFactory EQUIPMENT = new EquipmentItemFactory(ModEquipment.CATALOG, STATS, MODID);
    public static final EquipmentStatApplier EQUIPMENT_STATS = new EquipmentStatApplier(ModEquipment.CATALOG, STATS);

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    static {
        // Decision O9: every piece in one creative tab, no crafting recipes in the prototype.
        TABS.register(MODID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EQUIPMENT.item(ModEquipment.HEAVY_SWORD.id()).getDefaultInstance())
            .displayItems((_, output) -> EQUIPMENT.items().forEach(item -> output.accept(item.get())))
            .build());
    }

    public MobArmoryMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        STATS.register(modBusGroup);
        EQUIPMENT.register(modBusGroup);
        TABS.register(modBusGroup);
        // Game tests live in the separate src/gametest source set and register themselves (see build.gradle).

        new EntityStatBinder(STATS).register();
        EQUIPMENT_STATS.register();
        new StatsCommand(STATS).register();
        new CombatEffectListener(ModStats.COMBAT_EFFECTS, STATS).register();

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(event -> {
            STATS.verifyAll();
            LOGGER.info("Mob Armory: {} stats, {} pieces of equipment", ModStats.CATALOG.size(), ModEquipment.CATALOG.size());
        });
    }
}
