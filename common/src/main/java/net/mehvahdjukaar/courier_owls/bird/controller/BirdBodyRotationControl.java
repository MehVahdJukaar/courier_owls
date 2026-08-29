package net.mehvahdjukaar.courier_owls.bird.controller;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

public class BirdBodyRotationControl extends BodyRotationControl {
    private static final float HEAD_STABLE_ANGLE = 15.0F;
    private static final int TIME_TO_FACE_FRONT = 10;

    private final BaseBirdMob mob;
    private int headStableTicks;
    private float lastStableYHeadRot;

    public BirdBodyRotationControl(BaseBirdMob mob) {
        super(mob);
        this.mob = mob;
    }

    @Override
    public void clientTick() {
        if (this.mob.isHeadingCommanded()) {
            this.mob.yBodyRot = this.mob.getYRot();
            this.mob.yHeadRot = Mth.rotateIfNecessary(this.mob.yHeadRot, this.mob.yBodyRot, this.mob.getMaxHeadYRot());
            this.parkHead();
            return;
        }
        if (this.isMoving() || this.mob.getFirstPassenger() instanceof Mob) {
            super.clientTick();
            this.parkHead();
            return;
        }
        if (Math.abs(this.mob.yHeadRot - this.lastStableYHeadRot) > HEAD_STABLE_ANGLE) {
            this.parkHead();
            this.mob.yBodyRot = Mth.rotateIfNecessary(this.mob.yBodyRot, this.mob.yHeadRot, this.mob.getMaxHeadYRot());
            return;
        }
        this.headStableTicks++;
        int hold = this.mob.settings().gait().headHoldTicks;
        if (this.headStableTicks > hold) {
            float done = Mth.clamp((float) (this.headStableTicks - hold) / TIME_TO_FACE_FRONT, 0.0F, 1.0F);
            this.mob.yBodyRot = Mth.rotateIfNecessary(this.mob.yBodyRot, this.mob.yHeadRot,
                    this.mob.getMaxHeadYRot() * (1.0F - done));
        }
    }

    private void parkHead() {
        this.lastStableYHeadRot = this.mob.yHeadRot;
        this.headStableTicks = 0;
    }

    private boolean isMoving() {
        double dx = this.mob.getX() - this.mob.xo;
        double dz = this.mob.getZ() - this.mob.zo;
        return dx * dx + dz * dz > 2.5000003E-7F;
    }
}
