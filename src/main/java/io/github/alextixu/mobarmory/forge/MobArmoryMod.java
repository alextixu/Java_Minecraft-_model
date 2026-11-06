package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.data.WeaponEditor;
import io.github.alextixu.mobarmory.data.WeaponFolder;
import io.github.alextixu.mobarmory.forge.client.GeneratedWeaponPack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Mod entry point. Only wires the layers together; no game logic lives here.
 * Stats come from {@code content/}, weapons from the files in {@code config/mobarmory/} (R2b), rules from
 * {@code core/}, and the classes in this package adapt them to Forge.
 */
@Mod(MobArmoryMod.MODID)
public final class MobArmoryMod {
    public static final String MODID = "mobarmory";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EquipmentId TAB_ICON = EquipmentId.of("heavy_sword");

    // Public so the game tests (src/gametest) exercise the same objects the game uses.
    public static final StatAttributeBridge STATS = new StatAttributeBridge(ModStats.CATALOG, MODID);
    /** {@code config/mobarmory/}: weapons/*.json and textures/*.png (decision Q4 A). */
    public static final WeaponFolder WEAPON_FILES = new WeaponFolder(FMLPaths.CONFIGDIR.get().resolve(MODID));
    /** Read before any item is registered; a wrong file stops the game here with the file name (decision O7). */
    public static final EquipmentLibrary EQUIPMENT_LIBRARY = WeaponStartup.load(WEAPON_FILES, ModStats.CATALOG);
    public static final EquipmentItemFactory EQUIPMENT = new EquipmentItemFactory(EQUIPMENT_LIBRARY, STATS, MODID);
    public static final EquipmentStatApplier EQUIPMENT_STATS = new EquipmentStatApplier(EQUIPMENT_LIBRARY, STATS);
    /** Sends the definitions to players so remote clients show the same names and tooltips (decision Q3 B). */
    public static final WeaponSyncChannel WEAPON_SYNC = new WeaponSyncChannel(EQUIPMENT_LIBRARY, WEAPON_FILES);
    /** The one way to change weapons while the game runs: reload command and web page (R2b, R6). */
    public static final ServerWeaponAdmin WEAPON_ADMIN = new ServerWeaponAdmin(
        new WeaponEditor(EQUIPMENT_LIBRARY, WEAPON_FILES, WeaponStartup.loader(ModStats.CATALOG)), EQUIPMENT_STATS, WEAPON_SYNC);

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    static {
        // Decision O9: every piece in one creative tab, no crafting recipes in the prototype.
        TABS.register(MODID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(MobArmoryMod::tabIcon)
            .displayItems((_, output) -> EQUIPMENT.items().forEach(item -> output.accept(WeaponLooks.stackOf(item.get(), EQUIPMENT_LIBRARY))))
            .build());
    }

    public MobArmoryMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        STATS.register(modBusGroup);
        EQUIPMENT.register(modBusGroup);
        TABS.register(modBusGroup);
        // Item models and uploaded textures are generated at runtime (decision Q7, "要能上傳 PNG"); client side only.
        GeneratedWeaponPack.register(EQUIPMENT_LIBRARY, WEAPON_FILES);
        // Game tests live in the separate src/gametest source set and register themselves (see build.gradle).

        new EntityStatBinder(STATS).register();
        EQUIPMENT_STATS.register();
        new StatsCommand(STATS).register();
        new ReloadCommand(WEAPON_ADMIN).register();
        WEAPON_SYNC.register();
        new WebAdminLifecycle(WEAPON_ADMIN, FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("web.json")).register();
        new CombatEffectListener(ModStats.COMBAT_EFFECTS, STATS).register();

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(event -> {
            STATS.verifyAll();
            WeaponStartup.verifyLooks(EQUIPMENT_LIBRARY, WEAPON_FILES);
            LOGGER.info("Mob Armory: {} stats, {} pieces of equipment from {}", ModStats.CATALOG.size(),
                EQUIPMENT_LIBRARY.registered().size(), WEAPON_FILES.weaponsFolder());
        });
    }

    /** The heavy sword if it still exists, else the first piece, else a vanilla sword. */
    private static ItemStack tabIcon() {
        if (EQUIPMENT_LIBRARY.isRegistered(TAB_ICON)) {
            return WeaponLooks.stackOf(EQUIPMENT.item(TAB_ICON), EQUIPMENT_LIBRARY);
        }
        return EQUIPMENT.items().stream().findFirst()
            .map(item -> item.get().getDefaultInstance())
            .orElseGet(() -> new ItemStack(Items.IRON_SWORD));
    }
}
