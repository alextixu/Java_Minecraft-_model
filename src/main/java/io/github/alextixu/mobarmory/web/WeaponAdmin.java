package io.github.alextixu.mobarmory.web;

import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import io.github.alextixu.mobarmory.data.WeaponLoadException;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Everything the web page can do to weapons (08-weapon-data-web-boundary-note.md, decision Q2 A). The web layer
 * knows only this interface, never Minecraft: the game's adapter ({@code forge/ServerWeaponAdmin}) runs each call
 * on the server thread and recomputes the holders, and tests use a plain implementation without the game.
 * Monsters (R4) will get a {@code MobAdmin} of the same shape.
 */
public interface WeaponAdmin {

    StatCatalog stats();

    /** Definitions, waiting and file-missing weapons at one moment. */
    EquipmentLibrary.View view();

    /** Vanilla items that make sensible weapon or armor looks, for the look picker. */
    List<String> vanillaLooks();

    /** The textures a weapon can use: the seven built into the mod, then the uploaded ones. */
    List<String> textures() throws IOException;

    /** A texture's PNG, for the page's preview. */
    Optional<byte[]> texture(String name) throws IOException;

    /**
     * Saves a weapon; the later of two saves wins (decision O10).
     *
     * @throws NoSuchElementException if there is no such weapon (404)
     * @throws WeaponFormatException  if the change is not valid (400)
     */
    void update(EquipmentDefinition changed) throws IOException, WeaponFormatException;

    /** A new weapon; an item after the next restart. */
    void create(EquipmentDefinition definition) throws IOException, WeaponFormatException;

    /** Saves an uploaded PNG as {@code textures/<name>.png} (decision Q7, "要能上傳 PNG"). */
    void saveTexture(String name, byte[] png) throws IOException, WeaponFormatException;

    /** Reads the files again; all or nothing. */
    ReloadReport reload() throws IOException, WeaponLoadException;
}
