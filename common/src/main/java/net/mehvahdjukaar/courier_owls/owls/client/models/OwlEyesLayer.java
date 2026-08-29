package net.mehvahdjukaar.courier_owls.owls.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.courier_owls.owls.client.renderers.OwlRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class OwlEyesLayer extends RenderLayer<OwlRenderState, OwlModel> {
    public OwlEyesLayer(RenderLayerParent<OwlRenderState, OwlModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                       OwlRenderState state, float yRot, float xRot) {
        if (state.sleeping) return;

        if (state.chickMesh) return;
        collector.order(1).submitModel(this.getParentModel(), state, poseStack,
                RenderTypes.eyes(state.skin.eyesTexture), lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor, null);
    }
}
