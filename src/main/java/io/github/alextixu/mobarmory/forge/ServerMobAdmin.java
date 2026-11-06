package io.github.alextixu.mobarmory.forge;

import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
import io.github.alextixu.mobarmory.core.mob.MobReloadReport;
import io.github.alextixu.mobarmory.core.stat.StatCatalog;
import io.github.alextixu.mobarmory.data.MobEditor;
import io.github.alextixu.mobarmory.data.MobLoadException;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import io.github.alextixu.mobarmory.web.MobAdmin;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Connects {@link MobEditor} to the running game, for the reload command and the web page (R4b; Adapter, the same shape
 * as {@link ServerWeaponAdmin}):
 * <ol>
 *   <li>runs every change on the server thread ({@link ServerThread}), so saving from the browser works while a
 *       single-player game is paused too (B20);</li>
 *   <li>then gives every loaded mob of a changed kind the new stats and size at once (Q6 A), and sends the names to
 *       every player (Q7 A). Behaviors and equipment reach a mob the next time it is created.</li>
 * </ol>
 */
public final class ServerMobAdmin implements MobAdmin {
    private final MobEditor editor;
    private final MobSyncChannel sync;

    public ServerMobAdmin(MobEditor editor, MobSyncChannel sync) {
        this.editor = editor;
        this.sync = sync;
    }

    public MobLibrary library() {
        return editor.library();
    }

    @Override
    public StatCatalog stats() {
        return editor.library().stats();
    }

    @Override
    public MobLibrary.View view() {
        return editor.library().view();
    }

    @Override
    public void update(MobDefinition changed) throws IOException, WeaponFormatException {
        onServerThread(() -> {
            editor.update(changed);
            afterChange(Set.of(changed.id()));
            return null;
        });
    }

    @Override
    public void create(MobDefinition definition) throws IOException, WeaponFormatException {
        onServerThread(() -> {
            editor.create(definition);
            return null;
        });
    }

    @Override
    public MobReloadReport reload() throws IOException, MobLoadException {
        try {
            return ServerThread.call(() -> {
                MobReloadReport report = editor.reload();
                afterChange(report.changed());
                return report;
            });
        } catch (IOException | MobLoadException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /** How many loaded mobs took the new stats; also sends the names to every player. */
    private int afterChange(Collection<MobId> changed) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return 0;
        }
        int count = 0;
        if (!changed.isEmpty()) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof ArmoryMob mob && changed.contains(mob.mobId())) {
                        mob.applyDefinitionStats();
                        count++;
                    }
                }
            }
        }
        sync.sendToAll(server);
        return count;
    }

    private void onServerThread(ServerThread.Action<Void> action) throws IOException, WeaponFormatException {
        try {
            ServerThread.call(action);
        } catch (IOException | WeaponFormatException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
