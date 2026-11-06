package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.stats;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModEquipment;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.forge.EquipmentStatApplier;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;

/**
 * T1 with a real server-side player: it joins the world through the player list, ticks, goes through the
 * real equipment-change event, and runs {@code /mobarmory stats} like a player typing it in chat.
 */
final class PlayerGameTests {

    private PlayerGameTests() {
    }

    static void registerAll() {
        test("t1_real_player_and_stats_command", PlayerGameTests::t1RealPlayerAndStatsCommand);
        test("fresh_player_entity_does_not_stack", PlayerGameTests::freshPlayerEntityDoesNotStack);
    }

    private static ItemStack heavySword() {
        return new ItemStack(MobArmoryMod.EQUIPMENT.item(ModEquipment.HEAVY_SWORD.id()));
    }

    private static AttributeInstance speed(ServerPlayer player) {
        return player.getAttribute(stats().holder(ModStats.MOVEMENT_SPEED.id()));
    }

    /** Runs a command as the player and returns what it printed. */
    private static List<String> runAs(GameTestHelper helper, ServerPlayer player, String command) {
        List<String> lines = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                // The server has no mod translations (the client translates), so record the key and its arguments.
                if (message.getContents() instanceof TranslatableContents translatable) {
                    StringBuilder line = new StringBuilder(translatable.getKey());
                    for (Object arg : translatable.getArgs()) {
                        line.append(' ').append(arg instanceof Component component ? component.getString() : String.valueOf(arg));
                    }
                    lines.add(line.toString());
                } else {
                    lines.add(message.getString());
                }
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        helper.getLevel().getServer().getCommands()
            .performPrefixedCommand(player.createCommandSourceStack().withSource(capture), command);
        return lines;
    }

    /** H1 + H2 (T1) through the real event, with the numbers read back from /mobarmory stats. */
    static void t1RealPlayerAndStatsCommand(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.doTick();
        checkNear(helper, 0.1, speed(player).getValue(), "empty-handed speed");

        player.setItemSlot(EquipmentSlot.MAINHAND, heavySword());
        player.doTick(); // Minecraft notices the new item and fires the equipment-change event
        checkNear(helper, 0.07, speed(player).getValue(), "T1: speed with heavy sword after one tick");

        List<String> output = runAs(helper, player, "mobarmory stats");
        check(helper, output.stream().anyMatch(line -> line.contains("0.0700") && line.contains("0.1000")),
            "/mobarmory stats should show speed 0.0700 (base 0.1000), got " + output);

        check(helper, speed(player).pack().modifiers().stream()
                .noneMatch(modifier -> modifier.id().equals(EquipmentStatApplier.MODIFIER_ID)),
            "B12: the equipment modifier must not be saved with the player");

        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.doTick();
        checkNear(helper, 0.1, speed(player).getValue(), "T1: speed after putting the sword away");
        helper.succeed();
    }

    /**
     * B11/B12: after a respawn or relog the player is a brand-new entity whose inventory is restored before its
     * first tick. It must end up at 0.07 like the old one, never 0.049.
     */
    static void freshPlayerEntityDoesNotStack(GameTestHelper helper) {
        ServerPlayer before = helper.makeMockServerPlayerInLevel();
        before.setItemSlot(EquipmentSlot.MAINHAND, heavySword());
        before.doTick();
        checkNear(helper, 0.07, speed(before).getValue(), "speed before relog");

        ServerPlayer after = helper.makeMockServerPlayerInLevel();
        after.setItemSlot(EquipmentSlot.MAINHAND, heavySword());
        after.doTick();
        after.doTick();
        checkNear(helper, 0.07, speed(after).getValue(), "speed of the fresh entity after relog");
        checkNear(helper, 0.07, speed(before).getValue(), "old entity unaffected");
        helper.succeed();
    }
}
