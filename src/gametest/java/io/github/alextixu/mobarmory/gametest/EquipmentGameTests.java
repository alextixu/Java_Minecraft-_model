package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.stats;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModEquipment;
import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.forge.EquipmentStatApplier;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * System tests for "equipment changes the player's stats" (T1), run in a real server with real items and
 * attributes. Mock players do not tick, so their tests call the applier the equipment-change event calls;
 * the zombie test lets a ticking mob go through the real event.
 */
final class EquipmentGameTests {

    private EquipmentGameTests() {
    }

    static void registerAll() {
        test("t1_heavy_sword_slows_player", EquipmentGameTests::t1HeavySwordSlowsPlayer);
        test("heavy_sword_in_offhand_does_nothing", EquipmentGameTests::heavySwordInOffhandDoesNothing);
        test("slowdowns_multiply", EquipmentGameTests::slowdownsMultiply);
        test("refresh_does_not_stack", EquipmentGameTests::refreshDoesNotStack);
        test("evasion_chestplate_gives_dodge", EquipmentGameTests::evasionChestplateGivesDodge);
        test("dagger_attack_damage", EquipmentGameTests::daggerAttackDamage);
        test("vanilla_sword_adds_no_stats", EquipmentGameTests::vanillaSwordAddsNoStats);
        test("zombie_equipment_event", EquipmentGameTests::zombieEquipmentEvent);
        test("heavy_sword_swings_slower", EquipmentGameTests::heavySwordSwingsSlower);
    }

    /** N3: the heavy sword's -40% attack speed: 4.0 x 0.6 = 2.4, back to 4.0 without it. */
    static void heavySwordSwingsSlower(GameTestHelper helper) {
        Player player = player(helper);
        checkNear(helper, 4.0, value(player, ModStats.ATTACK_SPEED), "N3 given: empty-handed attack speed");

        equip(player, EquipmentSlot.MAINHAND, stack(ModEquipment.HEAVY_SWORD));
        checkNear(helper, 2.4, value(player, ModStats.ATTACK_SPEED), "N3 then: attack speed with heavy sword");

        equip(player, EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        checkNear(helper, 4.0, value(player, ModStats.ATTACK_SPEED), "N3: attack speed after putting it away");
        helper.succeed();
    }

    private static ItemStack stack(EquipmentDefinition equipment) {
        return new ItemStack(MobArmoryMod.EQUIPMENT.item(equipment.id()));
    }

    private static double value(LivingEntity entity, StatDefinition stat) {
        return entity.getAttributeValue(stats().holder(stat.id()));
    }

    private static Player player(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.SURVIVAL);
    }

    /** Puts the item in the slot, then does what the equipment-change event does. */
    private static void equip(LivingEntity entity, EquipmentSlot slot, ItemStack item) {
        entity.setItemSlot(slot, item);
        MobArmoryMod.EQUIPMENT_STATS.refresh(entity);
    }

    /** H1 + H2 = T1: heavy sword in main hand makes speed 0.1 x 0.7 = 0.07; empty hand brings back 0.1. */
    static void t1HeavySwordSlowsPlayer(GameTestHelper helper) {
        Player player = player(helper);
        checkNear(helper, 0.1, value(player, ModStats.MOVEMENT_SPEED), "H1 given: empty-handed speed");

        equip(player, EquipmentSlot.MAINHAND, stack(ModEquipment.HEAVY_SWORD));
        checkNear(helper, 0.07, value(player, ModStats.MOVEMENT_SPEED), "H1 then: speed with heavy sword");

        equip(player, EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        checkNear(helper, 0.1, value(player, ModStats.MOVEMENT_SPEED), "H2 then: speed after putting it away");
        check(helper, player.getAttribute(stats().holder(ModStats.MOVEMENT_SPEED.id()))
            .getModifier(EquipmentStatApplier.MODIFIER_ID) == null, "H2/B10: equipment modifier left behind");
        helper.succeed();
    }

    /** B1: weapons only work in the main hand. */
    static void heavySwordInOffhandDoesNothing(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, EquipmentSlot.OFFHAND, stack(ModEquipment.HEAVY_SWORD));
        checkNear(helper, 0.1, value(player, ModStats.MOVEMENT_SPEED), "B1: speed with heavy sword in off hand");
        helper.succeed();
    }

