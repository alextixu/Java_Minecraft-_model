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
        server.update(renamedSword());
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
        server.update(renamedSword());
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

    @Test
    @DisplayName("上傳的貼圖：只送正在用的、內建的不送；摘要不同時客戶端要重新載入資源")
    void uploadedTextures() throws Exception {
        InMemoryWeaponStore store = new InMemoryWeaponStore().putTexture("my_blade", TestPng.of(32, 32)).putTexture("unused", TestPng.of(16, 16));
        EquipmentLibrary server = defaults();
        server.update(new EquipmentDefinition(SWORD, "Heavy Sword", EquipmentSlotKind.MAINHAND, List.of(), EquipmentLook.texture("my_blade")));
        server.update(new EquipmentDefinition(EquipmentId.of("dagger"), "Dagger", EquipmentSlotKind.MAINHAND, List.of(),
            EquipmentLook.texture("dagger_iron")));

        Map<String, byte[]> sent = WeaponSync.uploadedTexturesInUse(server, store, (name, problem) -> {
            throw new AssertionError(name + " " + problem);
        });

        assertEquals(List.of("my_blade"), List.copyOf(sent.keySet()), "only uploaded textures in use; built-in ones are in every jar");
        Map<String, String> digests = WeaponSync.digests(sent);
        assertTrue(WeaponSync.needsReload(digests, Map.of()), "a client without it must reload");
        assertTrue(!WeaponSync.needsReload(digests, digests), "a client that has it must not");
        assertTrue(WeaponSync.needsReload(digests, Map.of("my_blade", "an older picture")), "a replaced picture needs a reload");
    }

    @Test
    @DisplayName("P2-8 Given 遊戲執行中有人把正在用的 PNG 換成壞檔或超過 256 KB 的檔 When 要送給玩家 Then 那張不送、不列進摘要，並說出原因")
    void unusableFilesAreNotSent() throws Exception {
        InMemoryWeaponStore store = new InMemoryWeaponStore()
            .putTexture("my_blade", TestPng.of(32, 32))
            .putTexture("not_a_png", "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .putTexture("too_big", new byte[PngCheck.MAX_BYTES + 1]);
        EquipmentLibrary server = defaults();
        server.update(new EquipmentDefinition(SWORD, "Heavy Sword", EquipmentSlotKind.MAINHAND, List.of(), EquipmentLook.texture("my_blade")));
        server.update(new EquipmentDefinition(EquipmentId.of("dagger"), "Dagger", EquipmentSlotKind.MAINHAND, List.of(),
            EquipmentLook.texture("not_a_png")));
        server.update(new EquipmentDefinition(EquipmentId.of("heavy_armor"), "Heavy Armor", EquipmentSlotKind.CHEST, List.of(),
            EquipmentLook.texture("too_big")));
        Map<String, String> left = new LinkedHashMap<>();

        Map<String, byte[]> sent = WeaponSync.uploadedTexturesInUse(server, store, left::put);

        assertEquals(List.of("my_blade"), List.copyOf(sent.keySet()));
        assertEquals(List.of("not_a_png", "too_big"), List.copyOf(left.keySet()));
        assertTrue(left.get("too_big").contains("256 KB"), left.get("too_big"));
        assertEquals(List.of("my_blade"), List.copyOf(WeaponSync.digests(sent).keySet()), "only what is sent gets a digest");
    }
}
