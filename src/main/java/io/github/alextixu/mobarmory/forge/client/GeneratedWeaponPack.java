package io.github.alextixu.mobarmory.forge.client;

import com.mojang.logging.LogUtils;
import io.github.alextixu.mobarmory.core.equipment.EquipmentLibrary;
import io.github.alextixu.mobarmory.data.WeaponAssets;
import io.github.alextixu.mobarmory.data.WeaponStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackMetadataResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A client resource pack made in memory from the weapon definitions (decision Q5 A): item models for every weapon
 * and the custom PNGs from {@code config/mobarmory/textures/}. Nothing is written to disk and nothing needs a
 * rebuild. Every resource reload (F3+T, or the automatic one after the web page changes a look) builds a fresh
 * snapshot, so it always matches the definitions in effect.
 */
public final class GeneratedWeaponPack extends AbstractPackMetadataResources implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ID = "mobarmory_weapons";

    private final PackMetadataSection metadata;
    private final Map<String, byte[]> files;

    private GeneratedWeaponPack(PackLocationInfo location, PackMetadataSection metadata, Map<String, byte[]> files) {
        super(location);
        this.metadata = metadata;
        this.files = files;
    }

    /** Adds the pack to the client's resource packs; always on, above the mod's own resources. */
    public static void register(EquipmentLibrary library, WeaponStore store) {
        AddPackFindersEvent.BUS.addListener(event -> {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) {
                return;
            }
            PackLocationInfo location = new PackLocationInfo(ID, Component.literal("Mob Armory weapons"), PackSource.BUILT_IN, Optional.empty());
            PackMetadataSection metadata = new PackMetadataSection(Component.literal("Weapon models and textures from config/mobarmory"),
                SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).minorRange());
            Pack pack = Pack.readMetaAndCreate(location, new Supplier(library, store, metadata), PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack == null) {
                LOGGER.error("Mob Armory: could not create the weapon resource pack; weapons will show missing textures");
                return;
            }
            event.addRepositorySource(consumer -> consumer.accept(pack));
        });
    }

    private static Map<String, byte[]> snapshot(EquipmentLibrary library, WeaponStore store) {
        return WeaponAssets.generate(library.current().all(), name -> {
            try {
                return store.texture(name);
            } catch (IOException e) {
                LOGGER.warn("Mob Armory: could not read texture {}.png: {}", name, e.toString());
                return Optional.empty();
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
        return type.name().equals("pack") ? (T) metadata : null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (type != PackType.CLIENT_RESOURCES || !location.getNamespace().equals(WeaponAssets.NAMESPACE)) {
            return null;
        }
        byte[] content = files.get(location.getPath());
        return content == null ? null : () -> new ByteArrayInputStream(content);
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !namespace.equals(WeaponAssets.NAMESPACE)) {
            return;
        }
        String prefix = directory.endsWith("/") ? directory : directory + "/";
        files.forEach((path, content) -> {
            if (path.startsWith(prefix)) {
                output.accept(Identifier.fromNamespaceAndPath(namespace, path), () -> new ByteArrayInputStream(content));
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(WeaponAssets.NAMESPACE) : Set.of();
    }

    @Override
    public void close() {
    }

    /** Opens a fresh snapshot each time Minecraft (re)loads resources. */
    private record Supplier(EquipmentLibrary library, WeaponStore store, PackMetadataSection metadata) implements Pack.ResourcesSupplier {
        @Override
        public PackMetadataResources openMetadata(PackLocationInfo location) {
            return new GeneratedWeaponPack(location, metadata, Map.of());
        }

        @Override
        public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata packMetadata) {
            return Stream.of(new GeneratedWeaponPack(location, metadata, snapshot(library, store)));
        }
    }
}
