package net.mehvahdjukaar.courier_owls.owls.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.courier_owls.owls.ShoulderOwls;
import net.mehvahdjukaar.courier_owls.owls.client.ClientSetup;
import net.mehvahdjukaar.courier_owls.owls.client.renderers.OwlRenderState;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class OwlOnShoulderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final float SIDE_OFFSET = 0.375F;
    private static final float SHOULDER_HEIGHT = -1.5F;
    private static final float CROUCHING_SHOULDER_HEIGHT = -1.3F;
    private static final float SHOULDER_SCALE = 0.85F;

    private static final float SHRINK_LIFT = 1.5F * (1.0F - SHOULDER_SCALE);

    private final OwlModel chick;

    public OwlOnShoulderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet modelSet) {
        super(renderer);

        this.chick = OwlModel.chick(modelSet.bakeLayer(ClientSetup.OWL_CHICK_MODEL));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                       AvatarRenderState state, float yRot, float xRot) {
        ShoulderOwls owls = (ShoulderOwls) state;
        OwlType left = owls.courier_owls$getShoulderOwl(true);
        if (left != null) {
            this.submitOnShoulder(poseStack, collector, lightCoords, state, left, yRot, xRot, true);
        }
        OwlType right = owls.courier_owls$getShoulderOwl(false);
        if (right != null) {
            this.submitOnShoulder(poseStack, collector, lightCoords, state, right, yRot, xRot, false);
        }
    }

    private void submitOnShoulder(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                                  AvatarRenderState player, OwlType skin, float yRot, float xRot,
                                  boolean leftShoulder) {
        poseStack.pushPose();
        float height = player.isCrouching ? CROUCHING_SHOULDER_HEIGHT : SHOULDER_HEIGHT;
        poseStack.translate(leftShoulder ? SIDE_OFFSET : -SIDE_OFFSET, height + SHRINK_LIFT, 0.0F);
        poseStack.scale(SHOULDER_SCALE, SHOULDER_SCALE, SHOULDER_SCALE);

        OwlRenderState perched = new OwlRenderState();
        perched.skin = skin;
        perched.onFoot = true;

        perched.yRot = yRot;
        perched.xRot = xRot;
        collector.submitModel(this.chick, perched, poseStack,
                this.chick.renderType(OwlType.CHICK_TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY,
                player.outlineColor, null);
        poseStack.popPose();
    }
}
