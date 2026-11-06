package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.stats;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentCatalog;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.core.equipment.EquipmentSlotKind;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatModifier;
import io.github.alextixu.mobarmory.data.DefaultWeapons;
import io.github.alextixu.mobarmory.forge.ArmoryMob;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import io.github.alextixu.mobarmory.forge.MobEquipmentSource;
import io.github.alextixu.mobarmory.forge.WeaponLooks;
import java.nio.file.Files;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * System tests for mobs and the weapon files (R4 after the merge with R2b, review-02 6-3 a and b): a mob whose weapon
 * is gone spawns without it instead of stopping the game (decision O2 changed to B on 11/07), and a weapon changed while
 * the game runs changes the mobs holding it.
 * <p>
 * The tests that change the shared heavy sword run in main's {@code mobarmory:live_edit} batch and follow its rules
 * ({@link LiveEditGameTests#inBatch}): everything within one tick, the shipped file put back before they finish.
 */
final class MobWeaponGameTests {
    /** The middle of {@code mobarmory:arena}. */
    private static final BlockPos HERE = new BlockPos(12, 0, 12);

    private MobWeaponGameTests() {
    }

    static void registerAll() {
        test("mob_spawns_without_deleted_weapon", MobWeaponGameTests::spawnsWithoutDeletedWeapon);
        test("mob_equipment_from_startup_weapons", MobWeaponGameTests::equipmentFromStartupWeapons);
        test("brute_follows_heavy_sword_edit", MobWeaponGameTests::bruteFollowsHeavySwordEdit);
    }

    /** Spawns like a spawn egg would (finalizeSpawn puts on the equipment) and applies its stats now, not next tick. */
    private static ArmoryMob spawn(GameTestHelper helper, MobDefinition mob) {
        ArmoryMob entity = helper.spawn(MobArmoryMod.MOBS.type(mob.id()), HERE, EntitySpawnReason.SPAWN_ITEM_USE);
        MobArmoryMod.EQUIPMENT_STATS.refresh(entity);
        return entity;
    }

    private static double value(LivingEntity entity, StatDefinition stat) {
        return entity.getAttributeValue(stats().holder(stat.id()));
    }

    /**
     * 6-3 a after {@code /mobarmory reload}: heavy_sword.json is deleted and the weapons are reloaded. The heavy sword
     * stays an item until a restart (R2b B8), but a brute spawned now comes without it, at its own speed; the skirmisher
     * is not affected. Once the file is back and reloaded, the next brute has its sword again.
     */
    static void spawnsWithoutDeletedWeapon(GameTestHelper helper) {
        LiveEditGameTests.inBatch(helper, () -> {
            Files.delete(MobArmoryMod.WEAPON_FILES.weaponsFolder().resolve("heavy_sword.json"));
            ReloadReport report = MobArmoryMod.WEAPON_ADMIN.reload();
            check(helper, report.fileMissing().contains(Weapons.HEAVY_SWORD), "heavy_sword.json should be missing: " + report);

            ArmoryMob brute = spawn(helper, Mobs.brute());
            ArmoryMob skirmisher = spawn(helper, Mobs.skirmisher());
            try {
                check(helper, brute.getMainHandItem().isEmpty(), "6-3 a: the brute should spawn without the deleted heavy sword, holds " + brute.getMainHandItem());
                checkNear(helper, 40.0, brute.getMaxHealth(), "6-3 a: the rest of the brute is unchanged (max health)");
                checkNear(helper, 4.0, value(brute, ModStats.ATTACK_DAMAGE), "6-3 a: brute attack without the sword");
                checkNear(helper, 0.25, value(brute, ModStats.MOVEMENT_SPEED), "6-3 a: brute speed without the sword");
                check(helper, MobArmoryMod.MOB_EQUIPMENT.check(Mobs.brute()).problems().size() == 1, "6-3 a: one problem for the brute");
                check(helper, skirmisher.getMainHandItem().is(MobArmoryMod.EQUIPMENT.item(Weapons.DAGGER)), "the skirmisher keeps its dagger");
            } finally {
                brute.discard();
                skirmisher.discard();
            }

            LiveEditGameTests.restore();
            ArmoryMob after = spawn(helper, Mobs.brute());
            try {
                check(helper, after.getMainHandItem().is(MobArmoryMod.EQUIPMENT.item(Weapons.HEAVY_SWORD)), "with the file back, a new brute holds the heavy sword");
                checkNear(helper, 0.175, value(after, ModStats.MOVEMENT_SPEED), "and is slowed by it again");
            } finally {
                after.discard();
            }
        });
    }

    /**
     * 6-3 a at startup: what a restart sees after heavy_sword.json was changed to the off hand and dagger.json was
     * deleted. Nothing stops the game; both mobs spawn without the piece and the startup check only warns. With the
     * shipped files, each gets its weapon, already showing the weapon's current look.
     */
    static void equipmentFromStartupWeapons(GameTestHelper helper) {
        EquipmentCatalog shipped = DefaultWeapons.catalog(ModStats.CATALOG);
        EquipmentCatalog.Builder edited = EquipmentCatalog.builder(ModStats.CATALOG);
        for (EquipmentDefinition piece : shipped.all()) {
            if (piece.id().equals(Weapons.HEAVY_SWORD)) {
                edited.add(piece.withSlot(EquipmentSlotKind.OFFHAND));
            } else if (!piece.id().equals(Weapons.DAGGER)) {
                edited.add(piece);
            }
        }
        MobEquipmentSource afterRestart = new MobEquipmentSource(new EquipmentLibrary(edited.build()), MobArmoryMod.EQUIPMENT::item);
        afterRestart.warnAll(MobArmoryMod.MOBS.catalog().all());
        check(helper, afterRestart.stacksFor(Mobs.brute()).isEmpty(), "6-3 a: heavy sword now in the off hand, the brute (main hand) gets nothing");
        check(helper, afterRestart.stacksFor(Mobs.skirmisher()).isEmpty(), "6-3 a: dagger.json deleted, the skirmisher gets nothing");

        MobEquipmentSource fresh = new MobEquipmentSource(new EquipmentLibrary(shipped), MobArmoryMod.EQUIPMENT::item);
        Map<EquipmentSlotKind, ItemStack> brute = fresh.stacksFor(Mobs.brute());
        ItemStack sword = brute.get(EquipmentSlotKind.MAINHAND);
        check(helper, brute.size() == 1 && sword != null && sword.is(MobArmoryMod.EQUIPMENT.item(Weapons.HEAVY_SWORD)), "the brute gets its heavy sword: " + brute);
        check(helper, WeaponLooks.modelId(shipped.get(Weapons.HEAVY_SWORD).look()).equals(sword.get(DataComponents.ITEM_MODEL)),
            "the sword shows its current look, got " + sword.get(DataComponents.ITEM_MODEL));
        helper.succeed();
    }

    /**
     * 6-3 b: changing the heavy sword while the game runs (the web page's save goes through the same call) changes a
     * brute already holding it, at once, and a brute spawned afterwards.
     */
    static void bruteFollowsHeavySwordEdit(GameTestHelper helper) {
        LiveEditGameTests.inBatch(helper, () -> {
            ArmoryMob brute = spawn(helper, Mobs.brute());
            try {
                checkNear(helper, 0.175, value(brute, ModStats.MOVEMENT_SPEED), "given: brute with the shipped heavy sword (-30%)");

                MobArmoryMod.WEAPON_ADMIN.update(LiveEditGameTests.swordWith(StatModifier.percent(ModStats.MOVEMENT_SPEED.id(), -0.5)));

                checkNear(helper, 0.125, value(brute, ModStats.MOVEMENT_SPEED), "6-3 b: the brute holding the sword slows to -50% at once");
                ArmoryMob later = spawn(helper, Mobs.brute());
                try {
                    checkNear(helper, 0.125, value(later, ModStats.MOVEMENT_SPEED), "6-3 b: a brute spawned after the save too");
                } finally {
                    later.discard();
                }
            } finally {
                brute.discard();
            }
        });
    }
}
