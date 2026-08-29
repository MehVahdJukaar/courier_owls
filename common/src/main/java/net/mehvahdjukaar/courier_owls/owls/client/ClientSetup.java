package net.mehvahdjukaar.courier_owls.owls.client;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.client.models.OwlMeshes;
import net.mehvahdjukaar.courier_owls.owls.client.particles.OwlFeatherParticle;
import net.mehvahdjukaar.courier_owls.owls.client.renderers.OwlEntityRenderer;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;
import net.minecraft.client.model.geom.ModelLayerLocation;

public class ClientSetup {
    private static ModelLayerLocation loc(String name) {
        return new ModelLayerLocation(BirdMod.res(name), name);
    }

    public static ModelLayerLocation OWL_MODEL = loc("owl");
    public static ModelLayerLocation OWL_BABY_MODEL = loc("owl_baby");
    public static ModelLayerLocation OWL_CHICK_MODEL = loc("owl_chick");

    public static void init() {
        ClientHelper.addModelLayerRegistration(ClientSetup::registerLayerDefinitions);
        ClientHelper.addEntityRenderersRegistration(ClientSetup::entityRenderers);
        ClientHelper.addParticleRegistration(ClientSetup::particles);
    }

    private static void registerLayerDefinitions(ClientHelper.ModelLayerEvent event) {
        event.register(OWL_MODEL, OwlMeshes::grown);
        event.register(OWL_BABY_MODEL, () -> OwlMeshes.grown().apply(OwlMeshes.BABY_TRANSFORMER));
        event.register(OWL_CHICK_MODEL, OwlMeshes::chick);
    }

    private static void entityRenderers(ClientHelper.EntityRendererEvent event) {
        event.register(OwlMod.OWL.get(), OwlEntityRenderer::new);
    }

    private static void particles(ClientHelper.ParticleEvent event) {
        event.register(OwlMod.OWL_FEATHER_PARTICLE.get(), OwlFeatherParticle.Factory::new);
    }
}
