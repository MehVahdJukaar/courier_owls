package net.mehvahdjukaar.courier_owls.mixins;

import net.mehvahdjukaar.courier_owls.owls.ShoulderOwls;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements ShoulderOwls {
    @Unique
    private @Nullable OwlType courier_owls$owlOnLeftShoulder;
    @Unique
    private @Nullable OwlType courier_owls$owlOnRightShoulder;

    @Override
    public void courier_owls$setShoulderOwl(boolean left, @Nullable OwlType type) {
        if (left) {
            this.courier_owls$owlOnLeftShoulder = type;
        } else {
            this.courier_owls$owlOnRightShoulder = type;
        }
    }

    @Override
    public @Nullable OwlType courier_owls$getShoulderOwl(boolean left) {
        return left ? this.courier_owls$owlOnLeftShoulder : this.courier_owls$owlOnRightShoulder;
    }
}
