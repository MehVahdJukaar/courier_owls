package net.mehvahdjukaar.courier_owls.owls.client.renderers;

import net.mehvahdjukaar.courier_owls.owls.client.ClientSetup;
import net.mehvahdjukaar.courier_owls.owls.client.models.OwlEyesLayer;
import net.mehvahdjukaar.courier_owls.owls.client.models.OwlHeldItemLayer;
import net.mehvahdjukaar.courier_owls.owls.client.models.OwlModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.courier_owls.bird.client.BirdAnimation;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

public class OwlEntityRenderer extends MobRenderer<OwlEntity, OwlRenderState, OwlModel> {
    private final OwlModel grown;
    private final OwlModel chick;
    private final ItemModelResolver itemModelResolver;

    public OwlEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new OwlModel(context.bakeLayer(ClientSetup.OWL_MODEL)), 0.3F);
        this.grown = this.model;
        this.chick = OwlModel.chick(context.bakeLayer(ClientSetup.OWL_CHICK_MODEL));
        this.itemModelResolver = context.getItemModelResolver();
        this.addLayer(new OwlEyesLayer(this));
        this.addLayer(new OwlHeldItemLayer(this));
    }

    @Override
    public OwlRenderState createRenderState() {
        return new OwlRenderState();
    }

    @Override
    public Identifier getTextureLocation(OwlRenderState state) {
        if (state.isBaby) {
            return state.sleeping ? OwlType.CHICK_SLEEPING_TEXTURE : OwlType.CHICK_TEXTURE;
        }
        return state.sleeping ? state.skin.sleepingTexture : state.skin.texture;
    }

    @Override
    public void extractRenderState(OwlEntity entity, OwlRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.skin = OwlType.skinOf(entity);
        state.sleeping = entity.isSleeping();
        state.onFoot = entity.isOnFoot();

        BirdAnimation animation = entity.animation();

        state.bodyPitch = animation.bodyPitch(partialTick) + entity.settings().gait().perchPitch;
        state.bank = animation.bank(partialTick);
        state.flapPhase = animation.flapPhase(partialTick);
        state.wingSpread = animation.wingSpread(partialTick);
        state.glide = animation.glide(partialTick);
        state.strokeScale = animation.strokeScale(partialTick);
        state.strokeTilt = animation.strokeTilt(partialTick);
        state.landingProgress = animation.landingProgress(partialTick);
        state.headTilt = animation.headTilt(partialTick);
        state.sit = animation.sit(partialTick);
        state.carry = animation.carry(partialTick);
        state.strokeBob = animation.strokeBob(partialTick);

        state.carried.extractItemGroupRenderState(entity, entity.getItemBySlot(EquipmentSlot.MAINHAND),
                this.itemModelResolver);
        state.carried.outlineColor = state.outlineColor;
    }

    @Override
    public void submit(OwlRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        this.model = state.isBaby ? this.chick : this.grown;
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected void setupRotations(OwlRenderState state, PoseStack poseStack, float bodyRot, float scale) {
        super.setupRotations(state, poseStack, bodyRot, scale);

        poseStack.translate(0.0F, state.strokeBob, 0.0F);
    }
}
