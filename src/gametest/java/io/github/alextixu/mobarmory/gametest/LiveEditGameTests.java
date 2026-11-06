package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.checkNear;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.stats;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentDefinition;
import io.github.alextixu.mobarmory.core.equipment.EquipmentId;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLook;
import io.github.alextixu.mobarmory.core.equipment.ReloadReport;
import io.github.alextixu.mobarmory.core.stat.StatDefinition;
import io.github.alextixu.mobarmory.core.stat.StatModifier;
import io.github.alextixu.mobarmory.data.BuiltInTextures;
import io.github.alextixu.mobarmory.data.DefaultWeapons;
import io.github.alextixu.mobarmory.data.WeaponJson;
import io.github.alextixu.mobarmory.data.WeaponLoadException;
import io.github.alextixu.mobarmory.data.WeaponSync;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;

/**
 * System tests for changing weapons while the game runs (R2b, R6): the same path the reload command and the
 * web page use ({@code MobArmoryMod.WEAPON_ADMIN}), with real entities in a real server.
 * <p>
 * They change the shared heavy sword, so they run in their own batch (test environment {@code mobarmory:live_edit},
 * after the other tests), do everything within one tick, and put the shipped definition and file back before
 * they finish.
 */
final class LiveEditGameTests {
    private static final EquipmentId SWORD = Weapons.HEAVY_SWORD;

    private LiveEditGameTests() {
    }

    static void registerAll() {
        test("live_edit_applies_to_sword_in_hand", LiveEditGameTests::appliesToSwordInHand);
        test("live_edit_applies_to_zombie", LiveEditGameTests::appliesToZombie);
        test("reload_applies_file_edit", LiveEditGameTests::reloadAppliesFileEdit);
        test("reload_with_broken_file_changes_nothing", LiveEditGameTests::brokenReloadChangesNothing);
        test("reload_new_and_deleted_files_wait_for_restart", LiveEditGameTests::newAndDeletedFilesWait);
        test("look_change_updates_held_sword", LiveEditGameTests::lookChangeUpdatesHeldSword);
        test("uploaded_texture_reaches_players", LiveEditGameTests::uploadedTextureReachesPlayers);
    }

    private static EquipmentDefinition shipped() {
        try {
            return WeaponJson.read(SWORD, DefaultWeapons.text(SWORD.value()), ModStats.CATALOG);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The shipped heavy sword with one modifier replaced. */
    private static EquipmentDefinition swordWith(StatModifier replacement) {
        EquipmentDefinition sword = shipped();
        List<StatModifier> modifiers = new ArrayList<>();
        for (StatModifier modifier : sword.modifiers()) {
            modifiers.add(modifier.stat().equals(replacement.stat()) ? replacement : modifier);
        }
        return new EquipmentDefinition(SWORD, sword.name(), sword.slot(), modifiers, sword.look());
    }

    private interface Body {
        void run() throws Exception;
    }

    /** Runs the body within this tick, always puts the shipped heavy sword back, then passes. */
    private static void inBatch(GameTestHelper helper, Body body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            restore();
        }
        helper.succeed();
    }

    /** Puts the shipped file and definition back, whatever the test did. */
    static void restore() {
        try {
            MobArmoryMod.WEAPON_FILES.writeWeapon(SWORD, DefaultWeapons.text(SWORD.value()));
            MobArmoryMod.WEAPON_ADMIN.reload();
        } catch (Exception e) {
            throw new IllegalStateException("could not restore heavy_sword.json", e);
        }
    }

    private static double value(LivingEntity entity, StatDefinition stat) {
        return entity.getAttributeValue(stats().holder(stat.id()));
    }

    private static ItemStack sword() {
        return new ItemStack(MobArmoryMod.EQUIPMENT.item(SWORD));
    }

    @SuppressWarnings("removal")
    static ServerPlayer playerHoldingSword(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.MAINHAND, sword());
        MobArmoryMod.EQUIPMENT_STATS.refresh(player);
        return player;
    }

    /** H5 + R2 Q6: the web page's save changes the sword already in a player's hand, without re-equipping. */
    static void appliesToSwordInHand(GameTestHelper helper) {
        inBatch(helper, () -> {
            ServerPlayer player = playerHoldingSword(helper);
            checkNear(helper, 7.0, value(player, ModStats.ATTACK_DAMAGE), "H5 given: attack with the shipped sword");
            MobArmoryMod.WEAPON_ADMIN.update(swordWith(StatModifier.add(ModStats.ATTACK_DAMAGE.id(), 16)));

            checkNear(helper, 17.0, value(player, ModStats.ATTACK_DAMAGE), "H5 then: attack right after the save");
            String file = Files.readString(MobArmoryMod.WEAPON_FILES.weaponsFolder().resolve("heavy_sword.json"));
            check(helper, file.contains("\"add\": 16"), "H5/H7: heavy_sword.json was not written: " + file);
        });
    }

