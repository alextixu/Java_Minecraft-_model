package io.github.alextixu.mobarmory.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Component tests for {@link MobLibrary} (R4b): the same rules as the weapons' library, for mobs (22, H4, H9, B11, B12,
 * B14, B15). Plain Java, no game.
 */
class MobLibraryTest {
    private static final StatDefinition HEALTH = StatDefinition.vanilla("max_health", "minecraft:max_health", 20, 1, 1024);
    private static final StatDefinition ATTACK = StatDefinition.vanilla("attack_damage", "minecraft:attack_damage", 1, 0, 2048);
    private static final StatCatalog STATS = StatCatalog.builder().add(HEALTH).add(ATTACK).build();

    private static MobDefinition brute(double attack) {
        return MobDefinition.builder("brute").name("Brute").look(MobLook.of(MobLook.Body.ZOMBIE).scaled(1.2))
            .stat(HEALTH, 40).stat(ATTACK, attack)
            .equip(EquipmentSlotKind.MAINHAND, "heavy_sword")
            .behavior(new WanderBehavior(1.0)).behavior(new AttackPlayersBehavior(1.0))
            .build();
    }

    private static MobDefinition goblin() {
        return MobDefinition.builder("goblin").name("Goblin").look(MobLook.of(MobLook.Body.HUSK).scaled(0.8))
            .behavior(new NeutralBehavior(1.3)).behavior(new LeapBehavior()).build();
    }

    private static MobLibrary library() {
        return new MobLibrary(MobCatalog.builder(STATS).add(brute(4)).build());
    }

    @Test
    @DisplayName("H4 Given 狂戰士攻擊 4 When 存成 8 Then 現在的定義是 8，物種清單不變")
    void updateReplacesTheDefinition() {
        MobLibrary library = library();
        MobCatalog before = library.current();
        library.update(brute(8));

        assertEquals(8.0, library.current().get(MobId.of("brute")).stats().get(ATTACK.id()));
        assertEquals(4.0, before.get(MobId.of("brute")).stats().get(ATTACK.id()), "a snapshot taken earlier never changes");
        assertEquals(List.of(MobId.of("brute")), library.registered());
    }

    @Test
    @DisplayName("B11 換身體要重開：存檔 → 錯誤，什麼都沒改；重新載入 → 保留舊身體，列在 bodyWaits，其他欄位照新檔")
    void bodyWaitsForARestart() {
        MobLibrary library = library();
        MobDefinition skeleton = brute(9).withLook(new MobLook(MobLook.Body.SKELETON, 1.5));

        var error = assertThrows(IllegalArgumentException.class, () -> library.update(skeleton));
        assertTrue(error.getMessage().contains("after a restart"), error.getMessage());
        assertEquals(4.0, library.current().get(MobId.of("brute")).stats().get(ATTACK.id()));

        MobReloadReport report = library.replaceAll(List.of(skeleton));
        MobDefinition now = library.current().get(MobId.of("brute"));
        assertEquals(List.of(MobId.of("brute")), report.bodyWaits());
        assertEquals(MobLook.Body.ZOMBIE, now.look().body());
        assertEquals(1.5, now.look().scale(), "the size is not the body: it applies at once");
        assertEquals(9.0, now.stats().get(ATTACK.id()));
    }

    @Test
    @DisplayName("H3 B14 新的怪物等重開：不在物種清單，在待重開；同名不能再加")
    void newMobsWaitForARestart() {
        MobLibrary library = library();
        library.addPending(goblin());

        assertFalse(library.isRegistered(MobId.of("goblin")));
        assertEquals(goblin(), library.pending().get(MobId.of("goblin")));
        assertThrows(IllegalArgumentException.class, () -> library.addPending(goblin()));
        assertThrows(IllegalArgumentException.class, () -> library.addPending(brute(1)));
        assertThrows(NoSuchElementException.class, () -> library.update(goblin()), "not an entity type yet");
    }

    @Test
    @DisplayName("H9 B12 B14 重新載入：改過的列在 changed、新檔待重開、被刪的保留到重開")
    void reloadReportsEveryCase() {
        MobLibrary library = library();
        MobReloadReport changed = library.replaceAll(List.of(brute(6), goblin()));

        assertEquals(List.of(MobId.of("brute")), changed.changed());
        assertEquals(List.of(MobId.of("goblin")), changed.pendingRestart());

        MobDefinition last = library.current().get(MobId.of("brute"));
        MobReloadReport deleted = library.replaceAll(List.of());
        assertEquals(List.of(MobId.of("brute")), deleted.fileMissing());
        assertSame(last, library.current().get(MobId.of("brute")), "kept until a restart");
        assertTrue(library.fileMissing().contains(MobId.of("brute")));

        library.update(brute(7));
        assertFalse(library.fileMissing().contains(MobId.of("brute")), "saving writes the file again");
    }

    @Test
    @DisplayName("B15 全有或全無：有一隻寫錯（數值超出範圍）就什麼都不改")
    void reloadIsAllOrNothing() {
        MobLibrary library = library();
        MobDefinition tooStrong = MobDefinition.builder("brute").look(MobLook.of(MobLook.Body.ZOMBIE)).stat(HEALTH, 5000).build();

        assertThrows(IllegalArgumentException.class, () -> library.replaceAll(List.of(tooStrong, goblin())));
        assertEquals(brute(4), library.current().get(MobId.of("brute")));
        assertTrue(library.pending().isEmpty());
    }

    @Test
    @DisplayName("B5 B6 B7 行為組合：攻擊玩家和中立只能選一種；衝刺要搭配其中一種；速度在 0 和 3 之間")
    void behaviorCombinations() {
        var both = assertThrows(IllegalArgumentException.class, () -> MobDefinition.builder("x").look(MobLook.of(MobLook.Body.HUSK))
            .behavior(new AttackPlayersBehavior(1)).behavior(new NeutralBehavior(1)).build());
        var leapAlone = assertThrows(IllegalArgumentException.class, () -> MobDefinition.builder("x").look(MobLook.of(MobLook.Body.HUSK))
            .behavior(new LeapBehavior()).build());

        assertTrue(both.getMessage().contains("keep only one"), both.getMessage());
        assertTrue(leapAlone.getMessage().contains("needs attack_players or neutral"), leapAlone.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new NeutralBehavior(0));
        assertThrows(IllegalArgumentException.class, () -> new WanderBehavior(3.01));
        assertEquals(3.0, new AttackPlayersBehavior(3.0).speed());
    }

    @Test
    @DisplayName("名稱：沒給就用 id；空白、太長、換行都不行（同武器的名稱規則）")
    void names() {
        assertEquals("statue", MobDefinition.builder("statue").look(MobLook.of(MobLook.Body.STRAY)).build().name());
        assertThrows(IllegalArgumentException.class, () -> MobDefinition.builder("x").name("  ").look(MobLook.of(MobLook.Body.STRAY)).build());
        assertThrows(IllegalArgumentException.class, () -> MobDefinition.builder("x").name("a".repeat(65)).look(MobLook.of(MobLook.Body.STRAY)).build());
        assertThrows(IllegalArgumentException.class, () -> MobDefinition.builder("x").name("two\nlines").look(MobLook.of(MobLook.Body.STRAY)).build());
    }
}
