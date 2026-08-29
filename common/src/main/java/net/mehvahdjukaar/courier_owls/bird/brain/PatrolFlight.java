package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.bird.trip.ArrivalStyle;
import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.entity.TameableBird;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.CorridorRaycaster;
import net.mehvahdjukaar.courier_owls.bird.envelope.FlightEnvelope;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.function.Function;

public class PatrolFlight extends Behavior<BaseBirdMob> {
    private enum Phase {
        LAUNCH,

        CRUISE,

        SETTLE
    }

    private static final double LAUNCH_OUT = 10.0;
    private static final double LAUNCH_UP = 7.0;

    private static final double[] LAUNCH_DEGREES = {0, 45, -45, 90, -90, 135, -135, 180};

    private static final int LAUNCH_BUDGET_TICKS = 200;
    private static final double LAUNCH_HANDOVER = 2.0;

    private static final double CARROT_LEAD = 12.0;

    private static final double MIN_HEIGHT = 6.0;
    private static final double MAX_HEIGHT = 14.0;
    private static final double HEIGHT_PERIOD = 700.0;

    private static final double HEIGHT_SOFTNESS = 9.0;

    private static final double MAX_SLOPE = 0.5;

    private static final double WANDER_SLOW_PERIOD = 520.0;
    private static final double WANDER_FAST_PERIOD = 233.0;
    private static final double WANDER_SLOW_BANK = 0.22;
    private static final double WANDER_FAST_BANK = 0.08;

    private static final double TICKS_TO_COME_ROUND = 120.0;

    private static final double MAX_BANK = 0.35;
    private static final double BANK_SLEW = 0.01;
    private static final double CLIMB_SLEW = 0.02;

    private static final double PROBE_AHEAD = 22.0;
    private static final double FEELER_DEGREES = 50.0;
    private static final int PROBE_INTERVAL = 5;

    private static final double SIDE_GAIN = 3.0;

    private static final double BOXED_IN = 3.0;

    private static final double WORLD_EASE = 0.2;

    private static final double SCOUT_AHEAD = 16.0;
    private static final double DEEP_WATER = 4.0;

    private static final double LAND_BANK = 0.25;

    private static final int SETTLE_SEARCH_INTERVAL = 20;
    private static final int SETTLE_BUDGET_TICKS = 600;

    private final PerchSearch perchSearch;
    private final Activity activity;
    private final float speedModifier;
    private final UniformInt patrolDuration;
    private final int groundedUrgeTicks;
    private final int cooldownTicks;
    private final double leashRange;
    private final Function<BaseBirdMob, BlockPos> anchorSource;

    private long nextPatrolTime;
    private long cruiseEndTime;
    private long deadline;
    private Phase phase = Phase.LAUNCH;
    private Vec3 anchor = Vec3.ZERO;
    private BlockPos launchTarget = BlockPos.ZERO;
    private int launchBearing;
    private Vec3 carrot = Vec3.ZERO;
    private Vec3 carrotHeading = Vec3.ZERO;

    private double bank;
    private double lazyBank;
    private double climb;

    private double worldBank;
    private double probedWorldBank;
    private double climbUrge;
    private double probedClimbUrge;
    private double wetAhead;
    private double probedWetAhead;

    private double slowPhase;
    private double fastPhase;
    private double heightPhase;
    private boolean done;

    public PatrolFlight(PerchSearch perchSearch, Activity activity, float speedModifier, UniformInt patrolDuration,
                        int groundedUrgeTicks, int cooldownTicks, double leashRange,
                        Function<BaseBirdMob, BlockPos> anchorSource) {
        super(Map.of(
                BirdMod.PERCH_POS.get(), MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.HURT_BY, MemoryStatus.VALUE_ABSENT),
                patrolDuration.maxInclusive() + LAUNCH_BUDGET_TICKS + SETTLE_BUDGET_TICKS);
        this.perchSearch = perchSearch;
        this.activity = activity;
        this.speedModifier = speedModifier;
        this.patrolDuration = patrolDuration;
        this.groundedUrgeTicks = groundedUrgeTicks;
        this.cooldownTicks = cooldownTicks;
        this.leashRange = leashRange;
        this.anchorSource = anchorSource;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, BaseBirdMob bird) {
        return this.canStartPatrol(bird, level.getGameTime())
                && bird.getRandom().nextInt(this.groundedUrgeTicks) == 0;
    }