    /** B2: heavy sword (-30%) and heavy armor (-15%) multiply: 0.1 x 0.7 x 0.85 = 0.0595. */
    static void slowdownsMultiply(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, EquipmentSlot.MAINHAND, stack(ModEquipment.HEAVY_SWORD));
        equip(player, EquipmentSlot.CHEST, stack(ModEquipment.HEAVY_ARMOR));
        checkNear(helper, 0.0595, value(player, ModStats.MOVEMENT_SPEED), "B2: speed with sword and armor");
        checkNear(helper, 8.0, value(player, ModStats.ARMOR), "B2: armor from heavy armor");
        helper.succeed();
    }

    /** B11/B12: recomputing (as after a respawn or relog) never stacks the same piece. */
    static void refreshDoesNotStack(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, EquipmentSlot.MAINHAND, stack(ModEquipment.HEAVY_SWORD));
        for (int i = 0; i < 3; i++) {
            MobArmoryMod.EQUIPMENT_STATS.refresh(player);
        }
        checkNear(helper, 0.07, value(player, ModStats.MOVEMENT_SPEED), "B12: speed after three refreshes (not 0.049)");
        helper.succeed();
    }

    /** H4: evasion chestplate gives 15% dodge. */
    static void evasionChestplateGivesDodge(GameTestHelper helper) {
        Player player = player(helper);
        checkNear(helper, 0.0, value(player, ModStats.DODGE_CHANCE), "H4 given: dodge");
        equip(player, EquipmentSlot.CHEST, stack(ModEquipment.EVASION_CHESTPLATE));
        checkNear(helper, 0.15, value(player, ModStats.DODGE_CHANCE), "H4 then: dodge with evasion chestplate");
        helper.succeed();
    }

    /** H7: dagger gives attack 1.0 + 2 = 3.0, plus its crit chance and speed bonus. */
    static void daggerAttackDamage(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, EquipmentSlot.MAINHAND, stack(ModEquipment.DAGGER));
        checkNear(helper, 3.0, value(player, ModStats.ATTACK_DAMAGE), "H7: attack damage with dagger");
        checkNear(helper, 0.25, value(player, ModStats.CRIT_CHANCE), "dagger crit chance");
        checkNear(helper, 0.11, value(player, ModStats.MOVEMENT_SPEED), "dagger speed");
        helper.succeed();
    }

    /** B13: a vanilla diamond sword adds none of our stats. */
    static void vanillaSwordAddsNoStats(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        checkNear(helper, 0.0, value(player, ModStats.CRIT_CHANCE), "B13: crit chance with diamond sword");
        checkNear(helper, 0.0, value(player, ModStats.DODGE_CHANCE), "B13: dodge chance with diamond sword");
        checkNear(helper, 0.1, value(player, ModStats.MOVEMENT_SPEED), "B13: speed with diamond sword");
        helper.succeed();
    }

    /**
     * The real event path: a zombie ticks, Minecraft notices the new main-hand item and fires the
     * equipment-change event, which must slow the zombie to 70% of its base speed.
     */
    static void zombieEquipmentEvent(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        zombie.setBaby(false);
        double base = zombie.getAttributeBaseValue(stats().holder(ModStats.MOVEMENT_SPEED.id()));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, stack(ModEquipment.HEAVY_SWORD));
        helper.succeedWhen(() -> checkNear(helper, base * 0.7, value(zombie, ModStats.MOVEMENT_SPEED),
            "zombie speed after the equipment-change event"));
    }
}
