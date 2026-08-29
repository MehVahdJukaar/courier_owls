package net.mehvahdjukaar.courier_owls.owls.swoop;

import net.mehvahdjukaar.courier_owls.bird.trip.ArrivalStyle;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.CorridorRaycaster;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.NodePlacementUtil;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.ArrivalHeading;
import net.mehvahdjukaar.courier_owls.bird.envelope.FlightEnvelope;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class PrepareSwoop extends Behavior<OwlEntity> {
    private static final double MAX_UP = 4.0;

    private static final float[] BEARING_OFFSETS = {0, 20, -20, 45, -45, 70, -70, 90, -90};

    private static final double[] STANDOFF_OUTS = {2.0, 3.0, 5.0, 8.0};

    private static final double CLIMB_PER_RUN = 1.0;

    private static final double DIVE_PER_ALIGN_STEP = 3.0;

    private static final int MAX_BEARING_RETRIES = 3;

    private static final double ARRIVE_DISTANCE = 2.0;
    private static final int ARRIVAL_PATIENCE_TICKS = 20;

    private static final double REPLAN_DISTANCE = 2.0;

    private static final double DIVE_MAX_OUT = 12.0;
    private static final double DIVE_MIN_HEIGHT = 1.0;
    private static final double DIVE_MIN_SLOPE = 0.35;

    private static final double PREFERRED_DIVE_SLOPE = 0.58;

    private static final double TURN_ON_HEADROOM = 1.1;

    private static final double MIN_DIVE_SPEED_FRACTION = 1.0;

    private static final double MOVING_SPEED = 0.1;

    private static final int MAX_MEASURED_PROBES = 24;
    private static final double MIN_BEARING_SPREAD_SQR = 1.0E-4;
    private static final int TIMEOUT = 600;

    private final float speed;

    private Vec3 strike = Vec3.ZERO;
    private Vec3 standoff = Vec3.ZERO;
    private float baseBearing;
    private FlightEnvelope envelope;

    private final List<Candidate> rankedStandoffs = new ArrayList<>();
    private int measuredProbes;
    private int standoffIndex;
    private int bearingRetries;
    private int noDiveTicks;
    private boolean gaveUp;
    private int bearingIndex;
    private boolean handedOff;

    public PrepareSwoop(float speed) {
        super(Map.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                OwlMod.SWOOP_GO.get(), MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), TIMEOUT);
        this.speed = speed;
    }

    @Override
    protected void start(ServerLevel level, OwlEntity owl, long gameTime) {
        this.handedOff = false;
        this.gaveUp = false;
        this.bearingIndex = 0;
        this.bearingRetries = 0;
        this.noDiveTicks = 0;
        this.envelope = FlightEnvelope.forMob(owl, owl.settings().envelope());
        if (!this.plan(owl)) {
            this.giveUp();
        }
    }

    private LivingEntity target(OwlEntity owl) {
        return owl.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
    }

    private void aimStandoff(OwlEntity owl, Vec3 standoff) {
        this.standoff = standoff;
        int alignSteps = alignStepsFor(standoff.subtract(this.strike).horizontalDistance());
        owl.trip().planArrival(BlockPos.containing(standoff), ArrivalStyle.IN_FLIGHT,
                ArrivalHeading.required(this.strike.subtract(standoff), alignSteps));
    }

    private static int alignStepsFor(double out) {
        return Mth.clamp((int) Math.ceil(out / DIVE_PER_ALIGN_STEP), 1, ArrivalHeading.MAX_RUN);
    }

    private boolean plan(OwlEntity owl) {
        this.strike = SwoopGeometry.strikePoint(owl, this.target(owl));

        Vec3 toOwl = owl.position().subtract(this.strike);
        this.baseBearing = toOwl.horizontalDistanceSqr() < MIN_BEARING_SPREAD_SQR
                ? owl.getYRot() : FlightMath.yawTowards(toOwl.x, toOwl.z);
        double owlOut = toOwl.horizontalDistance();

        this.rankedStandoffs.clear();
        this.measuredProbes = 0;
        List<Pending> pending = new ArrayList<>();
        for (int bearingIndex = 0; bearingIndex < BEARING_OFFSETS.length; bearingIndex++) {
            for (int outIndex = 0; outIndex < STANDOFF_OUTS.length; outIndex++) {
                this.gather(owl, bearingIndex, outIndex, owlOut, toOwl.y, pending);
            }
        }
        pending.sort(Comparator.comparingDouble(Pending::ticks));
        boolean[] spotTaken = new boolean[BEARING_OFFSETS.length * STANDOFF_OUTS.length];
        for (Pending candidate : pending) {
            if (this.measuredProbes >= MAX_MEASURED_PROBES) {
                break;
            }
            if (!spotTaken[candidate.spot()] && this.measure(owl, candidate)) {
                spotTaken[candidate.spot()] = true;
            }
        }
        if (this.rankedStandoffs.isEmpty()) {
            return false;
        }
        this.standoffIndex = 0;
        this.fly(owl, this.rankedStandoffs.get(0));
        return true;
    }

    private double reachableHeight(double bearingOffset, double out, double owlOut, double owlUp) {
        double leg = Math.sqrt(Math.max(0.0, owlOut * owlOut + out * out
                - 2.0 * owlOut * out * Math.cos(Math.toRadians(bearingOffset))));
        double climb = Math.max(0.0, leg - alignStepsFor(out)) * CLIMB_PER_RUN;
        return Math.min(MAX_UP, owlUp + climb);
    }

    private static double minDiveHeightAt(double out) {
        return Math.max(DIVE_MIN_HEIGHT, out * DIVE_MIN_SLOPE);
    }

    private boolean divable(double out, double up) {
        return out <= DIVE_MAX_OUT
                && up >= minDiveHeightAt(out)
                && SwoopGeometry.diveSpeedLimit(this.envelope, out, up)
                >= this.envelope.maxHorizontalSpeed() * MIN_DIVE_SPEED_FRACTION;
    }

    private void gather(OwlEntity owl, int bearingIndex, int outIndex, double owlOut, double owlUp,
                        List<Pending> pending) {
        double out = STANDOFF_OUTS[outIndex];
        float bearingOffset = BEARING_OFFSETS[bearingIndex];
        Vec3 outward = Vec3.directionFromRotation(0.0F, this.baseBearing + bearingOffset);
        Vec3 base = this.strike.add(outward.scale(out));
        double lowest = minDiveHeightAt(out);
        double highest = this.reachableHeight(bearingOffset, out, owlOut, owlUp);
        if (highest < lowest) {
            return;
        }
        double preferred = Mth.clamp(out * PREFERRED_DIVE_SLOPE, lowest, highest);

        double ticks = this.ticksToStrike(owl, base.add(0.0, preferred, 0.0));
        for (double up = preferred; up >= lowest - 1.0E-6; up -= 1.0) {
            this.offerHeight(owl, bearingIndex, outIndex, base, up, ticks, pending);
        }
        for (double up = preferred + 1.0; up <= highest + 1.0E-6; up += 1.0) {
            this.offerHeight(owl, bearingIndex, outIndex, base, up, ticks, pending);
        }
    }

    private void offerHeight(OwlEntity owl, int bearingIndex, int outIndex, Vec3 base, double up,
                             double ticks, List<Pending> pending) {
        Vec3 at = onTheGrid(owl, base.add(0.0, up, 0.0));
        if (!this.divable(at.subtract(this.strike).horizontalDistance(), at.y - this.strike.y)) {
            return;
        }
        pending.add(new Pending(bearingIndex, bearingIndex * STANDOFF_OUTS.length + outIndex, at, ticks));
    }

    private boolean measure(OwlEntity owl, Pending candidate) {
        this.measuredProbes++;
        if (!CorridorRaycaster.fitsAt(owl, candidate.at())
                || !SwoopGeometry.diveClear(owl, candidate.at(), this.target(owl))) {
            return false;
        }
        this.rankedStandoffs.add(new Candidate(candidate.bearingIndex(), candidate.at()));
        return true;
    }

    private record Pending(int bearingIndex, int spot, Vec3 at, double ticks) {
    }

    private static Vec3 onTheGrid(OwlEntity owl, Vec3 at) {
        return NodePlacementUtil.flownPointOf(BlockPos.containing(at), owl);
    }

    private void fly(OwlEntity owl, Candidate candidate) {
        this.bearingIndex = candidate.bearingIndex();
        this.aimStandoff(owl, candidate.standoff());
    }

    private record Candidate(int bearingIndex, Vec3 standoff) {
    }

    private double ticksToStrike(OwlEntity owl, Vec3 standoff) {
        Vec3 velocity = owl.getDeltaMovement();
        Vec3 heading = velocity.horizontalDistance() >= MOVING_SPEED
                ? SwoopGeometry.levelHeading(velocity) : Vec3.directionFromRotation(0.0F, owl.getYRot());
        Vec3 toStandoff = standoff.subtract(owl.position());
        Vec3 leg = SwoopGeometry.levelHeading(toStandoff);
        Vec3 dive = this.strike.subtract(standoff);
        double cruise = this.envelope.maxHorizontalSpeed();
        double perTick = Math.toDegrees(this.envelope.turnPerTickAt(cruise));
        double diveSpeed = SwoopGeometry.diveSpeedLimit(this.envelope, dive.horizontalDistance(), -dive.y);
        return (degreesBetween(heading, leg) + degreesBetween(leg, SwoopGeometry.levelHeading(dive))) / perTick
                + toStandoff.length() / cruise
                + dive.length() / Math.max(diveSpeed, 1.0E-3);
    }

    private static double degreesBetween(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Mth.clamp(a.dot(b), -1.0, 1.0)));
    }

    private boolean nextBearing(OwlEntity owl) {
        boolean more = this.standoffIndex + 1 < this.rankedStandoffs.size();
        if (this.bearingRetries >= MAX_BEARING_RETRIES || !more) {
            return false;
        }
        this.bearingRetries++;
        this.fly(owl, this.rankedStandoffs.get(++this.standoffIndex));
        return true;
    }

    private void giveUp() {
        this.gaveUp = true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, OwlEntity owl, long gameTime) {
        return !this.handedOff && !this.gaveUp && owl.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected void tick(ServerLevel level, OwlEntity owl, long gameTime) {
        Brain<?> brain = owl.getBrain();
        LivingEntity target = this.target(owl);

        BehaviorUtils.lookAtEntity(owl, target);
        if (SwoopGeometry.strikePoint(owl, target).distanceTo(this.strike) > REPLAN_DISTANCE
                && !this.plan(owl)) {
            this.giveUp();
            return;
        }

        if (brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            if (!this.nextBearing(owl)) {
                this.giveUp();
                return;
            }
        }

        if (this.linedUp(owl)) {
            this.handOff(owl);
            return;
        }

        boolean arrived = !owl.trip().isTraveling()
                && owl.position().distanceTo(this.standoff) <= this.arriveDistance();
        if (arrived && !owl.isOnFoot()) {
            if (++this.noDiveTicks < ARRIVAL_PATIENCE_TICKS) {
                return;
            }
            this.noDiveTicks = 0;
            if (!this.nextBearing(owl)) {
                this.giveUp();
                return;
            }
        } else {
            this.noDiveTicks = 0;
        }
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(this.standoff), this.speed, 0));
    }

    private double arriveDistance() {
        return Math.min(ARRIVE_DISTANCE, this.standoff.subtract(this.strike).horizontalDistance() * 0.5);
    }

    private boolean canDiveFromHere(OwlEntity owl) {
        if (owl.isOnFoot()) {
            return false;
        }

        Vec3 toStrike = this.strike.subtract(owl.position());
        return this.divable(toStrike.horizontalDistance(), -toStrike.y)
                && SwoopGeometry.diveClear(owl, owl.position(), this.target(owl));
    }

    private boolean linedUp(OwlEntity owl) {
        return this.canDiveFromHere(owl) && this.canTurnOntoDive(owl);
    }

    private boolean canTurnOntoDive(OwlEntity owl) {
        Vec3 velocity = owl.getDeltaMovement();
        return velocity.length() * TURN_ON_HEADROOM
                <= SwoopGeometry.turnOnSpeedLimit(this.envelope, velocity, this.strike.subtract(owl.position()));
    }

    private void handOff(OwlEntity owl) {
        Brain<?> brain = owl.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.setMemory(OwlMod.SWOOP_GO.get(), Unit.INSTANCE);
        this.handedOff = true;
    }

    @Override
    protected void stop(ServerLevel level, OwlEntity owl, long gameTime) {
        if (!this.handedOff) {
            owl.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
}