    private boolean canStartPatrol(BaseBirdMob bird, long gameTime) {
        if (!bird.canMoveOnItsOwn() || !bird.canMoveByFlying()) {
            return false;
        }
        if (bird.trip().isTraveling() || bird.isDrivenByBehavior()) {
            return false;
        }

        if (bird instanceof TameableBird tamed && tamed.isTame()) {
            return false;
        }

        return bird.isOnFoot() && gameTime >= this.nextPatrolTime;
    }

    @Override
    protected void start(ServerLevel level, BaseBirdMob bird, long gameTime) {
        BlockPos given = this.anchorSource.apply(bird);
        this.anchor = given != null ? Vec3.atCenterOf(given) : bird.position();
        this.resetCurve(bird);
        this.cruiseEndTime = gameTime + this.patrolDuration.sample(bird.getRandom());
        this.phase = Phase.LAUNCH;
        this.deadline = gameTime + LAUNCH_BUDGET_TICKS;
        this.launchBearing = 0;
        this.done = !this.aimLaunch(bird);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, BaseBirdMob bird, long gameTime) {
        if (this.done || !bird.canMoveOnItsOwn()) {
            return false;
        }

        if (this.phase != Phase.LAUNCH && bird.isOnFoot()) {
            return false;
        }

        if (this.phase == Phase.LAUNCH && !bird.getBrain().isActive(this.activity)) {
            return false;
        }

        Brain<?> brain = bird.getBrain();
        return this.phase == Phase.LAUNCH
                ? this.ownsLaunchTarget(brain) || noWalkTarget(brain)
                : noWalkTarget(brain);
    }

    @Override
    protected void tick(ServerLevel level, BaseBirdMob bird, long gameTime) {
        switch (this.phase) {
            case LAUNCH -> this.tickLaunch(level, bird, gameTime);
            case CRUISE, SETTLE -> this.tickFlying(level, bird, gameTime);
        }
    }

