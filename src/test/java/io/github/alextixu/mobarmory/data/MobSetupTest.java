package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Module tests for the mobs the game starts with (R4b; 22, H1, H2, B1, B10), in a real folder. The game tests can only
 * look after startup. Plain Java on purpose: {@code forge/MobStartup} only adds the log line and stops the game.
 */
class MobSetupTest {
    private static final EquipmentLibrary WEAPONS = new EquipmentLibrary(DefaultWeapons.catalog(ModStats.CATALOG));

    @TempDir
    Path config;

    @Test
    @DisplayName("H1 H2 第一次啟動放入兩個預設檔；玩家多放的 goblin.json 也變成一種怪物")
    void firstStartAndAPlayersFile() throws Exception {
        MobFolder folder = new MobFolder(config);
        DefaultMobs.installIfMissing(folder);
        MobLibrary first = MobSetup.read(folder, ModStats.CATALOG, WEAPONS);
        assertEquals(List.of(DefaultMobs.BRUTE, DefaultMobs.SKIRMISHER), first.registered());

        Files.copy(Path.of("src/gametest/config/mobarmory/mobs/goblin.json"), folder.mobsFolder().resolve("goblin.json"));
        DefaultMobs.installIfMissing(folder);
        MobLibrary second = MobSetup.read(folder, ModStats.CATALOG, WEAPONS);
        assertEquals(List.of(DefaultMobs.BRUTE, MobId.of("goblin"), DefaultMobs.SKIRMISHER), second.registered());
    }

    @Test
    @DisplayName("B1 怪物檔寫錯 → 啟動失敗，訊息寫出檔名（O2）")
    void wrongFileStopsTheStart() throws Exception {
        MobFolder folder = new MobFolder(config);
        Files.createDirectories(folder.mobsFolder());
        Files.writeString(folder.mobsFolder().resolve("orc.json"), "{\"format\": 1,");

        var error = assertThrows(MobLoadException.class, () -> MobSetup.read(folder, ModStats.CATALOG, WEAPONS));
        assertTrue(error.getMessage().contains("orc.json"), error.getMessage());
    }

    @Test
    @DisplayName("B10 生怪蛋的物品 id（brute_spawn_egg）剛好是一把武器的 id → 啟動失敗，寫出兩個檔")
    void eggIdTakenByAWeapon() throws Exception {
        EquipmentCatalog weapons = EquipmentCatalog.builder(ModStats.CATALOG)
            .add(EquipmentDefinition.of("brute_spawn_egg", EquipmentSlotKind.MAINHAND, "minecraft:egg"))
            .build();
        MobFolder folder = new MobFolder(config);
        DefaultMobs.installIfMissing(folder);
        var error = assertThrows(MobLoadException.class, () -> MobSetup.read(folder, ModStats.CATALOG, new EquipmentLibrary(weapons)));
        assertTrue(error.getMessage().contains("brute.json") && error.getMessage().contains("brute_spawn_egg.json"), error.getMessage());
    }
}
