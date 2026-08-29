package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Target;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class BirdPathFinder extends PathFinder {
    private final Node[] neighbors = new Node[32];
    private final int maxVisitedNodes;
    private final BirdNodeEvaluator nodeEvaluator;
    private final BinaryHeap openSet = new BinaryHeap();

    private final PathfindingSettings settings;

    @Nullable
    private RescueFlood flood;

    private float turnCostFloorPerBin;

    @Nullable
    private ArrivalHeading arrival;

    private final Long2ObjectMap<Target> ringCells = new Long2ObjectOpenHashMap<>();

    private float ringSlack;

    public BirdPathFinder(BirdNodeEvaluator nodeEvaluator, PathfindingSettings settings) {
        this(nodeEvaluator, settings, Mth.floor(settings.searchRange * settings.nodesPerBlockOfRange
                * BirdNodeEvaluator.HEADING_BINS * BirdNodeEvaluator.CLIMB_STATES));
    }

    private BirdPathFinder(BirdNodeEvaluator nodeEvaluator, PathfindingSettings settings, int maxVisitedNodes) {
        super(nodeEvaluator, maxVisitedNodes);
        this.nodeEvaluator = nodeEvaluator;
        this.maxVisitedNodes = maxVisitedNodes;
        this.settings = settings;
    }

    private record Query(ProfilerFiller profiler, Node start, Map<Target, BlockPos> targets,
                         float maxRange, int accuracy, int maxVisited) {
        Set<Target> targetSet() {
            return this.targets.keySet();
        }
    }

    private record Attempt(@Nullable Path path) {
        boolean reached() {
            return this.path != null && this.path.canReach();
        }
    }

    private static final class CheapestArrival {
        @Nullable
        private Node node;
        @Nullable
        private Target target;

        void offer(Node node, Target target) {
            if (this.node == null || node.g < this.node.g) {
                this.node = node;
                this.target = target;
            }
        }

        boolean found() {
            return this.node != null;
        }
    }

    private record LandingRing(@Nullable Attempt attempt) {
        static final LandingRing NONE = new LandingRing(null);

        boolean reached() {
            return this.attempt != null && this.attempt.reached();
        }
    }

    private enum LadderStep {
        BLIND(false),

        LANDING_RING(true),

        RESCUE_FLOOD(true);

        private final boolean warmStart;

        LadderStep(boolean warmStart) {
            this.warmStart = warmStart;
        }

        boolean onBudgetLeash(PathfindingSettings settings) {
            return switch (this) {
                case BLIND -> settings.landingRingWhenStuck || settings.rescueWhenStuck;
                case LANDING_RING -> settings.rescueWhenStuck;
                case RESCUE_FLOOD -> false;
            };
        }

        float weight(PathfindingSettings settings) {
            return this == RESCUE_FLOOD ? settings.floodHeuristicWeight : settings.heuristicWeight;
        }
    }

    @Override
    @Nullable
    public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targetPositions, float maxRange, int accuracy, float searchDepthMultiplier) {
        try {
            return this.requireArrivalHeading(
                    this.search(region, mob, targetPositions, maxRange, accuracy, searchDepthMultiplier));
        } finally {
            this.nodeEvaluator.done();
        }
    }

    @Nullable
    private Path requireArrivalHeading(@Nullable Path path) {
        if (!this.headingIsRequired() || path == null || (path.canReach() && this.arrival.endsFacing(path))) {
            return path;
        }
        return null;
    }

    @Nullable
    private Path search(PathNavigationRegion region, Mob mob, Set<BlockPos> targetPositions,
                        float maxRange, int accuracy, float searchDepthMultiplier) {
        this.nodeEvaluator.prepare(region, mob);
        Node start = this.nodeEvaluator.getStart();
        if (start == null) {
            return null;
        }
        Map<Target, BlockPos> targets = targetPositions.stream().collect(Collectors.toMap(
                pos -> this.nodeEvaluator.getTarget(pos.getX(), pos.getY(), pos.getZ()), Function.identity()));
        Query query = new Query(Profiler.get(), start, targets, maxRange, accuracy,
                (int) (this.maxVisitedNodes * searchDepthMultiplier));

        this.flood = null;
        this.ringCells.clear();
        this.ringSlack = 0.0F;
        this.turnCostFloorPerBin = this.settings.turnAwareHeuristic
                ? EdgeCost.cheapestTurnPerBin(this.settings) : 0.0F;
        Attempt first = this.runAttempt(query, LadderStep.BLIND);

        boolean ladderOff = !this.settings.landingRingWhenStuck && !this.settings.rescueWhenStuck;
        if (first.reached() || ladderOff) {
            return first.path();
        }
        return this.recover(query, first);
    }

    @Nullable
    private Path recover(Query query, Attempt first) {
        LandingRing ring = this.settings.landingRingWhenStuck && !this.headingIsRequired()
                ? this.widenLandingRing(query) : LandingRing.NONE;
        if (ring.reached() || !this.settings.rescueWhenStuck) {
            Attempt best = ring.reached() ? ring.attempt() : closerEffort(ring.attempt(), first);
            return best.path();
        }
        return this.floodRescue(query, first, ring);
    }

    private LandingRing widenLandingRing(Query query) {
        List<BlockPos> around = this.nodeEvaluator.walkableAround(List.copyOf(query.targets().values()),
                this.settings.landingRingRadius, this.settings.landingRingRise,
                this.settings.landingRingMaxSteps);
        if (around.isEmpty()) {
            return LandingRing.NONE;
        }

        if (around.contains(query.start().asBlockPos())) {
            return LandingRing.NONE;
        }
        for (BlockPos pos : around) {
            Target owner = nearestTarget(pos, query.targetSet());
            this.ringCells.put(pos.asLong(), owner);
            this.ringSlack = Math.max(this.ringSlack, Mth.sqrt((float) pos.distSqr(owner.asBlockPos())));
        }

        CheapestArrival known = this.cheapestClosedRingCell();
        if (known.found()) {
            known.target.setReached();
            Path path = reconstructPath(known.node, query.targets().get(known.target), true);
            return new LandingRing(new Attempt(path));
        }
        Set<Target> targets = query.targetSet();
        if (!this.reprice(node -> this.getBestH(node, targets) * this.settings.heuristicWeight)) {
            return LandingRing.NONE;
        }
        return new LandingRing(this.runAttempt(query, LadderStep.LANDING_RING));
    }

    public void setArrivalHeading(@Nullable ArrivalHeading arrival) {
        this.arrival = arrival;
    }

    private boolean arrivesFacing(Node node) {
        return !this.headingIsRequired() || this.arrival.endsFacing(node);
    }

    private boolean headingIsRequired() {
        return this.arrival != null && this.arrival.isRequired();
    }

    private static Target nearestTarget(BlockPos pos, Set<Target> targets) {
        return Collections.min(targets, Comparator.comparingDouble(target -> pos.distSqr(target.asBlockPos())));
    }

    @Nullable
    private Target ringOwner(Node node) {
        return this.ringCells.get(BlockPos.asLong(node.x, node.y, node.z));
    }

    private CheapestArrival cheapestClosedRingCell() {
        CheapestArrival best = new CheapestArrival();
        for (BirdNode node : this.nodeEvaluator.knownStates()) {
            Target owner = node.closed ? this.ringOwner(node) : null;
            if (owner != null) {
                best.offer(node, owner);
            }
        }
        return best;
    }

    private boolean reprice(ToDoubleFunction<Node> h) {
        List<Node> frontier = new ArrayList<>();
        while (!this.openSet.isEmpty()) {
            frontier.add(this.openSet.pop());
        }
        for (Node node : frontier) {
            float priced = (float) h.applyAsDouble(node);
            if (Float.isInfinite(priced)) {
                continue;
            }
            node.h = priced;
            node.f = node.g + node.h;
            this.openSet.insert(node);
        }
        return !this.openSet.isEmpty();
    }

    @Nullable
    private Path floodRescue(Query query, Attempt first, LandingRing ring) {
        Node start = query.start();
        RescueFlood flood = new RescueFlood(this.nodeEvaluator.rescueFloodStepper(),
                this.settings.floodSettleBudget, start.asBlockPos());
        query.targets().values().forEach(flood::seed);

        for (long cell : this.ringCells.keySet()) {
            flood.seed(BlockPos.of(cell));
        }

        flood.distanceTo(start.x, start.y, start.z);
        boolean startSettled = flood.isSettled(start.x, start.y, start.z);

        Attempt retry = null;
        if (startSettled && this.reprice(node ->
                flood.distanceTo(node.x, node.y, node.z) * this.settings.floodHeuristicWeight)) {
            this.flood = flood;
            retry = this.runAttempt(query, LadderStep.RESCUE_FLOOD);
            this.flood = null;
        }

        Attempt fallback = closerEffort(ring.attempt(), first);
        return retry != null && retry.path() != null ? retry.path() : fallback.path();
    }

    private static Attempt closerEffort(@Nullable Attempt a, Attempt b) {
        if (a == null || a.path() == null) {
            return b;
        }
        if (b.path() == null) {
            return a;
        }
        return a.path().getDistToTarget() <= b.path().getDistToTarget() ? a : b;
    }

    private Attempt runAttempt(Query query, LadderStep step) {
        boolean onBudgetLeash = step.onBudgetLeash(this.settings);

        boolean stallWatch = onBudgetLeash && (!this.headingIsRequired() || this.turnCostFloorPerBin > 0);
        int stallAfter = stallWatch ? this.settings.stallWindow : 0;
        int budget = onBudgetLeash
                ? Math.min(query.maxVisited(), (int) (this.maxVisitedNodes * this.settings.budgetLeashFraction))
                : query.maxVisited();

        return this.findLatticePath(query, step, budget, stallAfter);
    }

    private Attempt findLatticePath(Query query, LadderStep step, int maxVisited, int stallAfter) {
        ProfilerFiller profiler = query.profiler();
        profiler.push("find_path");
        profiler.markForCharting(MetricCategory.PATH_FINDING);
        Node from = query.start();
        Set<Target> targets = query.targetSet();
        float weight = step.weight(this.settings);

        Map<Target, Node> bestEndpoints = step.warmStart
                ? seedEndpoints(from, targets) : this.seedStart(from, targets, weight);
        Set<Target> reachedTargets = HashSet.newHashSet(targets.size());
        CheapestArrival settleOn = new CheapestArrival();
        int visited = 0;
        Node reachedAt = null;

        float bestHSeen = Float.MAX_VALUE;
        float progressEpsilon = this.settings.stallEpsilon * weight;
        int expansionsSinceProgress = 0;

        while (!this.openSet.isEmpty()) {
            if (++visited >= maxVisited) {
                break;
            }

            Node current = this.openSet.pop();
            current.closed = true;

            Target onRing = this.ringOwner(current);
            for (Target target : targets) {
                this.nominateEndpoint(bestEndpoints, target, current);
                boolean atGoal = target == onRing || current.distanceManhattan(target) <= query.accuracy();
                if (atGoal && this.arrivesFacing(current)) {
                    target.setReached();
                    reachedTargets.add(target);
                }
            }

            if (!reachedTargets.isEmpty()) {
                reachedAt = current;
                break;
            }

            if (stallAfter > 0) {
                if (current.h < bestHSeen - progressEpsilon) {
                    bestHSeen = current.h;
                    expansionsSinceProgress = 0;
                } else if (++expansionsSinceProgress >= stallAfter) {
                    break;
                }
            }

            if (current.distanceTo(from) < query.maxRange()) {
                this.relaxNeighbors(current, query, weight, settleOn);
            }
        }

        if (reachedTargets.isEmpty() && this.settings.settleWhenStuck && settleOn.found()) {
            settleOn.target.setReached();
            reachedTargets.add(settleOn.target);
            reachedAt = settleOn.node;
        }

        Optional<Path> best = chooseBest(query.targets(), reachedTargets, reachedAt, bestEndpoints);
        profiler.pop();
        return new Attempt(best.orElse(null));
    }

    private Map<Target, Node> seedStart(Node from, Set<Target> targets, float weight) {
        from.g = 0.0F;

        from.h = this.getBestH(from, targets) * weight;
        from.f = from.h;
        this.openSet.clear();
        this.openSet.insert(from);
        return seedEndpoints(from, targets);
    }

    private static Map<Target, Node> seedEndpoints(Node from, Set<Target> targets) {
        Map<Target, Node> bestEndpoints = HashMap.newHashMap(targets.size());
        targets.forEach(target -> bestEndpoints.put(target, from));
        return bestEndpoints;
    }

    private void relaxNeighbors(Node current, Query query, float weight, CheapestArrival settleOn) {
        Set<Target> targets = query.targetSet();
        int neighborCount = this.nodeEvaluator.getNeighbors(this.neighbors, current);
        for (int i = 0; i < neighborCount; i++) {
            Node neighbor = this.neighbors[i];
            EdgeCost step = EdgeCost.between(current, neighbor, this.settings);
            neighbor.walkedDistance = current.walkedDistance + step.distance();
            float tentativeG = current.g + step.total();
            boolean improves = !neighbor.inOpenSet() || tentativeG < neighbor.g;
            if (neighbor.walkedDistance >= query.maxRange() || !improves) {
                continue;
            }
            float h = this.getBestH(neighbor, targets) * weight;
            if (Float.isInfinite(h)) {
                continue;
            }
            neighbor.cameFrom = current;
            neighbor.g = tentativeG;
            neighbor.h = h;
            if (neighbor.inOpenSet()) {
                this.openSet.changeCost(neighbor, neighbor.g + neighbor.h);
            } else {
                neighbor.f = neighbor.g + neighbor.h;
                this.openSet.insert(neighbor);
            }

            if (this.settings.settleWhenStuck && this.arrivesFacing(neighbor)) {
                Target arrivedAt = this.arrivalTarget(neighbor, targets, query.accuracy());
                if (arrivedAt != null) {
                    settleOn.offer(neighbor, arrivedAt);
                }
            }
        }
    }

    @Nullable
    private Target arrivalTarget(Node node, Set<Target> targets, int accuracy) {
        Target ringOwner = this.ringOwner(node);
        if (ringOwner != null) {
            return ringOwner;
        }
        for (Target target : targets) {
            if (node.distanceManhattan(target) <= accuracy) {
                return target;
            }
        }
        return null;
    }

    private static Optional<Path> chooseBest(Map<Target, BlockPos> targetMap, Set<Target> reachedTargets,
                                             @Nullable Node reachedAt, Map<Target, Node> bestEndpoints) {
        return !reachedTargets.isEmpty()
                ? reachedTargets.stream()
                .map(target -> reconstructPath(reachedAt, targetMap.get(target), true))
                .min(Comparator.comparingDouble(Path::getDistToTarget))
                : targetMap.keySet().stream()
                .map(target -> reconstructPath(bestEndpoints.get(target), targetMap.get(target), false))
                .min(Comparator.comparingDouble(Path::getDistToTarget).thenComparingInt(Path::getNodeCount));
    }

    private void nominateEndpoint(Map<Target, Node> bestEndpoints, Target target, Node settled) {
        Node incumbent = bestEndpoints.get(target);
        float settledH = this.endpointDistance(settled, target);
        float incumbentH = this.endpointDistance(incumbent, target);
        if (settledH != incumbentH ? settledH < incumbentH : settled.g < incumbent.g) {
            bestEndpoints.put(target, settled);
        }
    }

    private float endpointDistance(Node node, Target target) {
        return this.flood != null ? this.flood.distanceTo(node.x, node.y, node.z) : node.distanceTo(target);
    }

    private float getBestH(Node node, Set<Target> targets) {
        if (this.flood != null) {
            return this.flood.distanceTo(node.x, node.y, node.z);
        }
        float best = Float.MAX_VALUE;
        for (Target target : targets) {
            float h = Math.max(node.distanceTo(target),
                    Math.abs(node.y - target.y) * this.settings.verticalHeuristicPerBlock)
                    + this.turnBound(node, target);
            best = Math.min(h, best);
        }

        return this.ringSlack > 0 ? Math.max(0.0F, best - this.ringSlack) : best;
    }

    private float turnBound(Node node, Target target) {
        if (this.turnCostFloorPerBin <= 0 || !(node instanceof BirdNode bird) || bird.freeHeading) {
            return 0.0F;
        }
        int towardBearing = this.ringSlack > 0 ? 0
                : BirdNodeEvaluator.turnBinsToward(bird.heading, target.x - node.x, target.z - node.z);
        int towardArrival = this.arrival == null ? 0 : this.arrival.binsFrom(bird.heading);
        return this.turnCostFloorPerBin * Math.max(towardBearing, towardArrival);
    }

    private static Path reconstructPath(Node end, BlockPos targetPos, boolean reachesTarget) {
        List<Node> nodes = new ArrayList<>();
        for (Node node = end; node != null; node = node.cameFrom) {
            nodes.addFirst(node);
        }
        return new Path(nodes, targetPos, reachesTarget);
    }
}
