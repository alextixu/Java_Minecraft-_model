package io.github.alextixu.mobarmory.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Contract tests: the folder store (game) and the in-memory store (tests) behave the same. */
class WeaponStoreContractTest {
    @TempDir
    static Path temp;
    private static int folders;

    static Stream<Named<Function<Path, WeaponStore>>> stores() {
        return Stream.of(
            Named.of("WeaponFolder", root -> new WeaponFolder(root.resolve("store" + folders++))),
            Named.of("InMemoryWeaponStore", root -> new InMemoryWeaponStore()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stores")
    void startsEmptyAndDoesNotExist(Function<Path, WeaponStore> factory) throws IOException {
        WeaponStore store = factory.apply(temp);
        assertFalse(store.exists());
        assertEquals(List.of(), store.weaponFiles());
        assertEquals(List.of(), store.textureNames());
        assertTrue(store.texture("nothing").isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stores")
    void readsWhatWasWrittenSortedByName(Function<Path, WeaponStore> factory) throws IOException {
        WeaponStore store = factory.apply(temp);
        store.writeWeapon(EquipmentId.of("spear"), "{\"name\": \"長矛\"}");
        store.writeWeapon(EquipmentId.of("axe"), "{}");
        store.writeWeapon(EquipmentId.of("spear"), "{\"name\": \"Spear\"}");

        assertTrue(store.exists());
        assertEquals(List.of(new WeaponStore.WeaponFile("axe.json", "{}"),
            new WeaponStore.WeaponFile("spear.json", "{\"name\": \"Spear\"}")), store.weaponFiles());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stores")
    void texturesRoundTrip(Function<Path, WeaponStore> factory) throws IOException {
        WeaponStore store = factory.apply(temp);
        byte[] png = TestPng.of(32, 32);
        store.writeTexture("halberd_netherite", png);
        store.writeTexture("dagger_iron", TestPng.of(16, 16));

        assertArrayEquals(png, store.texture("halberd_netherite").orElseThrow());
        assertEquals(List.of("dagger_iron", "halberd_netherite"), store.textureNames());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stores")
    void textureNamesCannotLeaveTheFolder(Function<Path, WeaponStore> factory) {
        WeaponStore store = factory.apply(temp);
        assertThrows(IllegalArgumentException.class, () -> store.writeTexture("../evil", TestPng.of(16, 16)));
        assertThrows(IllegalArgumentException.class, () -> store.texture("../../secret"));
    }

    @Test
    void folderWritesLeaveNoTemporaryFiles() throws IOException {
        WeaponFolder folder = new WeaponFolder(temp.resolve("atomic"));
        for (int i = 0; i < 5; i++) {
            folder.writeWeapon(EquipmentId.of("sword"), "{\"n\": " + i + "}");
        }
        try (Stream<Path> files = Files.list(folder.weaponsFolder())) {
            assertEquals(List.of("sword.json"), files.map(file -> file.getFileName().toString()).toList());
        }
        assertEquals("{\"n\": 4}", Files.readString(folder.weaponsFolder().resolve("sword.json")));
    }

    @Test
    void folderIgnoresOtherFiles() throws IOException {
        WeaponFolder folder = new WeaponFolder(temp.resolve("mixed"));
        Files.createDirectories(folder.weaponsFolder());
        Files.writeString(folder.weaponsFolder().resolve("notes.txt"), "hello");
        Files.createDirectories(folder.weaponsFolder().resolve("old.json"));
        assertEquals(List.of(), folder.weaponFiles());
    }
}
