package io.github.alextixu.mobarmory.forge;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.data.ClientWeaponState;
import io.github.alextixu.mobarmory.data.PngCheck;
import io.github.alextixu.mobarmory.data.WeaponFormatException;
import io.github.alextixu.mobarmory.data.WeaponLoadException;
import io.github.alextixu.mobarmory.data.WeaponLoader;
import io.github.alextixu.mobarmory.data.WeaponStore;
import io.github.alextixu.mobarmory.data.WeaponSync;
import io.github.alextixu.mobarmory.forge.client.ClientResourceReload;
import io.github.alextixu.mobarmory.forge.client.ClientTextures;
import io.github.alextixu.mobarmory.forge.client.GeneratedWeaponPack;
import io.netty.handler.codec.DecoderException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
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
 * Keeps every player's view of the weapons the same as the server's (decision Q3 B, review-01 risk 4): when a player
 * joins, and after every change from the reload command or the web page, the server sends
 * <ol>
 *   <li>each uploaded texture in use that this player has not received yet ({@link Texture}, one PNG per packet, so
 *       no packet comes near Minecraft's size limit);</li>
 *   <li>the definitions, with the SHA-256 of each uploaded texture ({@link Definitions}).</li>
 * </ol>
 * The client puts the definitions into its library (names, tooltips) and, if its runtime resource pack lacks one of
 * those textures, reloads its resources. In single player the client and the server share one library and one folder,
 * so only the reload check runs. When the player leaves a server, {@link #leftServer()} drops the received PNGs and
 * reads this game's own weapon files again (review-03 P2-8).
 */
public final class WeaponSyncChannel {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_WEAPONS = 4096;

    /** id → weapon JSON (the same text as the files), and uploaded texture name → SHA-256. */
    public record Definitions(Map<String, String> weapons, Map<String, String> textureDigests) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Definitions> STREAM_CODEC =
            StreamCodec.ofMember(Definitions::encode, Definitions::decode);

        public Definitions {
            weapons = Collections.unmodifiableMap(new LinkedHashMap<>(weapons));
            textureDigests = Collections.unmodifiableMap(new LinkedHashMap<>(textureDigests));
        }

        void encode(RegistryFriendlyByteBuf buffer) {
            writeMap(buffer, weapons);
            writeMap(buffer, textureDigests);
        }

        static Definitions decode(RegistryFriendlyByteBuf buffer) {
            return new Definitions(readMap(buffer), readMap(buffer));
        }
    }

    /** One uploaded texture's PNG. */
    public record Texture(String name, byte[] png) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Texture> STREAM_CODEC =
            StreamCodec.ofMember(Texture::encode, Texture::decode);

        void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(name);
            buffer.writeByteArray(png);
        }

        static Texture decode(RegistryFriendlyByteBuf buffer) {
            return new Texture(buffer.readUtf(), buffer.readByteArray(PngCheck.MAX_BYTES));
        }
    }

    private static final SimpleChannel CHANNEL = ChannelBuilder
        .named(Identifier.fromNamespaceAndPath(MobArmoryMod.MODID, "weapons"))
        .networkProtocolVersion(2)
        .simpleChannel()
            .play()
                .clientbound()
                    .addMain(Texture.class, Texture.STREAM_CODEC, WeaponSyncChannel::receiveTexture)
                    .addMain(Definitions.class, Definitions.STREAM_CODEC, WeaponSyncChannel::receiveDefinitions)
        .build();

    private final EquipmentLibrary library;
    private final WeaponStore store;
    /** On a client: whether the library holds a server's definitions, and how to go back to this game's files. */
    private final ClientWeaponState client;
    /** Which texture versions each player already has; only touched on the server thread. */
    private final Map<ServerPlayer, Map<String, String>> sentTextures = new WeakHashMap<>();

    public WeaponSyncChannel(EquipmentLibrary library, WeaponStore store, WeaponLoader loader) {
        this.library = library;
        this.store = store;
        this.client = new ClientWeaponState(library, store, loader);
    }

    public void register() {
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sentTextures.remove(player);
                sendTo(player);
            }
        });
    }

    /** The definitions packet as it would be sent now. */
    public Definitions current() {
        return new Definitions(WeaponSync.snapshot(library), WeaponSync.digests(uploadedTextures()));
    }

    /**
     * The texture packets a player who has nothing yet would get now. Files that fail {@code PngCheck} are left out
     * with a warning, so no player is sent a PNG it would refuse.
     */
    public Map<String, byte[]> uploadedTextures() {
        try {
            return WeaponSync.uploadedTexturesInUse(library, store, (name, problem) -> LOGGER.warn(
                "Mob Armory: not sending texture '{}' to players: the file {} (fix or replace it in the textures folder)",
                name, problem));
        } catch (IOException e) {
            LOGGER.warn("Mob Armory: could not read uploaded textures: {}", e.toString());
            return Map.of();
        }
    }

    public void sendToAll(MinecraftServer server) {
        Map<String, byte[]> textures = uploadedTextures(); // read once for everyone
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player, textures);
        }
    }

    /** The client side of this channel; the game tests use it to act as a remote client. */
    public ClientWeaponState client() {
        return client;
    }

    /**
     * On the client, when the player leaves a world or a server: forgets the PNGs the server sent and, if the library
     * held a server's definitions, reads this game's own weapon files again. Called from {@code WeaponClientEvents}.
     */
    public void leftServer() {
        ClientTextures.clear();
        try {
            client.restoreLocal().ifPresent(report ->
                LOGGER.info("Mob Armory: left the server; weapons are read from this game's files again (changed: {})",
                    report.changed()));
        } catch (IOException | WeaponLoadException e) {
            LOGGER.warn("Mob Armory: left the server but could not read this game's weapon files ({}); "
                + "the server's definitions stay until the next restart or server", e.getMessage());
        }
    }

    private void sendTo(ServerPlayer player) {
        sendTo(player, uploadedTextures());
    }

    private void sendTo(ServerPlayer player, Map<String, byte[]> textures) {
        Map<String, String> digests = WeaponSync.digests(textures);
        Map<String, String> sent = sentTextures.computeIfAbsent(player, p -> new HashMap<>());
        try {
            for (Map.Entry<String, byte[]> texture : textures.entrySet()) {
                String digest = digests.get(texture.getKey());
                if (!digest.equals(sent.get(texture.getKey()))) {
                    CHANNEL.send(new Texture(texture.getKey(), texture.getValue()), PacketDistributor.PLAYER.with(player));
                    sent.put(texture.getKey(), digest);
                }
            }
            CHANNEL.send(new Definitions(WeaponSync.snapshot(library), digests), PacketDistributor.PLAYER.with(player));
        } catch (RuntimeException e) {
            LOGGER.warn("Mob Armory: could not send weapons to {}: {}", player.getName().getString(), e.toString());
        }
    }

    private static boolean singlePlayer() {
        return ServerLifecycleHooks.getCurrentServer() != null;
    }

    /** On the client, on the main thread. */
    private static void receiveTexture(Texture texture, CustomPayloadEvent.Context context) {
        context.setPacketHandled(true);
        if (singlePlayer()) {
            return; // the server's textures folder is this game's folder
        }
        if (PngCheck.problem(texture.png()).isPresent()) {
            LOGGER.warn("Mob Armory: ignored an unusable texture '{}' from the server", texture.name());
            return;
        }
        ClientTextures.put(texture.name(), texture.png());
    }

    /** On the client, on the main thread. */
    private static void receiveDefinitions(Definitions definitions, CustomPayloadEvent.Context context) {
        context.setPacketHandled(true);
        if (!singlePlayer()) {
            try {
                MobArmoryMod.WEAPON_SYNC.client().applyFromServer(definitions.weapons());
            } catch (WeaponFormatException e) {
                LOGGER.warn("Mob Armory: ignored weapon definitions from the server: {}", e.getMessage());
                return;
            }
        }
        if (WeaponSync.needsReload(definitions.textureDigests(), GeneratedWeaponPack.textureDigests())) {
            ClientResourceReload.requestIfClient();
        }
    }

    private static void writeMap(RegistryFriendlyByteBuf buffer, Map<String, String> map) {
        buffer.writeVarInt(map.size());
        map.forEach((key, value) -> {
            buffer.writeUtf(key);
            buffer.writeUtf(value);
        });
    }

    private static Map<String, String> readMap(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_WEAPONS) {
            throw new DecoderException("Mob Armory: bad entry count " + count);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            map.put(buffer.readUtf(), buffer.readUtf());
        }
        return map;
    }
}