    @Override
    protected void stop(ServerLevel level, BaseBirdMob bird, long gameTime) {
        this.nextPatrolTime = gameTime + this.cooldownTicks;
        bird.releaseFlight();
        if (this.phase == Phase.LAUNCH && this.ownsLaunchTarget(bird.getBrain())) {
            bird.trip().cancel();
            bird.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    private void tickLaunch(ServerLevel level, BaseBirdMob bird, long gameTime) {
        boolean upAndClear = !bird.isOnFoot()
                && (bird.getY() >= this.launchTarget.getY() - LAUNCH_HANDOVER || gameTime >= this.deadline);
        if (upAndClear) {
            this.beginCruise(level, bird);
            return;
        }
        if (gameTime >= this.deadline) {
            this.done = true;
            return;
        }

        if (!this.ownsLaunchTarget(bird.getBrain())) {
            this.done = !this.aimLaunch(bird);
        }
    }

    private boolean aimLaunch(BaseBirdMob bird) {
        Vec3 facing = facing(bird);
        while (this.launchBearing < LAUNCH_DEGREES.length) {
            Vec3 out = turned(facing, LAUNCH_DEGREES[this.launchBearing++] * Mth.DEG_TO_RAD);
            Vec3 candidate = bird.position().add(out.scale(LAUNCH_OUT)).add(0.0, LAUNCH_UP, 0.0);
            if (!CorridorRaycaster.fitsAt(bird, candidate)) {
                continue;
            }
            this.launchTarget = BlockPos.containing(candidate);

            bird.trip().planArrival(this.launchTarget, ArrivalStyle.IN_FLIGHT);
            bird.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(this.launchTarget, this.speedModifier, 0));
            return true;
        }
        return false;
    }

    private void beginCruise(ServerLevel level, BaseBirdMob bird) {
        bird.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        if (!bird.takeOverFlight()) {
            this.done = true;
            return;
        }
        this.phase = Phase.CRUISE;
        this.carrotHeading = facing(bird);
        this.carrot = bird.position().add(this.carrotHeading.scale(CARROT_LEAD));
        this.carrot = new Vec3(this.carrot.x,
                Math.max(this.carrot.y, groundAt(level, this.carrot) + MIN_HEIGHT), this.carrot.z);
    }

    private void tickFlying(ServerLevel level, BaseBirdMob bird, long gameTime) {
        boolean outOfHours = !bird.getBrain().isActive(this.activity);
        if (this.phase == Phase.CRUISE && (gameTime >= this.cruiseEndTime || outOfHours)) {
            this.phase = Phase.SETTLE;
            this.deadline = gameTime + SETTLE_BUDGET_TICKS;
        }
        if (this.phase == Phase.SETTLE && this.tryToLand(bird, gameTime)) {
            return;
        }
        FlightEnvelope envelope = bird.activeEnvelope();
        double speed = Math.max(envelope.cruiseSpeed() * this.speedModifier, envelope.minSpeed());
        if (bird.tickCount % PROBE_INTERVAL == 0 && !this.probe(level, bird, envelope)) {
            this.endPatrolHere(bird);
            return;
        }
        this.worldBank = Mth.lerp(WORLD_EASE, this.worldBank, this.probedWorldBank);
        this.climbUrge = Mth.lerp(WORLD_EASE, this.climbUrge, this.probedClimbUrge);
        this.wetAhead = Mth.lerp(WORLD_EASE, this.wetAhead, this.probedWetAhead);
        this.driftBank(bird, envelope);
        this.driftClimb(level, bird);
        this.advanceCarrot(bird, envelope, speed);
        bird.steerTowards(this.carrot, envelope, speed);
    }

    private void advanceCarrot(BaseBirdMob bird, FlightEnvelope envelope, double speed) {
        this.carrotHeading = turned(this.carrotHeading, this.bank * envelope.turnPerTickAt(speed));

        double lead = this.carrot.distanceTo(bird.position());
        double step = speed * Mth.clamp(2.0 - lead / CARROT_LEAD, 0.0, 2.0);
        this.carrot = this.carrot
                .add(this.carrotHeading.scale(step))
                .add(0.0, this.climb * MAX_SLOPE * step, 0.0);
    }

    private void driftClimb(ServerLevel level, BaseBirdMob bird) {
        double want = Mth.lerp(0.5 + 0.5 * wave(bird.tickCount, HEIGHT_PERIOD, this.heightPhase),
                MIN_HEIGHT, MAX_HEIGHT);
        double over = this.carrot.y - groundAt(level, this.carrot);
        double target = Mth.clamp((want - over) / HEIGHT_SOFTNESS, -1.0, 1.0) + this.climbUrge;
        this.climb = slewToward(this.climb, Mth.clamp(target, -1.0, 1.0), CLIMB_SLEW);
    }

    private static double groundAt(ServerLevel level, Vec3 at) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(at.x, 0.0, at.z)).getY();
    }

    private void driftBank(BaseBirdMob bird, FlightEnvelope envelope) {
        double wander = WANDER_SLOW_BANK * wave(bird.tickCount, WANDER_SLOW_PERIOD, this.slowPhase)
                + WANDER_FAST_BANK * wave(bird.tickCount, WANDER_FAST_PERIOD, this.fastPhase);
        double lazy = Mth.clamp(this.withPullHome(bird, envelope, wander), -MAX_BANK, MAX_BANK);
        this.lazyBank = slewToward(this.lazyBank, lazy, BANK_SLEW);
        this.bank = Mth.clamp(this.lazyBank + this.worldBank, -1.0, 1.0);
    }

    private double withPullHome(BaseBirdMob bird, FlightEnvelope envelope, double wander) {
        Vec3 home = this.anchor.subtract(bird.position()).multiply(1, 0, 1);
        double out = home.length();
        double leash = Mth.clamp((out - this.leashRange) / Math.max(this.leashRange, 1.0), 0.0, 1.0);
        double pull = this.phase == Phase.SETTLE ? 1.0 : Math.max(leash, this.wetAhead);
        if (pull <= 0.0 || out < 1.0E-3) {
            return wander;
        }

        double toHome = signedAngle(this.carrotHeading, home.normalize());
        double perTick = Math.max(envelope.turnPerTickAt(envelope.cruiseSpeed()), 1.0E-3);
        double homeBank = Mth.clamp(toHome / (perTick * TICKS_TO_COME_ROUND), -MAX_BANK, MAX_BANK);
        return Mth.lerp(pull, wander, homeBank);
    }

    private boolean probe(ServerLevel level, BaseBirdMob bird, FlightEnvelope envelope) {
        double margin = envelope.corridorMargin();
        Vec3 left = turned(this.carrotHeading, -FEELER_DEGREES * Mth.DEG_TO_RAD);
        Vec3 right = turned(this.carrotHeading, FEELER_DEGREES * Mth.DEG_TO_RAD);
        double aheadOpen = this.openness(bird, this.carrotHeading, margin);
        double leftOpen = this.openness(bird, left, margin);
        double rightOpen = this.openness(bird, right, margin);
        if (Math.max(aheadOpen, Math.max(leftOpen, rightOpen)) * PROBE_AHEAD < BOXED_IN) {
            return false;
        }

        double urgency = (1.0 - aheadOpen) * (1.0 - aheadOpen);

        double sideways = Mth.clamp((rightOpen - leftOpen) * SIDE_GAIN + this.lazyBank, -1.0, 1.0);
        double wetSideways = LAND_BANK * (this.wetness(level, left) - this.wetness(level, right));
        this.probedWetAhead = this.wetness(level, this.carrotHeading);
        this.probedWorldBank = Mth.clamp(urgency * sideways + wetSideways, -1.0, 1.0);
        this.probedClimbUrge = urgency;
        return true;
    }

    private double openness(BaseBirdMob bird, Vec3 bearing, double margin) {
        Vec3 from = bird.position();
        Vec3 to = from.add(bearing.scale(PROBE_AHEAD));
        double open = CorridorRaycaster.clipCorridor(bird, from, to, PROBE_AHEAD, margin).open();
        return Mth.clamp(open / PROBE_AHEAD, 0.0, 1.0);
    }

    private double wetness(ServerLevel level, Vec3 bearing) {
        Vec3 at = this.carrot.add(bearing.scale(SCOUT_AHEAD));
        BlockPos column = BlockPos.containing(at.x, 0.0, at.z);
        int surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).getY();
        int floor = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, column).getY();
        return Mth.clamp((surface - floor) / DEEP_WATER, 0.0, 1.0);
    }

    private boolean tryToLand(BaseBirdMob bird, long gameTime) {
        if (gameTime >= this.deadline) {
            this.endPatrolHere(bird);
            return true;
        }
        if (gameTime % SETTLE_SEARCH_INTERVAL != 0) {
            return false;
        }
        BlockPos perch = this.perchSearch.findPerch(bird);
        if (perch == null) {
            return false;
        }
        bird.releaseFlight();
        bird.getBrain().setMemory(BirdMod.PERCH_POS.get(), perch);
        this.done = true;
        return true;
    }

    private void endPatrolHere(BaseBirdMob bird) {
        bird.releaseFlight();
        this.done = true;
    }

    private boolean ownsLaunchTarget(Brain<?> brain) {
        WalkTarget current = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        return current != null && current.getTarget().currentBlockPosition().equals(this.launchTarget);
    }

    private static boolean noWalkTarget(Brain<?> brain) {
        return !brain.hasMemoryValue(MemoryModuleType.WALK_TARGET);
    }

    private void resetCurve(BaseBirdMob bird) {
        this.bank = 0.0;
        this.lazyBank = 0.0;
        this.climb = 0.0;
        this.worldBank = 0.0;
        this.probedWorldBank = 0.0;
        this.climbUrge = 0.0;
        this.probedClimbUrge = 0.0;
        this.wetAhead = 0.0;
        this.probedWetAhead = 0.0;
        this.slowPhase = bird.getRandom().nextDouble() * Math.PI * 2.0;
        this.fastPhase = bird.getRandom().nextDouble() * Math.PI * 2.0;
        this.heightPhase = bird.getRandom().nextDouble() * Math.PI * 2.0;
    }

    private static double wave(int tick, double period, double phase) {
        return Math.sin(tick * (Math.PI * 2.0) / period + phase);
    }

    private static double slewToward(double value, double target, double rate) {
        return value + Mth.clamp(target - value, -rate, rate);
    }

    private static Vec3 facing(BaseBirdMob bird) {
        Vec3 velocity = bird.getDeltaMovement().multiply(1, 0, 1);
        if (velocity.lengthSqr() > 1.0E-4) {
            return velocity.normalize();
        }
        float yaw = bird.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    private static Vec3 turned(Vec3 heading, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(heading.x * cos - heading.z * sin, 0.0, heading.x * sin + heading.z * cos)
                .normalize();
    }

    private static double signedAngle(Vec3 from, Vec3 to) {
        return Math.atan2(from.x * to.z - from.z * to.x, from.x * to.x + from.z * to.z);
    }
}
