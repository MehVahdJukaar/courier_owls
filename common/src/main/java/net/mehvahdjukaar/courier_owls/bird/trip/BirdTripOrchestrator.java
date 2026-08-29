package net.mehvahdjukaar.courier_owls.bird.trip;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.navigator.BirdFlightNavigation;
import net.mehvahdjukaar.courier_owls.bird.navigator.BirdWalkNavigation;
import net.mehvahdjukaar.courier_owls.bird.navigator.FlightPlan;
import net.mehvahdjukaar.courier_owls.bird.navigator.direct.DirectPath;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.ArrivalHeading;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class BirdTripOrchestrator {
    private static final int RETRIES = 2;

    private static final double ARRIVED = 1.0;

    private static final int TICKS_BETWEEN_ATTEMPTS_RETRIES = 10;

    private static final double PARTIAL_LEG_HANDOFF_DISTANCE = 8.0;

    private static final double PROGRESS = 1.0;

    private enum Leg {
        NONE, WALK, FLIGHT, DIRECT
    }

    private final BaseBirdMob mob;
    private final BirdWalkNavigation groundNavigation;
    private final BirdFlightNavigation flightNavigation;

    @Nullable
    private BlockPos target;
    private int accuracy;
    private double speed;
    private int retriesLeft;
    private double distanceOfClosestAttempt = Double.MAX_VALUE;
    private int tickOfNextAttempt;
    private Leg lastLeg = Leg.NONE;

    private final EnumSet<Leg> demoted = EnumSet.noneOf(Leg.class);

    private Vec3 lastTravelPos = Vec3.ZERO;
    private int ticksWithoutTravel;

    @Nullable
    private FlightPlan watchedPlan;
    private double lastArcDist;
    private int ticksWithoutArc;

    private boolean lastTripFailed;

    private ArrivalStyle arrival = ArrivalStyle.LAND;

    @Nullable
    private BlockPos plannedPos;
    private ArrivalStyle plannedStyle = ArrivalStyle.LAND;
    @Nullable
    private ArrivalHeading plannedHeading;

    public BirdTripOrchestrator(BaseBirdMob mob, BirdWalkNavigation groundNavigation, BirdFlightNavigation flightNavigation) {
        this.mob = mob;
        this.groundNavigation = groundNavigation;
        this.flightNavigation = flightNavigation;
    }

    public boolean isDirectDemoted() {
        return this.demoted.contains(Leg.DIRECT);
    }

    public boolean isTraveling() {
        return this.target != null;
    }

    public boolean isTravelingTo(BlockPos pos) {
        return pos.equals(this.target);
    }

    public boolean lastTripFailed() {
        return this.lastTripFailed;
    }

    public void cancel() {
        this.target = null;
        this.lastTripFailed = false;
        if (this.arrival != ArrivalStyle.LAND) {
            this.arrival = ArrivalStyle.LAND;

            this.mob.releaseHold();
        }
    }

    public void planArrival(BlockPos target, ArrivalStyle style) {
        this.planArrival(target, style, null);
    }

    public void planArrival(BlockPos target, ArrivalStyle style, @Nullable ArrivalHeading heading) {
        this.plannedPos = target;
        this.plannedStyle = style;
        this.plannedHeading = heading;
    }

    public boolean arrivesInMidair() {
        return this.target != null && this.arrival != ArrivalStyle.LAND;
    }

    public ArrivalStyle arrivalStyle() {
        return this.target == null ? ArrivalStyle.LAND : this.arrival;
    }

    public boolean walkOrFlyTo(BlockPos target, int accuracy, double speed) {
        if (!target.equals(this.target)) {
            this.retriesLeft = RETRIES;
            this.distanceOfClosestAttempt = Double.MAX_VALUE;

            this.demoted.clear();
            this.lastLeg = Leg.NONE;
            this.lastTripFailed = false;
        }
        this.target = target;
        this.accuracy = accuracy;
        this.speed = speed;

        boolean planned = target.equals(this.plannedPos);
        ArrivalStyle style = planned ? this.plannedStyle : ArrivalStyle.LAND;
        this.arrival = style == ArrivalStyle.LAND_OR_HOVER ? this.resolveLandOrHover(target) : style;
        this.flightNavigation.setArrivalHeading(planned ? this.plannedHeading : null);
        WalkOrFly choice = WalkOrFly.decide(this.mob, target, accuracy, this.demoted.contains(Leg.WALK),
                this.groundNavigation, this.flightNavigation);
        return switch (choice.route()) {
            case WALK -> {
                this.lastLeg = Leg.WALK;
                yield this.mob.walkPath(choice.path());
            }
            case FLY -> {
                this.lastLeg = choice.path() instanceof DirectPath ? Leg.DIRECT : Leg.FLIGHT;
                yield this.mob.flyPath(choice.path(), speed);
            }
            case WAIT -> {
                this.lastLeg = Leg.NONE;
                yield false;
            }
        };
    }

    private ArrivalStyle resolveLandOrHover(BlockPos target) {
        if (!this.mob.level().isLoaded(target)) {
            return ArrivalStyle.LAND;
        }
        BlockPos below = target.below();
        return this.mob.level().getBlockState(below).getCollisionShape(this.mob.level(), below).isEmpty()
                ? ArrivalStyle.HOVER : ArrivalStyle.LAND;
    }

    public boolean reRoute() {
        if (this.target == null) {
            return false;
        }
        Path flying = this.flightNavigation.forceRemovePath();
        if (this.walkOrFlyTo(this.target, this.accuracy, this.speed)) {
            return true;
        }
        this.flightNavigation.forceSetPath(flying);
        return false;
    }

    public void onLegStarted() {
        this.lastTravelPos = this.mob.position();
        this.ticksWithoutTravel = 0;
    }

    public void tick() {
        BlockPos target = this.target;
        if (target == null) {
            return;
        }

        this.maybeHandoffToNextLeg(target);
        if (this.checkAttemptEnded()) {
            this.handleAttemptEnded(target);
        }
    }

    private void maybeHandoffToNextLeg(BlockPos target) {
        if (this.mob.getNavigation() != this.flightNavigation) {
            return;
        }
        FlightPlan plan = this.flightNavigation.currentPlan();
        if (plan == null) {
            return;
        }

        Path flightPath = plan.path();
        boolean endsBeforeTarget = flightPath.canReach() && !flightPath.getTarget().equals(target);
        if (endsBeforeTarget && plan.cursor().remaining() <= PARTIAL_LEG_HANDOFF_DISTANCE) {
            this.reRoute();
        }
    }

    private boolean checkAttemptEnded() {
        if (this.mob.isFluttering() || this.mob.isHoldingForLaunch()) {
            return false;
        }

        boolean inPlace = this.checkStalledInPlace();
        boolean onTheLine = this.checkStalledOnTheLine();
        return inPlace || onTheLine || this.mob.getNavigation().isDone();
    }

    private void handleAttemptEnded(BlockPos target) {
        double distanceToTarget = Vec3.atBottomCenterOf(target).distanceTo(this.mob.position());

        if (distanceToTarget <= ARRIVED + this.accuracy) {
            this.lastTripFailed = false;
            this.target = null;
            if (this.arrival == ArrivalStyle.HOVER) {
                this.mob.holdHere(Vec3.atBottomCenterOf(target));
            }

            this.arrival = ArrivalStyle.LAND;
            return;
        }
        if (distanceToTarget < this.distanceOfClosestAttempt - PROGRESS) {
            this.distanceOfClosestAttempt = distanceToTarget;
            this.demoted.clear();
        } else if (this.retriesLeft > 0) {
            if (this.mob.tickCount < this.tickOfNextAttempt) {
                return;
            }
            this.retriesLeft--;

            if (this.lastLeg != Leg.NONE) {
                this.demoted.add(this.lastLeg);
            }
        } else {
            this.lastTripFailed = true;
            this.target = null;

            this.arrival = ArrivalStyle.LAND;
            return;
        }
        this.tickOfNextAttempt = this.mob.tickCount + TICKS_BETWEEN_ATTEMPTS_RETRIES;
        this.reRoute();
    }

    private boolean checkStalledInPlace() {
        Vec3 currentPos = this.mob.position();
        if (currentPos.distanceToSqr(this.lastTravelPos) > Mth.square(this.mob.settings().pursuit().minimumTravelDist)) {
            this.lastTravelPos = currentPos;
            this.ticksWithoutTravel = 0;
            return false;
        }
        return ++this.ticksWithoutTravel >= this.mob.settings().pursuit().travelWindow;
    }

    private boolean checkStalledOnTheLine() {
        FlightPlan plan = this.mob.getNavigation() == this.flightNavigation
                ? this.flightNavigation.currentPlan() : null;
        if (plan == null) {
            this.watchedPlan = null;
            return false;
        }
        double along = plan.cursor().distanceAlongArc();
        if (plan != this.watchedPlan || along - this.lastArcDist >= this.mob.settings().pursuit().minimumArcDist) {
            this.watchedPlan = plan;
            this.lastArcDist = along;
            this.ticksWithoutArc = 0;
            return false;
        }
        return ++this.ticksWithoutArc >= this.mob.settings().pursuit().arcWindow;
    }
}
