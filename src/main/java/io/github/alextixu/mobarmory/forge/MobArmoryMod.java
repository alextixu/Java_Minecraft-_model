package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.content.ModEquipment;
import io.github.alextixu.mobarmory.content.ModMobs;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.forge.client.MobArmoryClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
    /**
     * R4 mobs. R2b merge point: the only place mobs learn which equipment exists (checked at startup) and how to get
     * a piece's item (when a mob spawns). When R2b replaces ModEquipment, pass the catalog it loads instead.
     */
    public static final MobEntityFactory MOBS = new MobEntityFactory(
        ModMobs.catalog(ModEquipment.CATALOG), STATS, BehaviorGoals.standard(), EQUIPMENT::item, MODID);

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    static {
        // Decision O9: every piece in one creative tab, no crafting recipes in the prototype; R4 adds the spawn eggs.
        TABS.register(MODID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EQUIPMENT.item(ModEquipment.HEAVY_SWORD.id()).getDefaultInstance())
            .displayItems((_, output) -> {
                EQUIPMENT.items().forEach(item -> output.accept(item.get()));
                MOBS.spawnEggs().forEach(egg -> output.accept(egg.get()));
            })
            .build());
    }

    public MobArmoryMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        STATS.register(modBusGroup);
        EQUIPMENT.register(modBusGroup);
        MOBS.register(modBusGroup);
        TABS.register(modBusGroup);
        // Game tests live in the separate src/gametest source set and register themselves (see build.gradle).

        new EntityStatBinder(STATS).register();
        EQUIPMENT_STATS.register();
        new StatsCommand(STATS).register();
        new CombatEffectListener(ModStats.COMBAT_EFFECTS, STATS).register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MobArmoryClient.register(MOBS);
        }

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(event -> {
            STATS.verifyAll();
            LOGGER.info("Mob Armory: {} stats, {} pieces of equipment, {} mobs",
                ModStats.CATALOG.size(), ModEquipment.CATALOG.size(), MOBS.catalog().size());
        });
    }
}
