package io.github.alextixu.mobarmory.forge.client;

import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.forge.MobEntityFactory;
import io.github.alextixu.mobarmory.forge.MobSyncChannel;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Client-only wiring. Only called on the client, so dedicated servers never load rendering classes. */
public final class MobArmoryClient {

    private MobArmoryClient() {
    }

    /**
     * One renderer per mob kind, chosen by the body in this computer's mob file at startup (R4b O9: a new body shows
     * after a restart). When the player leaves a server, the names it sent are dropped (Q7 A).
     */
    public static void register(MobEntityFactory mobs, MobSyncChannel sync) {
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> sync.leftServer());
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> {
            for (MobDefinition definition : mobs.catalog().all()) {
                event.registerEntityRenderer(mobs.type(definition.id()), ArmoryMobRenderer.provider(definition.look()));
            }
        });
    }
}
