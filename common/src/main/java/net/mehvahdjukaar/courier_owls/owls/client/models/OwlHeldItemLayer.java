package net.mehvahdjukaar.courier_owls.owls.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.mehvahdjukaar.courier_owls.owls.client.renderers.OwlRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.RandomSource;

public class OwlHeldItemLayer extends RenderLayer<OwlRenderState, OwlModel> {
    private final RandomSource random = RandomSource.create();

    public OwlHeldItemLayer(RenderLayerParent<OwlRenderState, OwlModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                       OwlRenderState state, float yRot, float xRot) {
        if (state.carried.item.isEmpty()) {
            return;
        }
        if (this.getParentModel().legsAreTucked()) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().translateToFeet(poseStack);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180));

        ItemEntityRenderer.submitMultipleFromCount(poseStack, collector, lightCoords, state.carried,
                this.random);
        poseStack.popPose();
    }
}
