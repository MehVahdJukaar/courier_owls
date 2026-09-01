package net.mehvahdjukaar.courier_owls.owls.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.mehvahdjukaar.courier_owls.bird.client.BirdAnimationConfig;
import net.mehvahdjukaar.courier_owls.bird.client.TwoPieceWingBeat;
import net.mehvahdjukaar.courier_owls.configs.ClientConfigs;
import net.mehvahdjukaar.courier_owls.owls.client.renderers.OwlRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public class OwlModel extends EntityModel<OwlRenderState> {
    private static final float LEG_TUCK = 1.4F;
    private static final float LANDING_LEG_REACH = 0.5F;
    private static final float CARRY_HOLD = 0.5F;

    private static final float LEG_LEAD = 1.35F;
    private static final float SIT_HIDES_LEGS = 0.5F;
    private static final float WING_SPLAY = Mth.HALF_PI;

    private static final float FLAP_AMPLITUDE = 1.0F;
    private static final float GROWN_CARRY_DROP = 7.0F;
    private static final float CHICK_CARRY_DROP = 3.0F;
    private static final float CHICK_SLEEP_TAIL = 0.1463F;

    private record SleepPose(float bodyY, float bodyZ, float neckY, float neckZ) {}

    private static final SleepPose GROWN_MESH_SLEEP = new SleepPose(21.0F, 11.0F, 18.75F, -3.0F);
    private static final SleepPose CHICK_MESH_SLEEP = new SleepPose(22.0F, 6.5F, 22.5F, -1.5F);

    private final ModelPart body;
    private final ModelPart chestAndLegs;
    @Nullable
    private final ModelPart tail;
    private final ModelPart legLeft;
    private final ModelPart legRight;
    private final ModelPart shoulderLeft;
    private final ModelPart shoulderRight;
    private final ModelPart wingLeft;
    private final ModelPart wingRight;
    private final ModelPart wingLeftTip;
    private final ModelPart wingRightTip;
    private final ModelPart neck;
    private final ModelPart head;

    private final TwoPieceWingBeat beat = new TwoPieceWingBeat();

    private final float bodyRestY;
    private final float chestRestY;
    private final float neckRestY;
    private final float tailRest;
    private final float wingRake;
    private final SleepPose sleepPose;
    private final boolean chickMesh;
    private final float carryDrop;

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
    public boolean curledAsleep;

    public OwlModel(ModelPart root) {
        this(root, false);
    }

    public static OwlModel chick(ModelPart root) {
        return new OwlModel(root, true);
    }

    private OwlModel(ModelPart root, boolean chickMesh) {
        super(root);
        this.chickMesh = chickMesh;
        this.sleepPose = chickMesh ? CHICK_MESH_SLEEP : GROWN_MESH_SLEEP;
        this.carryDrop = chickMesh ? CHICK_CARRY_DROP : GROWN_CARRY_DROP;
        this.body = root.getChild("body");
        this.chestAndLegs = this.body.getChild("chestAndLegs");
        this.tail = this.chestAndLegs.hasChild("tail") ? this.chestAndLegs.getChild("tail") : null;
        this.legLeft = this.chestAndLegs.getChild("legLeft");
        this.legRight = this.chestAndLegs.getChild("legRight");
        this.shoulderLeft = this.chestAndLegs.getChild("shoulderLeft");
        this.shoulderRight = this.chestAndLegs.getChild("shoulderRight");
        this.wingLeft = this.shoulderLeft.getChild("wingLeft");
        this.wingRight = this.shoulderRight.getChild("wingRight");
        this.wingLeftTip = this.wingLeft.getChild("wingLeftTip");
        this.wingRightTip = this.wingRight.getChild("wingRightTip");
        this.neck = root.getChild("neck");
        this.head = this.neck.getChild("head");

        this.bodyRestY = this.body.y;
        this.chestRestY = this.chestAndLegs.y;
        this.neckRestY = this.neck.y;
        this.tailRest = this.tail == null ? 0.0F : this.tail.xRot;
        this.wingRake = this.wingLeft.xRot;
    }

    @Override
    public void setupAnim(OwlRenderState state) {
        super.setupAnim(state);
        this.bodyPitch = state.bodyPitch;
        this.bank = state.bank;
        this.flapPhase = state.flapPhase;
        this.wingSpread = state.wingSpread;
        this.glide = state.glide;
        this.strokeScale = state.strokeScale;
        this.strokeTilt = state.strokeTilt;
        this.landingProgress = state.landingProgress;
        this.headTilt = state.headTilt;
        this.sit = state.sit;
        this.carry = state.carry;
        this.curledAsleep = state.isBaby && state.sleeping;
        this.pose(state.onFoot, state.ageInTicks, state.walkAnimationPos, state.walkAnimationSpeed,
                state.yRot, state.xRot);
        if (this.curledAsleep) {
            this.curlUpAsleep();
        }
    }

    public void pose(boolean standing, float ageInTicks, float limbSwing, float limbSwingAmount,
                     float netHeadYaw, float headPitch) {
        float pitch = headPitch * Mth.DEG_TO_RAD;
        this.neck.xRot = standing ? 0.0F : pitch;
        this.head.xRot = standing ? pitch : 0.0F;
        this.neck.yRot = netHeadYaw * Mth.DEG_TO_RAD;

        this.head.zRot = this.headTilt * Mth.DEG_TO_RAD;

        float settle = this.sit * BirdAnimationConfig.sitDropUnits;
        this.chestAndLegs.y = this.chestRestY + settle;
        this.neck.y = this.neckRestY + settle;

        this.neck.z = 0.0F;
        this.body.xRot = 0.0F;
        this.body.y = this.bodyRestY;
        this.body.z = 0.0F;

        this.chestAndLegs.xRot = this.bodyPitch * Mth.DEG_TO_RAD;

        this.chestAndLegs.zRot = -this.bank * Mth.DEG_TO_RAD;

        this.beat.update(this.flapPhase, this.wingSpread, this.glide, this.strokeScale, ageInTicks);
        this.poseWing(this.shoulderLeft, this.wingLeft, this.wingLeftTip, -1.0F);
        this.poseWing(this.shoulderRight, this.wingRight, this.wingRightTip, 1.0F);

        this.poseLegs(standing, limbSwing, limbSwingAmount);

        if (this.tail != null) {
            this.tail.xRot = this.tailRest + Mth.cos(limbSwing * 0.8F) * 0.4F * limbSwingAmount;
        }
    }

    private void poseWing(ModelPart shoulder, ModelPart wing, ModelPart wingTip, float sideSign) {
        shoulder.xRot = (this.strokeTilt * Mth.DEG_TO_RAD - this.chestAndLegs.xRot) * this.wingSpread;

        wing.xRot = this.wingRake * (1.0F - this.wingSpread);

        float splay = WING_SPLAY * this.wingSpread;
        if (!ClientConfigs.TWO_PIECE_WINGS.get()) {
            float beating = this.wingSpread * (1.0F - this.glide);
            float stroke = FLAP_AMPLITUDE * this.strokeScale * Mth.sin(this.flapPhase * Mth.TWO_PI) * beating;
            wing.zRot = sideSign * (splay + stroke);
            wingTip.zRot = 0.0F;
            wingTip.yScale = 1.0F;
            return;
        }

        float inner = splay + (this.beat.inner + this.beat.flex) * Mth.DEG_TO_RAD;
        float outer = splay + (this.beat.outer + this.beat.flex) * Mth.DEG_TO_RAD;
        wing.zRot = sideSign * inner;
        wingTip.zRot = sideSign * (outer - inner);

        wingTip.yScale = 1.0F + 2.0F * BirdAnimationConfig.wingStretch * this.wingSpread;
    }

    private void poseLegs(boolean standing, float limbSwing, float limbSwingAmount) {
        float reach = Math.min(1.0F, this.landingProgress * LEG_LEAD);

        float stride = Mth.cos(limbSwing * 1.6662F) * 1.4F * limbSwingAmount
                * (1.0F - this.wingSpread) * (1.0F - reach) * (1.0F - this.sit);
        float planted = -this.chestAndLegs.xRot;

        float airHip = planted + Mth.lerp(this.carry, LEG_TUCK * this.wingSpread, -CARRY_HOLD);
        float hip = standing ? planted : airHip;
        hip = Mth.lerp(reach, hip, planted - LANDING_LEG_REACH);
        this.legRight.xRot = hip + stride;
        this.legLeft.xRot = hip - stride;
        boolean showLegs = !this.legsAreTucked();
        this.legRight.visible = showLegs;
        this.legLeft.visible = showLegs;
    }

    private void curlUpAsleep() {
        this.body.xRot = Mth.HALF_PI;
        this.body.y = this.sleepPose.bodyY();
        this.body.z = this.sleepPose.bodyZ();
        this.chestAndLegs.xRot = 0.0F;
        this.chestAndLegs.zRot = 0.0F;
        this.chestAndLegs.y = this.chestRestY;
        this.neck.xRot = 0.0F;
        this.neck.y = this.sleepPose.neckY();
        this.neck.z = this.sleepPose.neckZ();
        if (this.tail != null) {
            this.tail.xRot = CHICK_SLEEP_TAIL;
        }
    }

    public void aimNeck(float yawDegrees) {
        this.neck.yRot = yawDegrees * Mth.DEG_TO_RAD;
    }

    public boolean legsAreTucked() {
        return this.curledAsleep || this.sit >= SIT_HIDES_LEGS;
    }

    public void translateToFeet(PoseStack poseStack) {
        this.body.translateAndRotate(poseStack);
        this.chestAndLegs.translateAndRotate(poseStack);
        this.legRight.translateAndRotate(poseStack);

        poseStack.translate(-this.legRight.x / 16.0F, this.carryDrop / 16.0F, 0.0F);

        poseStack.mulPose(Axis.XP.rotation(-this.chestAndLegs.xRot - this.legRight.xRot));
    }
}
