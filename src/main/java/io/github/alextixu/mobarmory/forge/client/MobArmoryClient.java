package io.github.alextixu.mobarmory.forge.client;

import io.github.alextixu.mobarmory.core.mob.MobDefinition;
import io.github.alextixu.mobarmory.forge.MobEntityFactory;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Client-only wiring. Only called on the client, so dedicated servers never load rendering classes. */
public final class MobArmoryClient {

    private MobArmoryClient() {
    }

    /** One renderer per mob kind, chosen by its look. */
    public static void register(MobEntityFactory mobs) {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> {
            for (MobDefinition definition : mobs.catalog().all()) {
                event.registerEntityRenderer(mobs.type(definition.id()), ArmoryMobRenderer.provider(definition.look()));
            }
        });
    }
}
