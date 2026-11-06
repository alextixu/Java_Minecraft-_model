package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import io.github.alextixu.mobarmory.data.WeaponSync;
import io.netty.handler.codec.DecoderException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Sends the server's weapon definitions to players (decision Q3 B): when they join, and after every change from the
 * reload command or the web page. A remote client then shows the same names and tooltips as the server.
 * <p>
 * In single player the client and the server share one library in the same game, so the client ignores the packet.
 */
public final class WeaponSyncChannel {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_WEAPONS = 4096;

    /** id → weapon JSON, the same text as the files. */
    public record Definitions(Map<String, String> weapons) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Definitions> STREAM_CODEC =
            StreamCodec.ofMember(Definitions::encode, Definitions::decode);

        public Definitions {
            weapons = Collections.unmodifiableMap(new LinkedHashMap<>(weapons));
        }

        void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(weapons.size());
            weapons.forEach((id, json) -> {
                buffer.writeUtf(id);
                buffer.writeUtf(json);
            });
        }

        static Definitions decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_WEAPONS) {
                throw new DecoderException("Mob Armory: bad weapon count " + count);
            }
            Map<String, String> weapons = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                weapons.put(buffer.readUtf(), buffer.readUtf());
            }
            return new Definitions(weapons);
        }
    }

    private static final SimpleChannel CHANNEL = ChannelBuilder
        .named(Identifier.fromNamespaceAndPath(MobArmoryMod.MODID, "weapons"))
        .networkProtocolVersion(1)
        .simpleChannel()
            .play()
                .clientbound()
                    .addMain(Definitions.class, Definitions.STREAM_CODEC, WeaponSyncChannel::receive)
        .build();

    private final EquipmentLibrary library;

    public WeaponSyncChannel(EquipmentLibrary library) {
        this.library = library;
    }

    public void register() {
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sendTo(player);
            }
        });
    }

    /** The packet as it would be sent now. */
    public Definitions current() {
        return new Definitions(WeaponSync.snapshot(library));
    }

    public void sendToAll(MinecraftServer server) {
        Definitions definitions = current();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(definitions, player);
        }
    }

    private void sendTo(ServerPlayer player) {
        send(current(), player);
    }

    private static void send(Definitions definitions, ServerPlayer player) {
        try {
            CHANNEL.send(definitions, PacketDistributor.PLAYER.with(player));
        } catch (RuntimeException e) {
            LOGGER.warn("Mob Armory: could not send weapon definitions to {}: {}", player.getName().getString(), e.toString());
        }
    }

    /** On the client, on the main thread. */
    private static void receive(Definitions definitions, CustomPayloadEvent.Context context) {
        context.setPacketHandled(true);
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return; // single player: this game's server already changed the shared library
        }
        try {
            WeaponSync.apply(MobArmoryMod.EQUIPMENT_LIBRARY, definitions.weapons());
        } catch (WeaponFormatException e) {
            LOGGER.warn("Mob Armory: ignored weapon definitions from the server: {}", e.getMessage());
        }
    }
}
