package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.content.ModCombos;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import io.github.alextixu.mobarmory.data.ComboEffectTypes;
import io.github.alextixu.mobarmory.data.ComboFiles;
import io.github.alextixu.mobarmory.data.ComboLoader;
import io.github.alextixu.mobarmory.data.JsonFolder;
import io.github.alextixu.mobarmory.data.MobAssets;
import io.github.alextixu.mobarmory.data.MobEditor;
import io.github.alextixu.mobarmory.data.MobFolder;
import io.github.alextixu.mobarmory.data.MobLoader;
import io.github.alextixu.mobarmory.data.StatFolder;
import io.github.alextixu.mobarmory.data.StatSetup;
import io.github.alextixu.mobarmory.data.WeaponEditor;
import io.github.alextixu.mobarmory.data.WeaponFolder;
import io.github.alextixu.mobarmory.forge.client.GeneratedWeaponPack;
import io.github.alextixu.mobarmory.forge.client.MobArmoryClient;
import io.github.alextixu.mobarmory.forge.client.WeaponClientEvents;
import io.github.alextixu.mobarmory.forge.feedback.ActionBarDisplay;
import io.github.alextixu.mobarmory.forge.feedback.HitFeedbackListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Mod entry point. Only wires the layers together; no game logic lives here.
 * Stats (section E), weapons (R2b) and mobs (R4b) come from the files in {@code config/mobarmory/}, with
 * {@code content/} and the files shipped in the jar as the built-in defaults; rules come from {@code core/}, and the classes in this package adapt them to Forge.
 */
@Mod(MobArmoryMod.MODID)
public final class MobArmoryMod {
    public static final String MODID = "mobarmory";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EquipmentId TAB_ICON = EquipmentId.of("heavy_sword");

