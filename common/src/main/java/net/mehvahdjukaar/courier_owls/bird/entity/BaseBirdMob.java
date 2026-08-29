package net.mehvahdjukaar.courier_owls.bird.entity;

import net.mehvahdjukaar.courier_owls.bird.client.BirdAnimation;
import net.mehvahdjukaar.courier_owls.bird.controller.*;
import net.mehvahdjukaar.courier_owls.bird.navigator.BirdFlightNavigation;
import net.mehvahdjukaar.courier_owls.bird.navigator.BirdWalkNavigation;
import net.mehvahdjukaar.courier_owls.bird.navigator.FlightPlan;
import net.mehvahdjukaar.courier_owls.bird.envelope.FlightEnvelope;
import net.mehvahdjukaar.courier_owls.bird.trip.BirdTripOrchestrator;
import net.mehvahdjukaar.courier_owls.bird.trip.LocomotionMode;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class BaseBirdMob extends Animal implements FlyingAnimal {
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(
            BaseBirdMob.class, EntityDataSerializers.BYTE);

    private static final EntityDataAccessor<Float> BODY_PITCH = SynchedEntityData.defineId(
            BaseBirdMob.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> WING_THRUST = SynchedEntityData.defineId(
            BaseBirdMob.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> LANDING_PROGRESS = SynchedEntityData.defineId(
            BaseBirdMob.class, EntityDataSerializers.FLOAT);

    private static final double WALK_SPEED_MODIFIER = 0.7;

    private static final double MIN_LAUNCH_SPREAD_SQR = 1.0E-4;

    private static final double MAX_STANDING_LAUNCH_SPEED_SQR = 0.1 * 0.1;
    private static final int LOST_GROUND_FLUTTER_TICKS = 3;

    private final BirdFlightMoveControl flightControl;
    private final MoveControl walkControl;
    private final BirdWalkNavigation groundNavigation;
    private final BirdFlightNavigation flightNavigation;
    private final BirdTripOrchestrator tripOrchestrator;

    private boolean flightTakenOver;

    @Nullable
    private Vec3 heldPoint;

    private int lostGroundTicks;

    @Nullable
    private final BirdAnimation animation;

    private float launchYaw;

    protected BaseBirdMob(EntityType<? extends BaseBirdMob> entityType, Level level) {
        super(entityType, level);
        this.flightNavigation = (BirdFlightNavigation) this.navigation;
        this.flightControl = new BirdFlightMoveControl(this);
        this.walkControl = new MoveControl(this);
        this.groundNavigation = new BirdWalkNavigation(this, level);
        this.tripOrchestrator = new BirdTripOrchestrator(this, this.groundNavigation, this.flightNavigation);

        this.navigation = this.groundNavigation;
        this.moveControl = this.walkControl;
        this.lookControl = this.createLookControl();
        this.animation = level.isClientSide() ? new BirdAnimation() : null;
    }

    public boolean canMoveOnItsOwn() {
        return true;
    }

    public boolean isInterested() {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        builder.define(MODE, (byte) LocomotionMode.PERCHED.ordinal());
        builder.define(BODY_PITCH, -this.settings().gait().perchPitch);
        builder.define(WING_THRUST, 0.0F);
        builder.define(LANDING_PROGRESS, 0.0F);
    }

    public BirdAnimation animation() {
        return Objects.requireNonNull(this.animation, "no pose on this side");
    }

    public BirdSettings settings() {
        return BirdSettings.DEFAULTS;
    }

    public BirdTripOrchestrator trip() {
        return this.tripOrchestrator;
    }

    public BirdFlightNavigation flightNavigation() {
        return this.flightNavigation;
    }

    public boolean takeOverFlight() {
        if (this.getMode().isOnFoot()) {
            return false;
        }
        this.tripOrchestrator.cancel();
        this.flightNavigation.stop();
        if (this.getMode().isHanging()) {
            this.setMode(LocomotionMode.AIRBORNE);
        }
        this.flightTakenOver = true;
        return true;
    }

    public void steerTowards(Vec3 aim, FlightEnvelope envelope, double speedLimit) {
        this.flightControl.setFlightTarget(aim, speedLimit, envelope, 1.0);
    }

    public void releaseFlight() {
        this.flightTakenOver = false;
    }

    public void holdPosition(Vec3 point) {
        if (!this.isHovering()) {
            this.tripOrchestrator.cancel();
        }
        this.holdHere(point);
    }

    public void holdHere(Vec3 point) {
        this.heldPoint = point;

        this.getNavigation().stop();
        if (!this.isHovering()) {
            this.setMode(LocomotionMode.AIRBORNE);
            this.setMode(LocomotionMode.HOVERING);
        }
    }

    public void turnToFace(Vec3 point, float degreesPerTick) {
        float wanted = FlightMath.yawTowards(point.x - this.getX(), point.z - this.getZ());
        this.setYRot(Mth.approachDegrees(this.getYRot(), wanted, degreesPerTick));

        this.yBodyRot = this.getYRot();
    }

    public void releaseHold() {
        this.heldPoint = null;
        if (this.isHovering()) {
            this.setMode(LocomotionMode.FLUTTERING);
        }
    }

    public boolean isHovering() {
        return this.getMode() == LocomotionMode.HOVERING;
    }

    public boolean isDrivenByBehavior() {
        return this.flightTakenOver || this.isHovering();
    }

    public boolean isFlightTakenOver() {
        return this.flightTakenOver;
    }

    public FlightEnvelope activeEnvelope() {
        FlightPlan plan = this.navigation == this.flightNavigation ? this.flightNavigation.currentPlan() : null;
        return plan != null ? plan.envelope() : FlightEnvelope.forMob(this, this.settings().envelope());
    }

    public LocomotionMode getMode() {
        return LocomotionMode.byId(this.entityData.get(MODE));
    }

    public float getBodyPitch() {
        return this.entityData.get(BODY_PITCH);
    }

    public float getWingThrust() {
        return this.entityData.get(WING_THRUST);
    }

    public float getLandingProgress() {
        return this.entityData.get(LANDING_PROGRESS);
    }

    @Override
    public boolean isFlying() {
        return !this.isOnFoot();
    }

    public boolean isOnFoot() {
        return this.getMode().isOnFoot();
    }

    public boolean isFluttering() {
        return getMode() == LocomotionMode.FLUTTERING;
    }

    public boolean canMoveByWalking() {
        return this.getMainHandItem().isEmpty();
    }

    public boolean canMoveByFlying() {
        return true;
    }

    public boolean isHoldingForLaunch() {
        return this.getMode() == LocomotionMode.LAUNCHING;
    }

    public boolean isHeadingCommanded() {
        return this.getMode().isHeadingCommanded();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.tickLocomotion();

        this.tripOrchestrator.tick();

        this.tickBodyPitch();
        this.entityData.set(WING_THRUST, (float) this.currentWingThrust());
    }

    private void tickBodyPitch() {
        GaitSettings gait = this.settings().gait();
        this.entityData.set(LANDING_PROGRESS, this.wantedLandingProgress(gait));
        this.entityData.set(BODY_PITCH,
                Mth.approachDegrees(this.getBodyPitch(), this.wantedBodyPitch(gait), gait.maxPitchPerTick));
    }

    private float wantedLandingProgress(GaitSettings gait) {
        return switch (this.getMode()) {
            case FLUTTERING, HOVERING -> 1.0F;
            case AIRBORNE -> {
                if (this.tripOrchestrator.arrivesInMidair()) {
                    yield 0.0F;
                }
                FlightPlan plan = this.flightNavigation.currentPlan();
                yield plan == null ? 0.0F
                        : (float) Mth.clamp(1.0 - plan.cursor().remaining() / gait.landingPoseDistance, 0.0, 1.0);
            }
            default -> 0.0F;
        };
    }

    private float wantedBodyPitch(GaitSettings gait) {
        Vec3 velocity = this.getDeltaMovement();
        float flare = -gait.flutterPitch;
        float slope = Mth.clamp(FlightMath.xRotOf(velocity), -gait.maxPitch, gait.maxPitch);
        return switch (this.getMode()) {
            case AIRBORNE -> Mth.lerp(this.getLandingProgress(), slope, flare);
            case FLUTTERING, HOVERING -> flare;
            default -> -gait.perchPitch;
        };
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new BirdFlightNavigation(this, level);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new BirdBodyRotationControl(this);
    }

    protected LookControl createLookControl() {
        return new BirdLookControl(this);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return this.isOnFoot() ? super.getMovementEmission() : MovementEmission.EVENTS;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return this.isFlying();
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.5F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }

    @Override
    protected void updateWalkAnimation(float distance) {
        super.updateWalkAnimation(this.isOnFoot() ? distance : 0.0F);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double range = this.getType().clientTrackingRange() * 16 * Entity.getViewScale();
        return distanceSqr < range * range;
    }

    @Override
    public void tick() {
        if (animation != null) {
            animation.tickBeforeMove(this);
        }
        super.tick();
        if (animation != null) {
            animation.tickAfterMove(this.getBodyPitch());
            return;
        }

        if (this.isImmobile() || !this.isEffectiveAi()) {
            this.setNoGravity(false);
        }
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        if (!FlightMath.isFinite(this.getDeltaMovement())) {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalStateException("bird set to a non-finite position (" + x + ", " + y
                    + ", " + z + "), velocity " + this.getDeltaMovement());
        }
        super.setPos(x, y, z);
    }

    public boolean flyPath(@Nullable Path path, double speed) {
        if (!this.canMoveByFlying()) {
            return false;
        }
        boolean accepted = this.startPath(this.flightNavigation, path, speed);

        if (!accepted) {
            if (this.isOnFoot()) {
                this.getNavigation().stop();
            }
        } else if (this.standingStill() || this.isOnFoot()) {
            this.setYRot(this.yBodyRot);
            this.launchYaw = this.getYRot();

            this.setMode(LocomotionMode.LAUNCHING);

            this.flightNavigation.primeLaunchHold();
        } else if (this.getMode().isHanging()) {
            this.setMode(LocomotionMode.AIRBORNE);
        }
        return accepted;
    }

    public boolean walkPath(@Nullable Path path) {
        boolean accepted = this.startPath(this.groundNavigation, path, WALK_SPEED_MODIFIER);

        if (accepted && this.getMode() == LocomotionMode.LAUNCHING) {
            this.setMode(LocomotionMode.PERCHED);
        }
        return accepted;
    }

    private boolean startPath(PathNavigation navigation, @Nullable Path path, double speed) {
        if (path != null && path == navigation.getPath() && !navigation.isDone()) {
            navigation.setSpeedModifier(speed);
            return true;
        }

        if (path == null) {
            return false;
        }

        this.tripOrchestrator.onLegStarted();

        this.flightTakenOver = false;

        navigation.stop();
        return navigation.moveTo(path, speed);
    }

    private void setMode(LocomotionMode mode) {
        if (this.getMode() == mode) {
            return;
        }
        this.lostGroundTicks = 0;

        this.entityData.set(MODE, (byte) mode.ordinal());
        if (mode == LocomotionMode.FLUTTERING) {
            return;
        }
        boolean walking = mode == LocomotionMode.PERCHED;
        if (walking == (this.navigation == this.groundNavigation)) {
            return;
        }
        this.navigation.stop();
        this.navigation = walking ? this.groundNavigation : this.flightNavigation;
        this.moveControl = walking ? this.walkControl : this.flightControl;
    }

    private void tickLocomotion() {
        switch (this.getMode()) {
            case AIRBORNE -> this.tickAirborne();
            case FLUTTERING -> this.tickFluttering();
            case PERCHED -> this.tickGrounded();
            case LAUNCHING -> this.tickLaunching();
            case HOVERING -> this.tickHovering();
        }

        this.setNoGravity(this.getMode().hasNoVanillaGravity());
    }

    private void tickAirborne() {
        if (this.flightTakenOver || !this.getNavigation().isDone()) {
            return;
        }
        if (!this.tripOrchestrator.arrivesInMidair()) {
            this.enterFlutter();
        }
    }

    private void tickHovering() {
        if (this.heldPoint == null) {
            return;
        }
        GaitSettings gait = this.settings().gait();
        Vec3 wanted = this.heldPoint.subtract(this.position()).scale(gait.hoverCloseFraction);
        double speed = wanted.length();
        this.setDeltaMovement(speed > gait.hoverMaxSpeed ? wanted.scale(gait.hoverMaxSpeed / speed) : wanted);
        this.beatWings();
    }

    private void enterFlutter() {
        this.setMode(LocomotionMode.FLUTTERING);
        this.tickFluttering();
    }

    private void tickFluttering() {
        if (this.hasFooting()) {
            this.setMode(LocomotionMode.PERCHED);
            return;
        }
        this.beatWings();
    }

    private void beatWings() {
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.y < 0.0) {
            this.setDeltaMovement(velocity.add(0.0, this.flutterThrust(), 0.0));
        }
    }

    private void tickGrounded() {
        if (this.hasFooting()) {
            this.lostGroundTicks = 0;
            return;
        }
        if (++this.lostGroundTicks >= LOST_GROUND_FLUTTER_TICKS) {
            this.setMode(LocomotionMode.FLUTTERING);
        }
    }

    private void tickLaunching() {
        if (this.hasFooting()) {
            this.lostGroundTicks = 0;
        } else if (++this.lostGroundTicks >= LOST_GROUND_FLUTTER_TICKS) {
            this.setMode(LocomotionMode.AIRBORNE);
            return;
        }

        if (this.getNavigation().isDone()) {
            this.setMode(LocomotionMode.PERCHED);
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        this.setDeltaMovement(0.0, velocity.y, 0.0);

        this.launchYaw = this.launchTargetYaw();
        GaitSettings gait = this.settings().gait();
        float turned = Mth.approachDegrees(this.getYRot(), this.launchYaw, gait.launchTurnPerTick);
        this.setYRot(turned);
        if (Math.abs(Mth.degreesDifference(turned, this.launchYaw)) <= gait.launchYawTolerance) {
            this.setMode(LocomotionMode.AIRBORNE);
        }
    }

    private boolean standingStill() {
        return this.hasFooting()
                && this.getDeltaMovement().horizontalDistanceSqr() < MAX_STANDING_LAUNCH_SPEED_SQR;
    }

    private boolean hasFooting() {
        return this.onGround() || this.isInWater();
    }

    @Override
    public boolean isAffectedByFluids() {
        return !this.getMode().hasNoVanillaGravity();
    }

    @Override
    public boolean isPushedByFluid() {
        return !this.getMode().hasNoVanillaGravity();
    }

    private float launchTargetYaw() {
        MoveControl moveControl = this.getMoveControl();
        double dx = moveControl.getWantedX() - this.getX();
        double dz = moveControl.getWantedZ() - this.getZ();
        return dx * dx + dz * dz < MIN_LAUNCH_SPREAD_SQR ? this.getYRot() : FlightMath.yawTowards(dx, dz);
    }

    private double flutterThrust() {
        return this.getAttributeValue(Attributes.GRAVITY) * this.settings().gait().flutterGravityFraction;
    }

    private double currentWingThrust() {
        LocomotionMode mode = this.getMode();
        if (mode == LocomotionMode.FLUTTERING || mode == LocomotionMode.HOVERING) {
            return this.flutterThrust();
        }
        return mode == LocomotionMode.AIRBORNE ? this.flightControl.wingThrust() : 0.0;
    }
}
