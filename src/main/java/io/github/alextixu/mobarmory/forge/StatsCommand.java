package io.github.alextixu.mobarmory.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * {@code /mobarmory stats [target]}: lists every stat of yourself or a target, with its base value,
 * so T1 can be shown in game (decision O6).
 */
public final class StatsCommand {
    private final StatAttributeBridge stats;

    public StatsCommand(StatAttributeBridge stats) {
        this.stats = stats;
    }

    public void register() {
        RegisterCommandsEvent.BUS.addListener(event -> register(event.getDispatcher()));
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(MobArmoryMod.MODID)
            .then(Commands.literal("stats")
                .executes(ctx -> show(ctx.getSource(), ctx.getSource().getEntityOrException()))
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> show(ctx.getSource(), EntityArgument.getEntity(ctx, "target"))))));
    }

    private int show(CommandSourceStack source, Entity entity) throws CommandSyntaxException {
        if (!(entity instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable("commands.mobarmory.stats.not_living", entity.getDisplayName()));
            return 0;
        }
        EntityStatReader reader = new EntityStatReader(living, stats);
        source.sendSuccess(() -> Component.translatable("commands.mobarmory.stats.header", living.getDisplayName())
            .withStyle(ChatFormatting.GOLD), false);
        for (StatDefinition stat : stats.catalog().all()) {
            Component name = Component.translatable(stats.holder(stat.id()).value().getDescriptionId());
            String value = format(reader.get(stat.id()));
            String base = format(reader.base(stat.id()));
            source.sendSuccess(() -> Component.translatable("commands.mobarmory.stats.line", name, value, base), false);
        }
        return stats.catalog().size();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
