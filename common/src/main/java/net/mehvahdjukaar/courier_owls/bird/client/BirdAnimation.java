package net.mehvahdjukaar.courier_owls.bird.client;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.envelope.FlightEnvelope;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class BirdAnimation {
    private static final float WING_SPREAD_PER_TICK = 0.25F;

    private static final float TILT_FLIP_THRESHOLD = 0.8F;

    private boolean settled;

    private float bodyPitch;
    private float bodyPitchO;

    private float flapPhase;
    private float flapPhaseO;
    private float wingSpread;
    private float wingSpreadO;

    private float glide;
    private float glideO;

    private float brake;
    private float brakeO;

    private float burst;
    private float burstO;

    private float landingProgress;
    private float landingProgressO;

    private float bank;
    private float bankO;

    private float headTilt;
    private float headTiltO;
    private boolean tiltLeft;

    private int tiltHold;

    private float sit;
    private float sitO;

    private float carry;
    private float carryO;

    private WingDrive drive = new WingDrive(0.0, 0.0, true);

    public void tickBeforeMove(BaseBirdMob mob) {
        this.tick(FlightEnvelope.forMob(mob, mob.settings().envelope()), PoseInputs.of(mob), mob.getRandom());
    }

    public void tick(FlightEnvelope envelope, PoseInputs in, RandomSource random) {
        this.snapPreviousToCurrent();
        this.tickHeadTilt(in.interested(), random);

        this.sit = in.sitting() ? 1.0F : 0.0F;
        this.sitO = this.sit;
        this.carry = Mth.approach(this.carry, in.carrying() ? 1.0F : 0.0F,
                this.step(BirdAnimationConfig.carryPerTick));

        double wingBrakeAccel = envelope.wingBrakeAccel();
        float braking = wingBrakeAccel <= 0.0 ? 0.0F
                : (float) Mth.clamp(-in.wingThrust() / wingBrakeAccel, 0.0, 1.0);
        this.brake = Mth.approach(this.brake, braking, this.step(BirdAnimationConfig.brakePerTick));

        double sustained = envelope.levelThrustAccel();
        double headroom = envelope.peakTakeOffAccel() - sustained;
        float bursting = headroom <= 0.0 ? 0.0F
                : (float) Mth.clamp((in.wingThrust() - sustained) / headroom, 0.0, 1.0);
        this.burst = Mth.approach(this.burst, bursting, this.step(BirdAnimationConfig.burstPerTick));

        this.landingProgress = Mth.approach(this.landingProgress, in.landingProgress(),
                this.step(BirdAnimationConfig.landingPosePerTick));
        WingDrive drive = WingDrive.live(in.wingThrust(), in.bodyPitch(), in.onFoot());
        this.tickWings(envelope, drive);
        this.tickBank(envelope, in.yawRate(), in.horizontalSpeed(), drive.grounded());
    }

    public void tickAfterMove(float bodyPitch) {
        this.bodyPitch = bodyPitch;
        if (!this.settled) {
            this.settled = true;

            this.snapPreviousToCurrent();
        }
    }

    private void snapPreviousToCurrent() {
        this.bodyPitchO = this.bodyPitch;
        this.flapPhaseO = this.flapPhase;
        this.wingSpreadO = this.wingSpread;
        this.glideO = this.glide;
        this.bankO = this.bank;
        this.brakeO = this.brake;
        this.burstO = this.burst;
        this.landingProgressO = this.landingProgress;
        this.headTiltO = this.headTilt;
        this.sitO = this.sit;
        this.carryO = this.carry;
    }

    private void tickHeadTilt(boolean interested, RandomSource random) {
        if (!interested) {
            this.headTilt = Mth.approach(this.headTilt, 0.0F, BirdAnimationConfig.headTiltPerTick);
            this.tiltHold = 0;
            return;
        }
        boolean allTheWayOver = Math.abs(this.headTilt) > TILT_FLIP_THRESHOLD;
        if (allTheWayOver) {
            if (this.tiltHold <= 0) {
                this.tiltHold = Mth.randomBetweenInclusive(random,
                        BirdAnimationConfig.headTiltHoldMinTicks, BirdAnimationConfig.headTiltHoldMaxTicks);
            } else if (--this.tiltHold == 0) {
                this.tiltLeft = !this.tiltLeft;
            }
        }
        this.headTilt = Mth.approach(this.headTilt, this.tiltLeft ? -1.0F : 1.0F,
                BirdAnimationConfig.headTiltPerTick);
    }

    private float step(float perTick) {
        return this.settled ? perTick : Float.MAX_VALUE;
    }

    private void tickWings(FlightEnvelope envelope, WingDrive drive) {
        this.drive = drive;
        double effort = wingEffort(envelope, drive.thrust());
        boolean gliding = !drive.grounded() && effort < BirdAnimationConfig.glideEffortThreshold;

        float glideStep = Mth.lerp(Math.max(this.brake, this.landingProgress),
                BirdAnimationConfig.glidePerTick, BirdAnimationConfig.brakePerTick);
        this.glide = Mth.approach(this.glide, gliding ? 1.0F : 0.0F, this.step(glideStep));

        double rate = Mth.lerp(effort, BirdAnimationConfig.minFlapRate, BirdAnimationConfig.maxFlapRate)
                * Mth.lerp(this.brake, 1.0F, BirdAnimationConfig.brakeFlapRateScale)
                * Mth.lerp(this.burst, 1.0F, BirdAnimationConfig.burstFlapRateScale);
        this.flapPhase = (float) ((this.flapPhase + rate * (1.0F - this.glide)) % 1.0);
        this.wingSpread = Mth.approach(this.wingSpread, drive.grounded() ? 0.0F : 1.0F, this.step(WING_SPREAD_PER_TICK));
    }

    private void tickBank(FlightEnvelope envelope, double yawRate, double speed, boolean grounded) {
        float target = 0.0F;
        if (!grounded) {
            double angle = envelope.bankAngleFor(speed, yawRate) * Mth.RAD_TO_DEG;
            target = (float) Mth.clamp(angle, -BirdAnimationConfig.maxBankAngle, BirdAnimationConfig.maxBankAngle);
        }
        this.bank = Mth.approach(this.bank, target, this.step(BirdAnimationConfig.maxBankPerTick));
    }

    public float sit(float partialTick) {
        return Mth.lerp(partialTick, this.sitO, this.sit);
    }

    public float carry(float partialTick) {
        return Mth.lerp(partialTick, this.carryO, this.carry);
    }

    public float headTilt(float partialTick) {
        return Mth.lerp(partialTick, this.headTiltO, this.headTilt) * BirdAnimationConfig.headTiltDegr;
    }

    public float bank(float partialTick) {
        return Mth.lerp(partialTick, this.bankO, this.bank);
    }

    public float bodyPitch(float partialTick) {
        return Mth.lerp(partialTick, this.bodyPitchO, this.bodyPitch);
    }

    public float flapPhase(float partialTick) {
        float to = this.flapPhase < this.flapPhaseO ? this.flapPhase + 1.0F : this.flapPhase;
        return Mth.lerp(partialTick, this.flapPhaseO, to);
    }

    public float wingSpread(float partialTick) {
        return Mth.lerp(partialTick, this.wingSpreadO, this.wingSpread);
    }

    public WingDrive drive() {
        return this.drive;
    }

    public float glide(float partialTick) {
        return Mth.lerp(partialTick, this.glideO, this.glide);
    }

    public float brake(float partialTick) {
        return Mth.lerp(partialTick, this.brakeO, this.brake);
    }

    public float burst(float partialTick) {
        return Mth.lerp(partialTick, this.burstO, this.burst);
    }

    public float strokeBob(float partialTick) {
        float beating = this.wingSpread(partialTick) * (1.0F - this.glide(partialTick));
        if (beating <= 0.0F) {
            return 0.0F;
        }
        float phase = this.flapPhase(partialTick) * Mth.TWO_PI
                + BirdAnimationConfig.strokeBobPhaseDegr * Mth.DEG_TO_RAD;
        return Mth.sin(phase) * BirdAnimationConfig.strokeBobBlocks * beating
                * this.strokeScale(partialTick);
    }

    public float strokeScale(float partialTick) {
        return Mth.lerp(this.brake(partialTick), 1.0F, BirdAnimationConfig.brakeAmplitudeScale)
                * Mth.lerp(this.burst(partialTick), 1.0F, BirdAnimationConfig.burstAmplitudeScale);
    }

    public float strokeTilt(float partialTick) {
        return BirdAnimationConfig.burstStrokeTiltDegr * this.burst(partialTick)
                - BirdAnimationConfig.brakeStrokeTiltDegr * this.brake(partialTick);
    }

    public float landingProgress(float partialTick) {
        return Mth.lerp(partialTick, this.landingProgressO, this.landingProgress);
    }

    private static double wingEffort(FlightEnvelope envelope, double thrust) {
        double sustained = envelope.levelThrustAccel();
        return sustained <= 1.0E-9 ? 0.0 : Mth.clamp(thrust / sustained, 0.0, 1.0);
    }
}
