package io.github.alextixu.mobarmory.forge.client;

import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Reloads the resources of the client on this machine, the same as pressing F3+T (decision O11). Called when the
 * server uses an uploaded texture this client's runtime resource pack does not have yet: in single player right after
 * an upload, on a remote client after the server sent the PNG. Built-in textures and vanilla looks never need it.
 */
public final class ClientResourceReload {
    private ClientResourceReload() {
    }

    public static void requestIfClient() {
        if (FMLEnvironment.dist.isClient()) {
            ClientOnly.reload(); // only loaded on the client, so the server never touches client classes
        }
    }

    private static final class ClientOnly {
        static void reload() {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            minecraft.execute(minecraft::reloadResourcePacks);
        }
    }
}
