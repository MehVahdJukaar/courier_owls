package net.mehvahdjukaar.courier_owls.bird.navigator;

import net.mehvahdjukaar.courier_owls.bird.controller.BirdFlightMoveControl;
import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.entity.BirdSettings;
import net.mehvahdjukaar.courier_owls.bird.line.LineCursor;
import net.mehvahdjukaar.courier_owls.bird.navigator.direct.DirectPath;
import net.mehvahdjukaar.courier_owls.bird.navigator.direct.DirectPathRouter;
import net.mehvahdjukaar.courier_owls.bird.navigator.trim.PathTrimmer;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.ArrivalHeading;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.BirdNodeEvaluator;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.BirdPathFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class BirdFlightNavigation extends FlyingPathNavigation {
    private static final long REROUTE_THROTTLE_TICKS = 20L;

    private final BaseBirdMob bird;
    private final BirdSettings settings;
    private final DirectPathRouter directRouter;
    @Nullable
    private ArrivalHeading arrivalHeading;

    @Nullable
    private FlightPlan plan;

    private int lastNodeIndex = -1;
    @Nullable
    private Carrot carrot;

    private double speedLimit = Double.MAX_VALUE;

    public BirdFlightNavigation(BaseBirdMob mob, Level level) {
        super(mob, level);
        this.bird = mob;
        this.settings = mob.settings();
        this.directRouter = new DirectPathRouter(mob);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        BirdNodeEvaluator evaluator = new BirdNodeEvaluator();
        evaluator.setCanPassDoors(true);
        this.nodeEvaluator = evaluator;

        return new BirdPathFinder(evaluator, ((BaseBirdMob) this.mob).settings().search());
    }

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        return this.moveTo(x, y, z, 1, speed);
    }

    @Override
    public boolean moveTo(Entity entity, double speed) {
        return this.moveTo(entity.getX(), entity.getY(), entity.getZ(), 1, speed);
    }

    @Override
    public boolean moveTo(double x, double y, double z, int accuracy, double speed) {
        return this.bird.trip().walkOrFlyTo(BlockPos.containing(x, y, z), accuracy, speed);
    }

    @Override
    public boolean moveTo(@Nullable Path path, double speed) {
        boolean accepted = super.moveTo(path, speed);
        if (!accepted) {
            this.clearPathState();
        }
        return accepted;
    }

    @Override
    @Nullable
    public Path createPath(BlockPos target, int accuracy) {
        Vec3 currentPos = this.getTempMobPos();
        DirectPath direct = this.directRouter.routeTo(target, currentPos, this.path, this.arrivalHeading);
        if (direct != null) {
            return direct;
        }

        if (this.path instanceof PartialPath partial && partial.headsTo(target) && !partial.isDone()) {
            return this.path;
        }
        double searchReach = this.settings.search().reachableRange();
        Vec3 to = Vec3.atCenterOf(target);
        double distance = currentPos.distanceTo(to);

        if (distance <= searchReach) {
            return this.createPath(Set.of(target), 8, false, accuracy);
        }

        BlockPos partWay = BlockPos.containing(currentPos.lerp(to, searchReach / distance));
        Path searched = this.createPath(Set.of(partWay), 8, false, 1);

        boolean fresh = searched != null && searched != this.path;
        return fresh ? PartialPath.of(searched, target) : searched;
    }

    public void setArrivalHeading(@Nullable ArrivalHeading heading) {
        this.arrivalHeading = heading;
        ((BirdPathFinder) this.pathFinder).setArrivalHeading(heading);
    }

    @Nullable
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy) {
        Path flyingPath = this.path;
        boolean hadDirectPath = flyingPath instanceof DirectPath;
        if (hadDirectPath) {
            this.path = null;
        }
        try {
            Path searched = super.createPath(targets, regionOffset, offsetUpward, accuracy,
                    this.settings.search().searchRange);

            if (searched == null || searched == this.path) {
                return searched;
            }
            PathTrimmer trimmer = new PathTrimmer(searched, this.mob, this.settings.trim(),
                    this.settings.direct().corridorMargin, this.arrivalHeading);
            return trimmer.trim();
        } finally {
            if (hadDirectPath) {
                this.path = flyingPath;
            }
        }
    }

    @Override
    public boolean shouldRecomputePath(BlockPos changed) {
        if (this.isDone()) {
            return false;
        }
        FlightPlan plan = this.planForFlownPath();
        double radius = plan.cursor().remaining();
        Vec3 center = this.getTempMobPos().add(plan.line().end()).scale(0.5);
        return changed.closerToCenterThan(center, radius);
    }

    @Override
    public void recomputePath() {
        if (this.level.getGameTime() - this.timeLastRecompute <= REROUTE_THROTTLE_TICKS) {
            this.hasDelayedRecomputation = true;
            return;
        }
        this.timeLastRecompute = this.level.getGameTime();
        this.hasDelayedRecomputation = false;
        this.bird.trip().reRoute();
    }

    @Nullable
    public Path forceRemovePath() {
        Path flying = this.path;
        this.path = null;
        return flying;
    }

    public void forceSetPath(@Nullable Path path) {
        this.path = path;
    }

    @Nullable
    public FlightPlan currentPlan() {
        return this.path == null ? null : this.planForFlownPath();
    }

    private FlightPlan planForFlownPath() {
        if (this.plan == null || this.plan.path() != this.path) {
            this.plan = FlightPlan.of(this.path, this.mob, this.settings);
            this.lastNodeIndex = -1;
        }
        return this.plan;
    }

    public void primeLaunchHold() {
        if (!this.isDone()) {
            this.steerAlong(this.planForFlownPath(), Double.MAX_VALUE);
        }
    }

    @Override
    public void tick() {
        if (this.bird.isHoldingForLaunch()) {
            if (!this.isDone()) {
                this.steerAlong(this.planForFlownPath(), Double.MAX_VALUE);
            }
            return;
        }
        this.tick++;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }
        if (this.isDone()) {
            return;
        }

        if (this.canUpdatePath()) {
            this.followThePath();
        }

        if (this.isDone()) {
            return;
        }
        FlightPlan plan = this.planForFlownPath();

        Carrot aim = Carrot.of(plan.cursor(), plan.pursuit());
        double limit = plan.speedLimitFor(this.getTempMobPos(), this.mob.getDeltaMovement().length(), aim);
        this.steerAlong(plan, aim, limit);
    }

    private void steerAlong(FlightPlan plan, double speedLimit) {
        this.steerAlong(plan, Carrot.of(plan.cursor(), plan.pursuit()), speedLimit);
    }

    private void steerAlong(FlightPlan plan, Carrot aim, double speedLimit) {
        this.carrot = aim;
        this.speedLimit = speedLimit;

        Vec3 point = aim.point();
        if (this.mob.getMoveControl() instanceof BirdFlightMoveControl flightControl) {
            flightControl.setFlightTarget(point, speedLimit, plan.envelope(), this.speedModifier);
        } else {
            this.mob.getMoveControl().setWantedPosition(point.x, point.y, point.z, this.speedModifier);
        }
    }

    @Override
    protected void followThePath() {
        Vec3 pos = this.getTempMobPos();
        FlightPlan plan = this.planForFlownPath();
        LineCursor cursor = plan.cursor();
        cursor.advanceTo(pos, this.settings.pursuit().cursorProjectionWindow);
        if (plan.hasArrived(this.mob.onGround())) {
            this.stop();
            return;
        }
        int nextNode = cursor.nextNodeIndex();
        if (nextNode != this.lastNodeIndex) {
            this.lastNodeIndex = nextNode;

            this.timeoutTimer = 0L;
        }
        this.path.setNextNodeIndex(nextNode);
        this.maxDistanceToWaypoint = (float) this.settings.pursuit().openAirLookahead;
        this.doStuckDetection(pos);
    }

    @Override
    protected void trimPath() {
    }

    @Override
    public void stop() {
        super.stop();
        this.clearPathState();
    }

    private void clearPathState() {
        this.plan = null;
        this.carrot = null;
        this.lastNodeIndex = -1;
        this.speedLimit = Double.MAX_VALUE;
    }
}
