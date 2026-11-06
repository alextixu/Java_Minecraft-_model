package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.core.mob.MobId;
import io.github.alextixu.mobarmory.core.mob.MobLibrary;
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
 * Keeps every player's mob names the same as the server's (R4b decision Q7 A; the same timing as
 * {@link WeaponSyncChannel}: when a player joins and after every change from the reload command or the web page).
 * Only the names travel: stats and size are attributes, which Minecraft syncs itself; behaviors only matter on the
 * server; the body and the kinds of mob come from each computer's own files and change at a restart (O9).
 * <p>
 * A remote client keeps the names in {@link MobNames}, apart from its own mob library, and drops them when it leaves.
 */
public final class MobSyncChannel {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_MOBS = 4096;

    /** Mob id → display name. */
    public record Names(Map<String, String> names) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Names> STREAM_CODEC =
            StreamCodec.ofMember(Names::encode, Names::decode);

        public Names {
            names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
        }

        void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(names.size());
            names.forEach((id, name) -> {
                buffer.writeUtf(id);
                buffer.writeUtf(name);
            });
        }

        static Names decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_MOBS) {
                throw new DecoderException("Mob Armory: bad mob count " + count);
            }
            Map<String, String> names = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                names.put(buffer.readUtf(), buffer.readUtf());
            }
            return new Names(names);
        }
    }

    private static final SimpleChannel CHANNEL = ChannelBuilder
        .named(Identifier.fromNamespaceAndPath(MobArmoryMod.MODID, "mobs"))
        .networkProtocolVersion(1)
        .simpleChannel()
            .play()
                .clientbound()
                    .addMain(Names.class, Names.STREAM_CODEC, MobSyncChannel::receive)
        .build();

    private final MobLibrary library;

    public MobSyncChannel(MobLibrary library) {
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
    public Names current() {
        Map<String, String> names = new LinkedHashMap<>();
        for (MobDefinition mob : library.current().all()) {
            names.put(mob.id().value(), mob.name());
        }
        return new Names(names);
    }

    public void sendToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }

    /** The player left a world or a server: back to this computer's own names. */
    public void leftServer() {
        MobNames.clearFromServer();
    }

    private void sendTo(ServerPlayer player) {
        try {
            CHANNEL.send(current(), PacketDistributor.PLAYER.with(player));
        } catch (RuntimeException e) {
            LOGGER.warn("Mob Armory: could not send mob names to {}: {}", player.getName().getString(), e.toString());
        }
    }

    /** On the client, on the main thread. */
    private static void receive(Names names, CustomPayloadEvent.Context context) {
        context.setPacketHandled(true);
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return; // single player: the server's library is this game's library
        }
        Map<MobId, String> parsed = new LinkedHashMap<>();
        names.names().forEach((id, name) -> {
            try {
                parsed.put(MobId.of(id), name);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Mob Armory: ignored a mob name from the server: {}", e.getMessage());
            }
        });
        MobNames.replaceFromServer(parsed);
    }
}
