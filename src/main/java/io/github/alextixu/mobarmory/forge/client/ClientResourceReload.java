package io.github.alextixu.mobarmory.forge.client;

import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * After a look changed: reload the resources of the client on this machine, the same as pressing F3+T
 * (decision O11). That covers single player, where the server and the client share one game. A dedicated server
 * has no client here; remote players' textures wait for R5.
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
