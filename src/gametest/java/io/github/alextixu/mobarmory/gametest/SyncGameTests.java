package io.github.alextixu.mobarmory.gametest;

import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.check;
import static io.github.alextixu.mobarmory.gametest.MobArmoryGameTests.test;

import io.github.alextixu.mobarmory.content.ModStats;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.data.BuiltInTextures;
import io.github.alextixu.mobarmory.data.DefaultWeapons;
import io.github.alextixu.mobarmory.data.WeaponSync;
import io.github.alextixu.mobarmory.forge.MobArmoryMod;
import io.github.alextixu.mobarmory.forge.WeaponLooks;
import io.github.alextixu.mobarmory.forge.WeaponSyncChannel;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.HashSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * System tests for what clients get (decision Q3 B): the definitions packet survives the network encoding and puts a
 * client's library in line with the server's, and new stacks already show their weapon's look.
 */
final class SyncGameTests {
    private SyncGameTests() {
    }

    static void registerAll() {
        test("sync_packet_round_trip", SyncGameTests::packetRoundTrip);
        test("new_stack_shows_its_look", SyncGameTests::newStackShowsItsLook);
    }

    /** B18: encode as the server would, decode as a client would, apply to a separate library. */
    static void packetRoundTrip(GameTestHelper helper) {
        WeaponSyncChannel.Definitions sent = MobArmoryMod.WEAPON_SYNC.current();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());

        WeaponSyncChannel.Definitions.STREAM_CODEC.encode(buffer, sent);
        WeaponSyncChannel.Definitions received = WeaponSyncChannel.Definitions.STREAM_CODEC.decode(buffer);

        check(helper, received.weapons().equals(sent.weapons()), "the packet changed on the way: " + received.weapons().keySet());
        check(helper, received.textureDigests().equals(sent.textureDigests()), "texture digests changed on the way");
        check(helper, buffer.readableBytes() == 0, "bytes left over after decoding: " + buffer.readableBytes());

        byte[] png = BuiltInTextures.png("dagger_ruby").orElseThrow();
        WeaponSyncChannel.Texture.STREAM_CODEC.encode(buffer, new WeaponSyncChannel.Texture("an_upload", png));
        WeaponSyncChannel.Texture texture = WeaponSyncChannel.Texture.STREAM_CODEC.decode(buffer);
        check(helper, texture.name().equals("an_upload") && Arrays.equals(texture.png(), png), "the texture packet changed on the way");
        EquipmentLibrary client = new EquipmentLibrary(DefaultWeapons.catalog(ModStats.CATALOG));
        try {
            WeaponSync.apply(client, received.weapons());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        // Same pieces with the same definitions; the order is the client's own (startup order), so compare as sets.
        check(helper, new HashSet<>(client.current().all()).equals(new HashSet<>(MobArmoryMod.EQUIPMENT_LIBRARY.current().all())),
            "a client library fed this packet should match the server");
        helper.succeed();
    }

    /** O11: the creative tab and new stacks carry the item model of their current look. */
    static void newStackShowsItsLook(GameTestHelper helper) {
        ItemStack sword = WeaponLooks.stackOf(MobArmoryMod.EQUIPMENT.item(Weapons.HEAVY_SWORD), MobArmoryMod.EQUIPMENT_LIBRARY);
        Identifier model = sword.get(DataComponents.ITEM_MODEL);
        check(helper, Identifier.parse("minecraft:netherite_sword").equals(model), "heavy sword should look like a netherite sword, got " + model);
        check(helper, !WeaponLooks.apply(sword, MobArmoryMod.EQUIPMENT_LIBRARY), "applying the same look twice should change nothing");
        helper.succeed();
    }
}
