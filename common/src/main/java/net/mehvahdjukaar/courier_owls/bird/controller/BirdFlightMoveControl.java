package net.mehvahdjukaar.courier_owls.bird.controller;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.envelope.FlightEnvelope;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class BirdFlightMoveControl extends MoveControl {
    private static final double MIN_DIRECTION_LENGTH = FlightMath.NEAR_ZERO;

    private static final double MIN_HORIZONTAL_AIM_SQR = 1.0E-2;

    private static final float AIR_PIVOT_MIN_YAW_ERROR = 60.0F;

    private double wingThrust;
    private double fallPush;

    private double speedLimit = Double.MAX_VALUE;
    @Nullable
    private FlightEnvelope envelope;

    private boolean refusedLastCommand;

    private final BaseBirdMob bird;

    public BirdFlightMoveControl(BaseBirdMob mob) {
        super(mob);
        this.bird = mob;
    }

    public void setFlightTarget(Vec3 carrot, double speedLimit, FlightEnvelope envelope, double speedModifier) {
        if (!FlightMath.isFinite(carrot) || Double.isNaN(speedLimit) || Double.isNaN(speedModifier)) {
            if (!this.refusedLastCommand) {
                this.refusedLastCommand = true;
                BirdMod.LOGGER.error("refusing a non-finite steering command for bird {}: carrot {}, limit {}, modifier {}, steered by a behavior {}",
                        this.mob.getId(), carrot, speedLimit, speedModifier, this.bird.isFlightTakenOver());
            }
            return;
        }
        this.refusedLastCommand = false;
        this.setWantedPosition(carrot.x, carrot.y, carrot.z, speedModifier);
        this.speedLimit = speedLimit;
        this.envelope = envelope;
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
        super.setWantedPosition(x, y, z, speed);
        this.speedLimit = Double.MAX_VALUE;
        this.envelope = null;
    }

    @Override
    public void tick() {
        this.handOverVanillaInputs();

        boolean nothingToFly = this.mob.getNavigation().isDone() && !this.bird.isFlightTakenOver();
        if (!this.hasWanted() || nothingToFly || this.bird.isHoldingForLaunch()) {
            this.coast();
            return;
        }

        Vec3 toCarrot = new Vec3(this.wantedX, this.wantedY, this.wantedZ).subtract(this.mob.position());
        if (toCarrot.length() < MIN_DIRECTION_LENGTH) {
            this.coast();
            return;
        }

        FlightEnvelope envelope = this.envelope != null ? this.envelope : FlightEnvelope.forMob(this.mob, this.bird.settings().envelope());

        Vec3 velocity = bendVelocity(this.mob.getDeltaMovement(), toCarrot, envelope);
        this.faceLineOfFlight(velocity, toCarrot, envelope);
        boolean pivoting = this.pivotsBeforeThrusting(velocity, toCarrot);
        if (pivoting) {
            this.mob.setDeltaMovement(velocity);
            this.coast();
            return;
        }
        this.applyThrust(velocity, toCarrot, envelope, this.speedLimit);
    }

    private boolean pivotsBeforeThrusting(Vec3 velocity, Vec3 toCarrot) {
        boolean standingInTheAir = velocity.horizontalDistanceSqr() < MIN_HORIZONTAL_AIM_SQR
                && toCarrot.horizontalDistanceSqr() > MIN_HORIZONTAL_AIM_SQR;
        if (!standingInTheAir) {
            return false;
        }
        float wantedYaw = FlightMath.yawTowards(toCarrot.x, toCarrot.z);
        return Math.abs(Mth.degreesDifference(this.mob.getYRot(), wantedYaw)) > AIR_PIVOT_MIN_YAW_ERROR;
    }

    public double wingThrust() {
        return this.wingThrust;
    }

    public double fallPush() {
        return this.fallPush;
    }

    private void handOverVanillaInputs() {
        this.mob.setSpeed((float) this.mob.getDeltaMovement().horizontalDistance());
        this.mob.setXxa(0.0F);
        this.mob.setYya(0.0F);
        this.mob.setZza(0.0F);
    }

    private void coast() {
        this.wingThrust = 0.0;
        this.fallPush = 0.0;
    }

    private static Vec3 bendVelocity(Vec3 velocity, Vec3 toCarrot, FlightEnvelope envelope) {
        double speed = velocity.length();
        if (speed < MIN_DIRECTION_LENGTH) {
            return velocity;
        }
        Vec3 heading = velocity.scale(1.0 / speed);
        Vec3 wanted = toCarrot.normalize();
        double angle = Math.acos(Mth.clamp(heading.dot(wanted), -1.0, 1.0));
        if (angle < FlightMath.NEAR_ZERO) {
            return velocity;
        }
        Vec3 axis = heading.cross(wanted);

        if (axis.lengthSqr() < FlightMath.DEGENERATE_LEG_SQR) {
            axis = new Vec3(0.0, 1.0, 0.0);
        }
        double bent = Math.min(angle, envelope.turnPerTickAt(speed));
        return FlightMath.rotateAround(heading, axis.normalize(), bent).scale(speed);
    }

    private void faceLineOfFlight(Vec3 velocity, Vec3 toCarrot, FlightEnvelope envelope) {
        Vec3 along = velocity.horizontalDistanceSqr() > MIN_HORIZONTAL_AIM_SQR ? velocity : toCarrot;
        if (along.horizontalDistanceSqr() <= MIN_HORIZONTAL_AIM_SQR) {
            return;
        }
        float wantedYaw = FlightMath.yawTowards(along.x, along.z);
        float maxTurn = (float) (envelope.hoverTurnRate() * Mth.RAD_TO_DEG);
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), wantedYaw, maxTurn));
    }

    private void applyThrust(Vec3 velocity, Vec3 toCarrot, FlightEnvelope envelope, double speedLimit) {
        double speed = velocity.length();
        Vec3 lineOfFlight = speed >= MIN_DIRECTION_LENGTH ? velocity.scale(1.0 / speed) : this.noseDirection(toCarrot);
        double pitch = FlightMath.pitchOf(lineOfFlight);

        double wingsChase = envelope.maxHorizontalSpeed() * this.speedModifier;

        double fallChase = envelope.maxSpeedAt(pitch) * this.speedModifier;
        double push = envelope.fallPushToReach(Math.min(fallChase, speedLimit), speed, pitch);
        double speedWithPush = speed + push;
        this.fallPush = push;
        this.wingThrust = envelope.thrustToReach(Math.min(wingsChase, speedLimit), speedWithPush, pitch);

        if (this.wingThrust < 0.0) {
            this.wingThrust = Math.min(0.0, envelope.thrustToReach(speedLimit, speedWithPush, pitch));
        }
        Vec3 applied = lineOfFlight.scale(this.wingThrust + push + envelope.gravityAlong(pitch));
        this.mob.setDeltaMovement(velocity.add(applied));
    }

    private Vec3 noseDirection(Vec3 toCarrot) {
        Vec3 direction = toCarrot.normalize();
        return Vec3.directionFromRotation(0.0F, this.mob.getYRot())
                .scale(direction.horizontalDistance())
                .add(0.0, direction.y, 0.0);
    }
}
