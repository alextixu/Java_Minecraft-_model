package io.github.alextixu.mobarmory.forge;

import com.mojang.brigadier.CommandDispatcher;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobReloadReport;
import io.github.alextixu.mobarmory.data.MobLoadException;
import io.github.alextixu.mobarmory.data.WeaponLoadException;
import io.github.alextixu.mobarmory.web.MobAdmin;
import io.github.alextixu.mobarmory.web.WeaponAdmin;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * {@code /mobarmory reload}: reads the files in {@code config/mobarmory/} again after editing them by hand (decision
 * Q4 A). Each kind of file is one {@link Section}: weapons (R2b) and mobs (R4b), each all or nothing on its own (R4b
 * O12). New files wait for a restart (B7, B14), deleted ones stay until a restart (B8, B12).
 */
public final class ReloadCommand {

    /** One kind of file the command reads again; prints its own lines. */
    @FunctionalInterface
    public interface Section {
        /** @return how many definitions changed, or -1 if nothing could be reloaded */
        int reload(CommandSourceStack source);
    }

    private final List<Section> sections;

    public ReloadCommand(List<Section> sections) {
        this.sections = List.copyOf(sections);
    }

    /** Weapons (R2b): unchanged messages. */
    public static Section weapons(WeaponAdmin admin) {
        return source -> {
            ReloadReport report;
            try {
                report = admin.reload();
            } catch (WeaponLoadException e) {
                source.sendFailure(Component.translatable("commands.mobarmory.reload.failed"));
                e.problems().forEach(problem -> source.sendFailure(Component.literal("  " + problem)));
                return -1;
            } catch (IOException e) {
                source.sendFailure(Component.translatable("commands.mobarmory.reload.io", e.getMessage()));
                return -1;
            }
            source.sendSuccess(() -> Component.translatable("commands.mobarmory.reload.done", report.changed().size(),
                names(report.changed(), EquipmentId::value)).withStyle(ChatFormatting.GREEN), true);
            line(source, "commands.mobarmory.reload.pending", report.pendingRestart(), EquipmentId::value);
            line(source, "commands.mobarmory.reload.missing", report.fileMissing(), EquipmentId::value);
            line(source, "commands.mobarmory.reload.slot", report.slotWaits(), EquipmentId::value);
            return report.changed().size();
        };
    }

    /** Mobs (R4b). */
    public static Section mobs(MobAdmin admin) {
        return source -> {
            MobReloadReport report;
            try {
                report = admin.reload();
            } catch (MobLoadException e) {
                source.sendFailure(Component.translatable("commands.mobarmory.reload.mobs.failed"));
                e.problems().forEach(problem -> source.sendFailure(Component.literal("  " + problem)));
                return -1;
            } catch (IOException e) {
                source.sendFailure(Component.translatable("commands.mobarmory.reload.mobs.io", e.getMessage()));
                return -1;
            }
            source.sendSuccess(() -> Component.translatable("commands.mobarmory.reload.mobs.done", report.changed().size(),
                names(report.changed(), MobId::value)).withStyle(ChatFormatting.GREEN), true);
            line(source, "commands.mobarmory.reload.mobs.pending", report.pendingRestart(), MobId::value);
            line(source, "commands.mobarmory.reload.mobs.missing", report.fileMissing(), MobId::value);
            line(source, "commands.mobarmory.reload.mobs.body", report.bodyWaits(), MobId::value);
            return report.changed().size();
        };
    }

    public void register() {
        RegisterCommandsEvent.BUS.addListener(event -> register(event.getDispatcher()));
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(MobArmoryMod.MODID)
            .then(Commands.literal("reload")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> reload(ctx.getSource()))));
    }

    /** Every section runs, even after one fails; the result counts the changes of the ones that worked. */
    private int reload(CommandSourceStack source) {
        int changed = 0;
        for (Section section : sections) {
            changed += Math.max(0, section.reload(source));
        }
        return changed;
    }

    private static <T> void line(CommandSourceStack source, String key, List<T> ids, java.util.function.Function<T, String> name) {
        if (!ids.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(key, names(ids, name)).withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static <T> String names(List<T> ids, java.util.function.Function<T, String> name) {
        return ids.isEmpty() ? "-" : ids.stream().map(name).collect(Collectors.joining(", "));
    }
}
