package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.web.AdminHttpServer;
import io.github.alextixu.mobarmory.web.MobAdmin;
import io.github.alextixu.mobarmory.web.WeaponAdmin;
import io.github.alextixu.mobarmory.web.WebSettings;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Opens the web admin when a world (the server) starts and closes it when the world closes (decision O6). Game
 * masters see the address in chat when they join, as a link. If the port is taken the game carries on and says
 * so (B14).
 * <p>
 * The game test server sets {@code -Dmobarmory.web=false} so a test run never fights a running game for the port.
 */
public final class WebAdminLifecycle {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final WeaponAdmin admin;
    private final MobAdmin mobs;
    private final Path settingsFile;
    private volatile AdminHttpServer running;
    private volatile String problem;

    public WebAdminLifecycle(WeaponAdmin admin, MobAdmin mobs, Path settingsFile) {
        this.admin = admin;
        this.mobs = mobs;
        this.settingsFile = settingsFile;
    }

    public void register() {
        ServerStartedEvent.BUS.addListener(event -> start());
        ServerStoppingEvent.BUS.addListener(event -> stop());
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                greet(player);
            }
        });
    }

    private void start() {
        problem = null;
        if (!Boolean.parseBoolean(System.getProperty("mobarmory.web", "true"))) {
            return;
        }
        try {
            WebSettings settings = WebSettings.load(settingsFile);
            if (!settings.enabled()) {
                LOGGER.info("Mob Armory: web admin is turned off in {}", settingsFile);
                return;
            }
            running = AdminHttpServer.start(admin, mobs, settings);
            LOGGER.info("Mob Armory: web admin at {}", running.uri());
        } catch (IOException | IllegalStateException e) {
            problem = e.getMessage();
            LOGGER.warn("Mob Armory: web admin did not start ({}); the game carries on without it", problem);
        }
    }

    private void stop() {
        AdminHttpServer server = running;
        running = null;
        if (server != null) {
            server.close();
            LOGGER.info("Mob Armory: web admin stopped");
        }
    }

    private void greet(ServerPlayer player) {
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())) {
            return;
        }
        AdminHttpServer server = running;
        if (server != null) {
            String url = server.uri().toString();
            Component link = Component.literal(url).withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(server.uri())));
            player.sendSystemMessage(Component.translatable("mobarmory.web.ready", link));
        } else if (problem != null) {
            player.sendSystemMessage(Component.translatable("mobarmory.web.failed", problem).withStyle(ChatFormatting.YELLOW));
        }
    }
}