    /** H6: every copy shares one definition, so a zombie's sword changes too. */
    static void appliesToZombie(GameTestHelper helper) {
        inBatch(helper, () -> {
            Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
            zombie.setBaby(false);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, sword());
            MobArmoryMod.EQUIPMENT_STATS.refresh(zombie);
            double base = zombie.getAttributeBaseValue(stats().holder(ModStats.MOVEMENT_SPEED.id()));
            checkNear(helper, base * 0.7, value(zombie, ModStats.MOVEMENT_SPEED), "H6 given: zombie with the shipped sword");

            MobArmoryMod.WEAPON_ADMIN.update(swordWith(StatModifier.percent(ModStats.MOVEMENT_SPEED.id(), -0.5)));

            checkNear(helper, base * 0.5, value(zombie, ModStats.MOVEMENT_SPEED), "H6 then: zombie speed after the save");
        });
    }

    /** H3: edit the file by hand, run the reload, the sword in hand changes. */
    static void reloadAppliesFileEdit(GameTestHelper helper) {
        inBatch(helper, () -> {
            ServerPlayer player = playerHoldingSword(helper);
            MobArmoryMod.WEAPON_FILES.writeWeapon(SWORD, WeaponJson.write(swordWith(StatModifier.add(ModStats.ATTACK_DAMAGE.id(), 10))));

            ReloadReport report = MobArmoryMod.WEAPON_ADMIN.reload();

            check(helper, report.changed().equals(List.of(SWORD)), "H3: report should list heavy_sword, got " + report);
            checkNear(helper, 11.0, value(player, ModStats.ATTACK_DAMAGE), "H3: attack after the reload");
        });
    }

    /** B6: one broken file and the whole reload is refused; the sword keeps its value. */
    static void brokenReloadChangesNothing(GameTestHelper helper) {
        inBatch(helper, () -> {
            try {
                ServerPlayer player = playerHoldingSword(helper);
                MobArmoryMod.WEAPON_FILES.writeWeapon(SWORD, WeaponJson.write(swordWith(StatModifier.add(ModStats.ATTACK_DAMAGE.id(), 10))));
                MobArmoryMod.WEAPON_FILES.writeWeapon(Weapons.DAGGER, "{ \"name\": \"Dagger\" ");
                boolean refused = false;
                try {
                    MobArmoryMod.WEAPON_ADMIN.reload();
                } catch (WeaponLoadException e) {
                    refused = true;
                    check(helper, e.getMessage().contains("dagger.json"), "B6: the problem should name dagger.json: " + e.getMessage());
                }
                check(helper, refused, "B6: a broken dagger.json must stop the reload");
                checkNear(helper, 7.0, value(player, ModStats.ATTACK_DAMAGE), "B6: attack keeps the old value");
            } finally {
                MobArmoryMod.WEAPON_FILES.writeWeapon(Weapons.DAGGER, DefaultWeapons.text(Weapons.DAGGER.value()));
            }
        });
    }

    /** H8 + O11: a new look reaches the sword in hand at once (its item model), stats untouched, no resource reload. */
    static void lookChangeUpdatesHeldSword(GameTestHelper helper) {
        inBatch(helper, () -> {
            ServerPlayer player = playerHoldingSword(helper);
            EquipmentDefinition sword = shipped();
            EquipmentDefinition halberdLook = new EquipmentDefinition(SWORD, sword.name(), sword.slot(), sword.modifiers(),
                EquipmentLook.texture("halberd_netherite"));

            MobArmoryMod.WEAPON_ADMIN.update(halberdLook);

            Identifier model = player.getMainHandItem().get(DataComponents.ITEM_MODEL);
            check(helper, Identifier.parse("mobarmory:look/halberd_netherite").equals(model), "H8: sword in hand should look like the halberd, got " + model);
            checkNear(helper, 7.0, value(player, ModStats.ATTACK_DAMAGE), "H8: a new look keeps the stats");
        });
    }

    /**
     * Q7 ("要能上傳 PNG") + review-01 risk 4: an uploaded PNG becomes the sword's look, the sword in hand points at it,
     * and what the server sends players carries that PNG with its digest.
     */
    static void uploadedTextureReachesPlayers(GameTestHelper helper) {
        inBatch(helper, () -> {
            ServerPlayer player = playerHoldingSword(helper);
            byte[] png = BuiltInTextures.png("scythe_blood").orElseThrow();
            MobArmoryMod.WEAPON_ADMIN.saveTexture("gametest_blade", png);
            EquipmentDefinition sword = shipped();

            MobArmoryMod.WEAPON_ADMIN.update(new EquipmentDefinition(SWORD, sword.name(), sword.slot(), sword.modifiers(),
                EquipmentLook.texture("gametest_blade")));

            Identifier model = player.getMainHandItem().get(DataComponents.ITEM_MODEL);
            check(helper, Identifier.parse("mobarmory:look/gametest_blade").equals(model), "the sword in hand should use the upload, got " + model);
            check(helper, MobArmoryMod.WEAPON_SYNC.uploadedTextures().containsKey("gametest_blade"), "players should be sent the uploaded PNG");
            check(helper, WeaponSync.digest(png).equals(MobArmoryMod.WEAPON_SYNC.current().textureDigests().get("gametest_blade")),
                "the definitions packet should carry the PNG's digest");
        });
    }

    /** B7 + B8: a new file waits for a restart; a deleted file keeps its weapon until a restart. */
    static void newAndDeletedFilesWait(GameTestHelper helper) {
        inBatch(helper, () -> {
            EquipmentId spear = EquipmentId.of("gametest_spear");
            MobArmoryMod.WEAPON_FILES.writeWeapon(spear, DefaultWeapons.text("dagger"));
            Files.delete(MobArmoryMod.WEAPON_FILES.weaponsFolder().resolve("heavy_sword.json"));

            ReloadReport report = MobArmoryMod.WEAPON_ADMIN.reload();

            check(helper, report.pendingRestart().contains(spear), "B7: the new file should wait for a restart: " + report);
            check(helper, !MobArmoryMod.EQUIPMENT_LIBRARY.isRegistered(spear), "B7: the new weapon is not an item yet");
            check(helper, report.fileMissing().contains(SWORD), "B8: heavy_sword should be listed as missing: " + report);
            check(helper, MobArmoryMod.EQUIPMENT_LIBRARY.current().find(SWORD).isPresent(), "B8: heavy_sword is kept");
            Files.delete(MobArmoryMod.WEAPON_FILES.weaponsFolder().resolve("gametest_spear.json"));
        });
    }
}
