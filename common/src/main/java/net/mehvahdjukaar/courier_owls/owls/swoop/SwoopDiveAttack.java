package net.mehvahdjukaar.courier_owls.owls.swoop;

import net.mehvahdjukaar.courier_owls.bird.envelope.FlightEnvelope;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public class SwoopDiveAttack extends Behavior<OwlEntity> {
    private static final double TALON_REACH = 0.5;

    private static final double MIN_PULL_UP_DISTANCE = 1.0;

    private static final double WIDE_PASS_DISTANCE = 4.0;

    private static final double CLIMB_ALONG = 4.0;
    private static final double CLIMB_UP = 1.5;

    private static final int CLIMB_OUT_TICKS = 15;
    private static final int TIMEOUT = 160;

    private LivingEntity target;
    private FlightEnvelope envelope;
    private Vec3 aim = Vec3.ZERO;
    private boolean pullingUp;
    private int climbTicks;
    private boolean struckTarget;

    public SwoopDiveAttack() {
        super(Map.of(
                OwlMod.SWOOP_GO.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT), TIMEOUT);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, OwlEntity owl) {
        if (owl.isOnFoot()) {
            owl.getBrain().eraseMemory(OwlMod.SWOOP_GO.get());
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel level, OwlEntity owl, long gameTime) {
        this.target = owl.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        this.envelope = FlightEnvelope.forMob(owl, owl.settings().envelope());
        this.pullingUp = false;
        this.climbTicks = 0;
        this.struckTarget = false;

        if (!owl.takeOverFlight()) {
            return;
        }

        this.aim = SwoopGeometry.strikePoint(owl, this.target);
        this.steerAtStrike(owl);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, OwlEntity owl, long gameTime) {
        Brain<?> brain = owl.getBrain();
        return this.climbTicks < CLIMB_OUT_TICKS && owl.isFlightTakenOver()
                && this.noOtherTargetOrdered(brain) && brain.hasMemoryValue(OwlMod.SWOOP_GO.get());
    }

    @Override
    protected void tick(ServerLevel level, OwlEntity owl, long gameTime) {
        BehaviorUtils.lookAtEntity(owl, this.target);
        Vec3 strike = SwoopGeometry.strikePoint(owl, this.target);

        Vec3 toStrike = strike.subtract(owl.position());
        boolean nearby = toStrike.horizontalDistance() <= WIDE_PASS_DISTANCE;
        boolean passedWide = nearby && SwoopGeometry.levelHeading(toStrike).dot(SwoopGeometry.levelHeading(owl.getDeltaMovement())) < 0.0;
        if (!this.pullingUp && (toStrike.length() <= this.pullUpDistance(owl) || passedWide)) {
            this.pullingUp = true;
        }
        if (this.pullingUp) {
            this.tickPullUp(owl, strike);

            owl.steerTowards(this.aim, this.envelope, Double.MAX_VALUE);
        } else {
            this.aim = strike;
            this.steerAtStrike(owl);
        }
    }

    private void steerAtStrike(OwlEntity owl) {
        Vec3 toStrike = this.aim.subtract(owl.position());
        double limit = Math.min(
                SwoopGeometry.diveSpeedLimit(this.envelope, toStrike.horizontalDistance(), -toStrike.y),
                SwoopGeometry.turnOnSpeedLimit(this.envelope, owl.getDeltaMovement(), toStrike));
        owl.steerTowards(this.aim, this.envelope, limit);
    }

    private double pullUpDistance(OwlEntity owl) {
        Vec3 velocity = owl.getDeltaMovement();
        double speed = velocity.length();
        if (speed <= 1.0E-6) {
            return MIN_PULL_UP_DISTANCE;
        }
        double diveSin = Math.max(0.0, -velocity.y / speed);
        return Math.max(MIN_PULL_UP_DISTANCE, this.envelope.arcRadiusAt(speed) * diveSin);
    }

    private void tickPullUp(OwlEntity owl, Vec3 strike) {
        Vec3 along = SwoopGeometry.levelHeading(owl.getDeltaMovement());
        if (along.equals(Vec3.ZERO)) {
            along = Vec3.directionFromRotation(0.0F, owl.getYRot());
        }
        this.aim = owl.position().add(along.scale(CLIMB_ALONG)).add(0.0, CLIMB_UP, 0.0);
        if (!this.struckTarget) {
            this.strikeNow(owl);
        }
        boolean pastStrike = strike.subtract(owl.position()).dot(along) < 0.0;
        if (this.struckTarget || pastStrike) {
            this.climbTicks++;
        }
    }

    private void strikeNow(OwlEntity owl) {
        AABB talons = owl.getBoundingBox().inflate(TALON_REACH);
        List<LivingEntity> hits = owl.level().getEntitiesOfClass(LivingEntity.class, talons,
                other -> other != owl && other.isAlive());
        if (hits.isEmpty()) {
            return;
        }
        boolean canHurt = owl.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE);
        if (canHurt && owl.level() instanceof ServerLevel serverLevel) {
            hits.forEach(hit -> owl.doHurtTarget(serverLevel, hit));
        }
        this.struckTarget = hits.contains(this.target);
    }

    private boolean noOtherTargetOrdered(Brain<?> brain) {
        LivingEntity ordered = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        return ordered == null || ordered == this.target;
    }

    @Override
    protected void stop(ServerLevel level, OwlEntity owl, long gameTime) {
        owl.releaseFlight();
        owl.getBrain().eraseMemory(OwlMod.SWOOP_GO.get());
        this.target = null;
    }
}
