package net.mehvahdjukaar.courier_owls.bird.trip;

import net.mehvahdjukaar.courier_owls.bird.controller.GaitSettings;
import net.mehvahdjukaar.courier_owls.bird.controller.Landing;
import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.navigator.BirdWalkNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

record WalkOrFly(Route route, @Nullable Path path, String why) {
    enum Route {
        WALK, FLY,

        WAIT
    }

    static WalkOrFly decide(BaseBirdMob mob, BlockPos target, int accuracy, boolean walkDemoted,
                            PathNavigation groundNavigation, PathNavigation flightNavigation) {
        GaitSettings gait = mob.settings().gait();

        boolean landing = isLanding(mob, gait);
        if (!mob.isOnFoot() && !landing) {
            return fly(mob, flightNavigation, target, accuracy,
                    "airborne (" + mob.getMode() + "), so walking was never on the table");
        }

        if (walkDemoted && !landing) {
            return fly(mob, flightNavigation, target, accuracy, "the last walk got nowhere, so trying the air");
        }

        if (!landing && !mob.canMoveByWalking() && mob.canMoveByFlying()) {
            Path unwalkedFlight = flightNavigation.createPath(target, accuracy);
            if (unwalkedFlight != null && unwalkedFlight.canReach()) {
                return new WalkOrFly(Route.FLY, unwalkedFlight, "the legs are not available, flying instead");
            }
        }
        Vec3 away = Vec3.atBottomCenterOf(target).subtract(mob.position());
        double reach = away.horizontalDistance();
        if (reach > gait.walkMaxDistance || Math.abs(away.y) > gait.walkMaxRise) {
            return flyUnlessLanding(mob, landing, flightNavigation, target, accuracy,
                    String.format(Locale.ROOT, "outside the walk band (%.1f out, %.1f up/down)", reach, away.y));
        }

        if (groundNavigation instanceof BirdWalkNavigation walk && walk.isInTheAir(target)) {
            return flyUnlessLanding(mob, landing, flightNavigation, target, accuracy,
                    String.format(Locale.ROOT, "a point in the air (%.1f up), no walk gets there", away.y));
        }
        Path groundPath = groundNavigation.createPath(target, accuracy);
        if (groundPath == null || !groundPath.canReach()) {
            String why = String.format(Locale.ROOT, "inside the walk band (%.1f out) but no ground route %s",
                    reach, groundPath == null ? "at all" : "that reaches");

            if (away.length() <= gait.walkAlwaysDistance) {
                return new WalkOrFly(Route.WAIT, null, why + ", and too short to be worth flying");
            }
            return flyUnlessLanding(mob, landing, flightNavigation, target, accuracy, why);
        }

        if (landing || reach <= gait.walkAlwaysDistance) {
            return new WalkOrFly(Route.WALK, groundPath, landing
                    ? "coming down anyway, walking what it lands next to"
                    : String.format(Locale.ROOT, "%.1f blocks, too short to fly", reach));
        }

        Path flightPath = flightNavigation.createPath(target, accuracy);
        double flightCost = flightPath != null ? pathLength(flightPath, mob) : Double.MAX_VALUE;
        double walkCost = pathLength(groundPath, mob) * gait.walkCostMultiplier;
        String priced = String.format(Locale.ROOT, "priced walk %.1f against flight %.1f",
                walkCost, flightCost);
        return walkCost > flightCost
                ? new WalkOrFly(Route.FLY, flightPath, priced)
                : new WalkOrFly(Route.WALK, groundPath, priced);
    }

    private static WalkOrFly fly(BaseBirdMob mob, PathNavigation flightNavigation, BlockPos target,
                                 int accuracy, String why) {
        if (!mob.canMoveByFlying()) {
            return new WalkOrFly(Route.WAIT, null, why + ", but this bird does not fly");
        }
        return new WalkOrFly(Route.FLY, flightNavigation.createPath(target, accuracy), why);
    }

    private static WalkOrFly flyUnlessLanding(BaseBirdMob mob, boolean landing, PathNavigation flightNavigation,
                                              BlockPos target, int accuracy, String why) {
        return landing
                ? new WalkOrFly(Route.WAIT, null, why + ", still coming down so declining to answer")
                : fly(mob, flightNavigation, target, accuracy, why);
    }

    private static boolean isLanding(BaseBirdMob mob, GaitSettings gait) {
        return !mob.trip().arrivesInMidair()
                && mob.isFluttering() && Landing.groundWithinReach(mob, gait.perchProbeDepth);
    }

    private static double pathLength(Path path, Entity entity) {
        double length = 0.0;
        for (int i = 1; i < path.getNodeCount(); i++) {
            length += path.getEntityPosAtNode(entity, i).distanceTo(path.getEntityPosAtNode(entity, i - 1));
        }
        return length;
    }
}
