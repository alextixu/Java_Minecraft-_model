package io.github.alextixu.mobarmory.forge.feedback;

import io.github.alextixu.mobarmory.core.feedback.HitFilter;
import io.github.alextixu.mobarmory.core.feedback.HitLine;
import io.github.alextixu.mobarmory.core.feedback.HitReport;
import io.github.alextixu.mobarmory.core.feedback.HitText;
import io.github.alextixu.mobarmory.core.feedback.Tone;
import io.github.alextixu.mobarmory.core.feedback.Viewer;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shows a hit on the action bar, the line above the hotbar (decision Q4 C). The attacking player sees the target's
 * name and health, the player who was hit sees the attacker's name and their own health (Q5). Uses Minecraft's own
 * system message packet, so nothing has to be written for the client or for multiplayer (Q8 A).
 */
public final class ActionBarDisplay implements HitDisplay {
    private final BiConsumer<ServerPlayer, Component> sender;

    public ActionBarDisplay() {
        this(ServerPlayer::sendOverlayMessage);
    }

    /** @param sender how a line reaches a player; the game tests pass one that records instead of sending */
    public ActionBarDisplay(BiConsumer<ServerPlayer, Component> sender) {
        this.sender = sender;
    }

    @Override
    public void show(HitReport report, LivingEntity defender, LivingEntity attacker) {
        Set<Viewer> viewers = HitFilter.viewers(report);
        if (viewers.contains(Viewer.ATTACKER) && attacker instanceof ServerPlayer player && player != defender) {
            sender.accept(player, message(report, Viewer.ATTACKER, defender));
        }
        if (viewers.contains(Viewer.DEFENDER) && defender instanceof ServerPlayer player && attacker != null) {
            sender.accept(player, message(report, Viewer.DEFENDER, attacker));
        }
    }

    /** The line one viewer gets. {@code other} is whose name is shown: the target for the attacker, and the other way round. */
    static Component message(HitReport report, Viewer viewer, Entity other) {
        HitLine line = HitText.line(report, viewer);
        return Component.translatable(line.translationKey(), other.getDisplayName(), line.damage(), line.health(), line.maxHealth())
            .withStyle(color(line.tone()));
    }

    static ChatFormatting color(Tone tone) {
        return switch (tone) {
            case NORMAL -> ChatFormatting.WHITE;
            case CRITICAL -> ChatFormatting.GOLD;
            case MISS -> ChatFormatting.GRAY;
            case HURT -> ChatFormatting.RED;
        };
    }
}
