package io.github.alextixu.mobarmory.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * The weapon textures shipped inside the mod (decision Q5 B): seven HanhanPx textures in
 * {@code assets/mobarmory/textures/item/}, each with a model ({@code models/item/look/<name>.json}) and an item model
 * definition ({@code items/look/<name>.json}). Being in the jar, every client has them, so a weapon looks the same
 * to every player (R5). A weapon file picks one with {@code "look": {"texture": "<name>"}}.
 * <p>
 * Adding a texture: the PNG, its two small JSON files, and one name here; then rebuild.
 */
public final class BuiltInTextures {
    public static final List<String> NAMES = List.of(
        "dagger_iron", "dagger_ruby", "spear_iron", "pitchfork_gold", "halberd_netherite", "halberd_wooden", "scythe_blood");

    private static final String FOLDER = "/assets/mobarmory/textures/item/";

    private BuiltInTextures() {
    }

    public static boolean contains(String name) {
        return NAMES.contains(name);
    }

    /** The PNG from the mod jar, for the web page's preview. */
    public static Optional<byte[]> png(String name) {
        if (!contains(name)) {
            return Optional.empty();
        }
        try (InputStream in = BuiltInTextures.class.getResourceAsStream(FOLDER + name + ".png")) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
