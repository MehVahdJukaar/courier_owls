package net.mehvahdjukaar.courier_owls.owls.client.renderers;

import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class OwlRenderState extends LivingEntityRenderState {
    public OwlType skin = OwlType.HORNED;

    public boolean sleeping;
    public boolean onFoot;

    public float bodyPitch;
    public float bank;
    public float flapPhase;
    public float wingSpread;
    public float glide;
    public float strokeScale;
    public float strokeTilt;
    public float landingProgress;
    public float headTilt;
    public float sit;
    public float carry;
    public float strokeBob;

    public final ItemClusterRenderState carried = new ItemClusterRenderState();
}
