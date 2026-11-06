package io.github.alextixu.mobarmory.web;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.mob.MobCatalog;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import io.github.alextixu.mobarmory.core.mob.MobReloadReport;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.data.DefaultMobs;
import io.github.alextixu.mobarmory.data.InMemoryMobStore;
import io.github.alextixu.mobarmory.data.MobEditor;
import io.github.alextixu.mobarmory.data.MobLoadException;
import io.github.alextixu.mobarmory.data.MobLoader;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import java.io.IOException;

/** {@link MobAdmin} without the game: the real editor over an in-memory store holding the two default mobs. */
final class TestMobAdmin implements MobAdmin {
    final InMemoryMobStore store = new InMemoryMobStore();
    final MobLibrary library;
    private final MobEditor editor;

    TestMobAdmin() {
        try {
            DefaultMobs.installIfMissing(store);
            MobLoader loader = new MobLoader(ModStats.CATALOG);
            library = new MobLibrary(MobCatalog.builder(ModStats.CATALOG).addAll(loader.load(store)).build());
            editor = new MobEditor(library, store, loader);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public StatCatalog stats() {
        return library.stats();
    }

    @Override
    public MobLibrary.View view() {
        return library.view();
    }

    @Override
    public void update(MobDefinition changed) throws IOException, WeaponFormatException {
        editor.update(changed);
    }

    @Override
    public void create(MobDefinition definition) throws IOException, WeaponFormatException {
        editor.create(definition);
    }

    @Override
    public MobReloadReport reload() throws IOException, MobLoadException {
        return editor.reload();
    }
}
