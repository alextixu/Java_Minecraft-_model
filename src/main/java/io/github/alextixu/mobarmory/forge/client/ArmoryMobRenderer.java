package io.github.alextixu.mobarmory.forge.client;

import io.github.alextixu.mobarmory.core.mob.MobLook;
import io.github.alextixu.mobarmory.forge.ArmoryMob;
import java.util.function.Function;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws a custom mob with the vanilla body its {@link MobLook} names (Q4 A). One renderer class for every mob;
 * the size comes from the scale attribute, which Minecraft already applies when rendering.
 */
public final class ArmoryMobRenderer extends HumanoidMobRenderer<ArmoryMob, ZombieRenderState, HumanoidModel<ZombieRenderState>> {

    /** Everything that differs between the borrowed vanilla bodies. */
    private record Body(ModelLayerLocation layer, ArmorModelSet<ModelLayerLocation> armor,
                        Function<ModelPart, HumanoidModel<ZombieRenderState>> model, Identifier texture) {
    }

    private final Identifier texture;

    private ArmoryMobRenderer(EntityRendererProvider.Context context, Body body) {
        super(context, body.model().apply(context.bakeLayer(body.layer())), 0.5F);
        this.texture = body.texture();
        addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(body.armor(), context.getModelSet(), body.model()),
            context.getEquipmentRenderer()));
    }

    /** The renderer for mobs with this look. */
    public static EntityRendererProvider<ArmoryMob> provider(MobLook look) {
        Body body = body(look.body());
        return context -> new ArmoryMobRenderer(context, body);
    }

    private static Body body(MobLook.Body body) {
        return switch (body) {
            case ZOMBIE -> new Body(ModelLayers.ZOMBIE, ModelLayers.ZOMBIE_ARMOR, ZombieModel::new, vanilla("zombie/zombie"));
            case HUSK -> new Body(ModelLayers.HUSK, ModelLayers.HUSK_ARMOR, ZombieModel::new, vanilla("zombie/husk"));
            case SKELETON -> new Body(ModelLayers.SKELETON, ModelLayers.SKELETON_ARMOR, HumanoidModel::new, vanilla("skeleton/skeleton"));
            case STRAY -> new Body(ModelLayers.STRAY, ModelLayers.STRAY_ARMOR, HumanoidModel::new, vanilla("skeleton/stray"));
        };
    }

    private static Identifier vanilla(String entityTexture) {
        return Identifier.withDefaultNamespace("textures/entity/" + entityTexture + ".png");
    }

    @Override
    public ZombieRenderState createRenderState() {
        return new ZombieRenderState();
    }

    @Override
    public void extractRenderState(ArmoryMob mob, ZombieRenderState state, float partialTicks) {
        super.extractRenderState(mob, state, partialTicks);
        state.isAggressive = mob.isAggressive(); // zombie bodies raise their arms while attacking
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return texture;
    }
}
