package net.mehvahdjukaar.courier_owls.bird.pathfinding;

public class PathfindingSettings {
    public float[] turnCostByBin = {0.0F, 0.5F, 5.0F, 12.0F, 25.0F};

    public float[] startTurnCostByBin = {0.0F, 1.5F, 30.0F, 80.0F, 150.0F};

    public float[] pitchCostByBin = {0.0F, 0.5F, 5.0F};

    public float straightVerticalCost = 7.0F;

    public float enclosureCost = 3.5F;

    public float heuristicWeight = 1.6F;

    public float verticalHeuristicPerBlock = 1.41F;

    public boolean turnAwareHeuristic = true;

    public boolean settleWhenStuck = true;

    public int nodesPerBlockOfRange = 16;

    public float searchRange = 96.0F;

    public double searchableFraction = 0.75;

    public double reachableRange() {
        return this.searchRange * this.searchableFraction;
    }

    public int stallWindow = 300;

    public float stallEpsilon = 0.25F;

    public float budgetLeashFraction = 0.5F;

    public boolean landingRingWhenStuck = true;

    public int landingRingRadius = 4;
    public int landingRingRise = 2;

    public int landingRingMaxSteps = 6;

    public boolean rescueWhenStuck = true;

    public int floodSettleBudget = 32_768;

    public float floodHeuristicWeight = 1.6F;
}
