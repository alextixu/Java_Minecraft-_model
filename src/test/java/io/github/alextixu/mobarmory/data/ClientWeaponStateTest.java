package io.github.alextixu.mobarmory.data;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Module tests: a client goes back to its own weapon files after leaving a server (review-03 P2-8). The conditions
 * for it to work: this game's files are readable, and the library really held a server's definitions.
 */
class ClientWeaponStateTest {
    private static final EquipmentId SWORD = EquipmentId.of("heavy_sword");

    private final InMemoryWeaponStore store = new InMemoryWeaponStore();
    private final EquipmentLibrary library = new EquipmentLibrary(DefaultWeapons.catalog(ModStats.CATALOG));
    private final ClientWeaponState client = new ClientWeaponState(library, store, new WeaponLoader(ModStats.CATALOG, item -> true));

    ClientWeaponStateTest() throws Exception {
        DefaultWeapons.installIfMissing(store);
    }

    /** What a server that renamed the heavy sword and made it hit for 16 sends. */
    private static Map<String, String> serverWithRenamedSword() {
        EquipmentLibrary server = new EquipmentLibrary(DefaultWeapons.catalog(ModStats.CATALOG));
        server.update(new EquipmentDefinition(SWORD, "Server Sword", EquipmentSlotKind.MAINHAND,
            List.of(add(ModStats.ATTACK_DAMAGE.id(), 16)), EquipmentLook.texture("scythe_blood")));
        return WeaponSync.snapshot(server);
    }

    @Test
    @DisplayName("P2-8 Given 連過一台改了重劍的伺服器 When 登出 Then 重劍回到本機檔案的名稱和數值")
    void leavingAServerGoesBackToTheFiles() throws Exception {
        client.applyFromServer(serverWithRenamedSword());
        assertEquals("Server Sword", library.current().get(SWORD).name());
        assertTrue(client.holdsServerDefinitions());

        Optional<ReloadReport> report = client.restoreLocal();

        assertTrue(report.isPresent());
        assertEquals(List.of(SWORD), report.get().changed());
        assertEquals("Heavy Sword", library.current().get(SWORD).name());
        assertEquals(EquipmentLook.item("minecraft:netherite_sword"), library.current().get(SWORD).look());
        assertFalse(client.holdsServerDefinitions());
    }

    @Test
    @DisplayName("P2-8 單人遊戲（從沒套用過伺服器的定義）When 登出 Then 什麼都不做，連檔案都不讀")
    void singlePlayerChangesNothing() throws Exception {
        EquipmentDefinition before = library.current().get(SWORD);
        store.putFile("dagger.json", "{ broken"); // would fail if it were read

        assertEquals(Optional.empty(), client.restoreLocal());
        assertSame(before, library.current().get(SWORD));
    }

    @Test
    @DisplayName("P2-8 登出兩次 → 第二次什麼都不做")
    void secondLogoutChangesNothing() throws Exception {
        client.applyFromServer(serverWithRenamedSword());
        client.restoreLocal();

        assertEquals(Optional.empty(), client.restoreLocal());
    }

    @Test
    @DisplayName("P2-8 本機的武器檔壞了 → 丟出錯誤、留著伺服器的定義，修好後下次登出再還原")
    void brokenLocalFileKeepsTheServersDefinitions() throws Exception {
        client.applyFromServer(serverWithRenamedSword());
        store.putFile("dagger.json", "{ broken");

        WeaponLoadException error = assertThrows(WeaponLoadException.class, client::restoreLocal);

        assertTrue(error.getMessage().contains("dagger.json"), error.getMessage());
        assertEquals("Server Sword", library.current().get(SWORD).name());
        assertTrue(client.holdsServerDefinitions());

        store.putFile("dagger.json", DefaultWeapons.text("dagger"));
        assertTrue(client.restoreLocal().isPresent());
        assertEquals("Heavy Sword", library.current().get(SWORD).name());
    }

    @Test
    @DisplayName("伺服器送來讀不懂的內容 → 什麼都不變，也不算用了伺服器的定義")
    void unreadableServerContentChangesNothing() {
        Map<String, String> sent = new LinkedHashMap<>(serverWithRenamedSword());
        sent.put("heavy_sword", "{ broken");

        assertThrows(WeaponFormatException.class, () -> client.applyFromServer(sent));
        assertEquals("Heavy Sword", library.current().get(SWORD).name());
        assertFalse(client.holdsServerDefinitions());
    }
}
