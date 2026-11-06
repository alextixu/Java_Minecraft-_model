package io.github.alextixu.mobarmory.forge;

import com.mojang.brigadier.CommandDispatcher;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import io.github.alextixu.mobarmory.data.WeaponLoadException;
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
 * {@code /mobarmory reload}: reads the weapon files again after editing them by hand (decision Q4 A). All or
 * nothing (decision O8); new files wait for a restart (B7), deleted ones stay until a restart (B8).
 */
public final class ReloadCommand {
    private final WeaponAdmin admin;

    public ReloadCommand(WeaponAdmin admin) {
        this.admin = admin;
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

    private int reload(CommandSourceStack source) {
        ReloadReport report;
        try {
            report = admin.reload();
        } catch (WeaponLoadException e) {
            source.sendFailure(Component.translatable("commands.mobarmory.reload.failed"));
            e.problems().forEach(problem -> source.sendFailure(Component.literal("  " + problem)));
            return 0;
        } catch (IOException e) {
            source.sendFailure(Component.translatable("commands.mobarmory.reload.io", e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.mobarmory.reload.done", report.changed().size(),
            names(report.changed())).withStyle(ChatFormatting.GREEN), true);
        line(source, "commands.mobarmory.reload.pending", report.pendingRestart());
        line(source, "commands.mobarmory.reload.missing", report.fileMissing());
        line(source, "commands.mobarmory.reload.slot", report.slotWaits());
        return report.changed().size();
    }

    private static void line(CommandSourceStack source, String key, List<EquipmentId> ids) {
        if (!ids.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(key, names(ids)).withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static String names(List<EquipmentId> ids) {
        return ids.isEmpty() ? "-" : ids.stream().map(EquipmentId::value).collect(Collectors.joining(", "));
    }
}
