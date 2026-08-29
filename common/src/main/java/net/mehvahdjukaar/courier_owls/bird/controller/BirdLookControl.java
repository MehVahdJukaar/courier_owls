package net.mehvahdjukaar.courier_owls.bird.controller;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class BirdLookControl extends LookControl {
    private static final double MIN_AIM_LENGTH_SQR = 1.0E-4;

    private static final float IDLE_HEAD_TURN = 10.0F;

    private final BaseBirdMob bird;

    public BirdLookControl(BaseBirdMob mob) {
        super(mob);
        this.bird = mob;
    }

    @Override
    public void tick() {
        if (this.mob.isSleeping()) {
            this.restHead();
            return;
        }
        Heading commanded = this.headBelongsToGoals() ? null : this.commandedHeading();
        if (commanded == null) {
            super.tick();
            return;
        }
        float headTurn = (float) (commanded.bodyTurnPerTick * this.bird.settings().gait().headTurnLead);
        this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot,
                FlightMath.yawTowards(commanded.direction.x, commanded.direction.z), headTurn);
        this.mob.setXRot(this.rotateTowards(this.mob.getXRot(),
                FlightMath.xRotOf(commanded.direction), headTurn));
    }

    private void restHead() {
        this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, IDLE_HEAD_TURN);
        this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), 0.0F, IDLE_HEAD_TURN));
    }

    protected boolean headBelongsToGoals() {
        return false;
    }

    @Nullable
    private Heading commandedHeading() {
        if (!this.bird.isHeadingCommanded()) {
            return null;
        }
        MoveControl moveControl = this.mob.getMoveControl();
        if (!moveControl.hasWanted()) {
            return null;
        }
        Vec3 toCarrot = new Vec3(moveControl.getWantedX(), moveControl.getWantedY(), moveControl.getWantedZ())
                .subtract(this.mob.getEyePosition());
        if (toCarrot.lengthSqr() < MIN_AIM_LENGTH_SQR) {
            return null;
        }

        double bodyTurn = this.bird.isHoldingForLaunch()
                ? this.bird.settings().gait().launchTurnPerTick
                : this.bird.activeEnvelope().turnPerTickAt(this.mob.getDeltaMovement().length()) * Mth.RAD_TO_DEG;
        return new Heading(this.aimPushedOut(toCarrot), bodyTurn);
    }

    private Vec3 aimPushedOut(Vec3 toCarrot) {
        double run = toCarrot.horizontalDistance();
        double aimRun = this.bird.settings().gait().headAimRun;
        if (run >= aimRun) {
            return toCarrot;
        }

        Vec3 bearing = run * run > MIN_AIM_LENGTH_SQR
                ? new Vec3(toCarrot.x / run, 0.0, toCarrot.z / run)
                : Vec3.directionFromRotation(0.0F, this.mob.yBodyRot);
        return new Vec3(bearing.x * aimRun, toCarrot.y, bearing.z * aimRun);
    }

    private record Heading(Vec3 direction, double bodyTurnPerTick) {
    }
}
