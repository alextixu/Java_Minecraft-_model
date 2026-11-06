package io.github.alextixu.mobarmory.data;

import static io.github.alextixu.mobarmory.core.stat.StatModifier.add;
import static io.github.alextixu.mobarmory.core.stat.StatModifier.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Component tests: what the server sends each client about weapons, and how a remote client applies it (B18). */
class WeaponSyncTest {
    private static final EquipmentId SWORD = EquipmentId.of("heavy_sword");

    private static EquipmentLibrary defaults() {
        return new EquipmentLibrary(DefaultWeapons.catalog(ModStats.CATALOG));
    }

    private static EquipmentDefinition renamedSword() {
        return new EquipmentDefinition(SWORD, "重劍", EquipmentSlotKind.MAINHAND, List.of(add(ModStats.ATTACK_DAMAGE.id(), 16),
            percent(ModStats.MOVEMENT_SPEED.id(), -0.30), percent(ModStats.ATTACK_SPEED.id(), -0.40)),
            EquipmentLook.texture("halberd_netherite"));
    }

    @Test
    @DisplayName("B18 Given 伺服器把重劍改名、改數值、換外觀 When 遠端客戶端收到同步 Then 客戶端的定義和伺服器一樣")
    void remoteClientMatchesTheServer() throws Exception {
        EquipmentLibrary server = defaults();
        server.update(renamedSword(), server.version(SWORD));
        EquipmentLibrary client = defaults();

        WeaponSync.apply(client, WeaponSync.snapshot(server));

        assertEquals(server.current().all(), client.current().all());
        assertEquals("重劍", client.current().get(SWORD).name());
    }

    @Test
    @DisplayName("伺服器送來的內容就是武器檔的 JSON，每把一筆")
    void snapshotIsTheFileFormat() {
        Map<String, String> sent = WeaponSync.snapshot(defaults());
        assertEquals(DefaultWeapons.IDS, List.copyOf(sent.keySet()));
        assertTrue(sent.get("heavy_sword").contains("\"percent\": -30"), sent.get("heavy_sword"));
    }

    @Test
    @DisplayName("客戶端沒有的武器（伺服器多出來的）不影響其他武器的同步")
    void unknownWeaponsAreIgnored() throws Exception {
        EquipmentLibrary server = defaults();
        server.update(renamedSword(), 1);
        Map<String, String> sent = new LinkedHashMap<>(WeaponSync.snapshot(server));
        sent.put("server_only", DefaultWeapons.text("dagger"));
        EquipmentLibrary client = defaults();

        WeaponSync.apply(client, sent);

        assertEquals("重劍", client.current().get(SWORD).name());
        assertTrue(client.current().find(EquipmentId.of("server_only")).isEmpty());
    }

    @Test
    @DisplayName("讀不懂的內容 → 丟出錯誤，客戶端什麼都不變")
    void badContentChangesNothing() {
        EquipmentLibrary client = defaults();
        EquipmentDefinition before = client.current().get(SWORD);
        Map<String, String> sent = new LinkedHashMap<>(WeaponSync.snapshot(defaults()));
        sent.put("heavy_sword", "{ broken");

        assertThrows(WeaponFormatException.class, () -> WeaponSync.apply(client, sent));
        assertSame(before, client.current().get(SWORD));
    }
}
