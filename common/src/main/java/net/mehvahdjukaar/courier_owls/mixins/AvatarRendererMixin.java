package net.mehvahdjukaar.courier_owls.mixins;

import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.ShoulderOwls;
import net.mehvahdjukaar.courier_owls.owls.ShoulderRidingOwls;
import net.mehvahdjukaar.courier_owls.owls.client.models.OwlOnShoulderLayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin<T extends Avatar>
        extends LivingEntityRenderer<T, AvatarRenderState, PlayerModel> {
    private AvatarRendererMixin(EntityRendererProvider.Context context, PlayerModel model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void courier_owls$addOwlShoulderLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        this.addLayer(new OwlOnShoulderLayer(this, context.getModelSet()));
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void courier_owls$extractShoulderOwls(T entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        ShoulderRidingOwls riders = entity instanceof Player player
                ? OwlMod.SHOULDER_OWLS.getOrCreate(player) : ShoulderRidingOwls.NONE;
        ShoulderOwls owls = (ShoulderOwls) state;
        owls.courier_owls$setShoulderOwl(true, riders.on(true));
        owls.courier_owls$setShoulderOwl(false, riders.on(false));
    }
}