    // Public so the game tests (src/gametest) exercise the same objects the game uses.
    /** {@code config/mobarmory/stats/}: the built-in stats plus any the player adds (section E). */
    public static final StatFolder STAT_FILES = new StatFolder(FMLPaths.CONFIGDIR.get().resolve(MODID));
    /** Read before attributes are registered; a wrong file stops the game here with the file name (Q9 A). */
    public static final StatSetup STAT_SETUP = StatStartup.load(STAT_FILES, ModStats.CATALOG);
    /** Every stat in the game is {@code STATS.catalog()}; {@code ModStats.CATALOG} is only the built-in ones. */
    public static final StatAttributeBridge STATS = new StatAttributeBridge(STAT_SETUP.catalog(), MODID, STAT_SETUP::synced);
    /** {@code config/mobarmory/}: weapons/*.json and textures/*.png (decision Q4 A). */
    public static final WeaponFolder WEAPON_FILES = new WeaponFolder(FMLPaths.CONFIGDIR.get().resolve(MODID));
    /** Read before any item is registered; a wrong file stops the game here with the file name (decision O7). */
    public static final EquipmentLibrary EQUIPMENT_LIBRARY = WeaponStartup.load(WEAPON_FILES, STATS.catalog());
    public static final EquipmentItemFactory EQUIPMENT = new EquipmentItemFactory(EQUIPMENT_LIBRARY, STATS, MODID);
    public static final EquipmentStatApplier EQUIPMENT_STATS = new EquipmentStatApplier(EQUIPMENT_LIBRARY, STATS);
    /** Sends the definitions to players so remote clients show the same names and tooltips (decision Q3 B). */
    public static final WeaponSyncChannel WEAPON_SYNC =
        new WeaponSyncChannel(EQUIPMENT_LIBRARY, WEAPON_FILES, WeaponStartup.loader(STATS.catalog()));
    /** The one way to change weapons while the game runs: reload command and web page (R2b, R6). */
    public static final ServerWeaponAdmin WEAPON_ADMIN = new ServerWeaponAdmin(
        new WeaponEditor(EQUIPMENT_LIBRARY, WEAPON_FILES, WeaponStartup.loader(STATS.catalog())), EQUIPMENT_STATS, WEAPON_SYNC);
    /**
     * What a mob holds and wears when it spawns: the pieces its definition lists that exist right now. A piece that
     * is missing or works in another slot is left out with a warning (R4 decision O2, changed to B on 11/07).
     */
    public static final MobEquipmentSource MOB_EQUIPMENT = new MobEquipmentSource(EQUIPMENT_LIBRARY, EQUIPMENT::item);
    /** {@code config/mobarmory/mobs/}: one file per kind of mob (R4b, decision Q4 A). */
    public static final MobFolder MOB_FILES = new MobFolder(FMLPaths.CONFIGDIR.get().resolve(MODID));
    /** Read before entity types are registered; a wrong file stops the game here with the file name (R4b O2). */
    public static final MobLibrary MOB_LIBRARY = MobStartup.load(MOB_FILES, STATS.catalog(), EQUIPMENT_LIBRARY);
    /** One entity type and spawn egg per mob file present at startup (R4, T2; since R4b no Java per mob). */
    public static final MobEntityFactory MOBS = new MobEntityFactory(
        MOB_LIBRARY, STATS, BehaviorGoals.standard(), MOB_EQUIPMENT::stacksFor, EQUIPMENT_STATS::refresh, MODID);
    /** Sends the mob names to players so remote clients show the same names (R4b Q7 A). */
    public static final MobSyncChannel MOB_SYNC = new MobSyncChannel(MOB_LIBRARY);
    /** The one way to change mobs while the game runs: reload command and web page (R4b, A6, A7). */
    public static final ServerMobAdmin MOB_ADMIN = new ServerMobAdmin(
        new MobEditor(MOB_LIBRARY, MOB_FILES, new MobLoader(STATS.catalog())), MOB_SYNC);
    // D1: each hit's result on the action bar of the players taking part.
    public static final HitFeedbackListener HIT_FEEDBACK = new HitFeedbackListener(List.of(new ActionBarDisplay()));
    /**
     * R3: {@code config/mobarmory/combos/}, one file per combo; the defaults (Lunge, Follow-up) are installed on the
     * first start, and a player adds a combo by adding a file (19, Q2 changed to JSON).
     */
    public static final ComboFiles COMBO_FILES = ComboFiles.load(
        new JsonFolder(FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("combos")), new ComboLoader(ComboEffectTypes.STANDARD));

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    static {
        // Decision O9: every piece in one creative tab, no crafting recipes in the prototype; R4 adds the spawn eggs.
        TABS.register(MODID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(MobArmoryMod::tabIcon)
            .displayItems((_, output) -> {
                EQUIPMENT.items().forEach(item -> output.accept(WeaponLooks.stackOf(item.get(), EQUIPMENT_LIBRARY)));
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
        // Item models and uploaded textures are generated at runtime (decision Q7, "要能上傳 PNG"); client side only.
        GeneratedWeaponPack.register(EQUIPMENT_LIBRARY, WEAPON_FILES, fixedClientFiles());
        // Game tests live in the separate src/gametest source set and register themselves (see build.gradle).

        new EntityStatBinder(STATS).register();
        EQUIPMENT_STATS.register();
        new StatsCommand(STATS).register();
        new ReloadCommand(List.of(ReloadCommand.weapons(WEAPON_ADMIN), ReloadCommand.combos(COMBO_FILES),
            ReloadCommand.mobs(MOB_ADMIN))).register();
        WEAPON_SYNC.register();
        MOB_SYNC.register();
        new WebAdminLifecycle(WEAPON_ADMIN, MOB_ADMIN, FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("web.json")).register();
        new CombatEffectListener(ModStats.COMBAT_EFFECTS, STATS, HIT_FEEDBACK).register();
        HIT_FEEDBACK.register();
        new ComboListener(COMBO_FILES.library(), ModCombos.INPUTS, ModCombos.FULL_CHARGE, STATS, HIT_FEEDBACK).register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MobArmoryClient.register(MOBS, MOB_SYNC);
            WeaponClientEvents.register(WEAPON_SYNC);
        }

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(event -> {
            STATS.verifyAll();
            WeaponStartup.verifyLooks(EQUIPMENT_LIBRARY, WEAPON_FILES);
            MOB_EQUIPMENT.warnAll(MOBS.catalog().all());
            LOGGER.info("Mob Armory: {} stats from {}, {} pieces of equipment from {}, {} mobs from {}, {} combos from {}",
                STATS.catalog().size(), STAT_FILES.statsFolder(), EQUIPMENT_LIBRARY.registered().size(),
                WEAPON_FILES.weaponsFolder(), MOB_LIBRARY.registered().size(), MOB_FILES.mobsFolder(),
                COMBO_FILES.library().current().all().size(), COMBO_FILES.folder().path());
        });
    }

    /** Client files that stay the same until a restart: names of added stats (section E) and spawn egg models (R4b O14). */
    private static Map<String, String> fixedClientFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("lang/en_us.json", STAT_SETUP.languageFile(MODID));
        files.putAll(MobAssets.spawnEggModels(MOB_LIBRARY.current().all()));
        return files;
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
