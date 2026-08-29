package net.mehvahdjukaar.courier_owls.bird.navigator;

import net.minecraft.util.Mth;

public class PursuitFlightSettings {
    public double openAirLookahead = 4;

    public double enclosedLookahead = 0.6;

    public double offRouteRecoveryGain = 1.0;

    public double enclosureCaution = 3.0;

    public double lookaheadTaper = 4.0;

    public double arrivalLookaheadFraction = 0.5;

    public double lookahead(double pullIn, double offRoute, double remaining) {
        double room = Mth.lerp(pullIn, openAirLookahead, enclosedLookahead);
        room = Math.min(room, remaining * arrivalLookaheadFraction);
        return Mth.clamp(room - offRoute * offRouteRecoveryGain, enclosedLookahead, openAirLookahead);
    }

    public double rejoinRampStart = 2.0;
    public double rejoinRampEnd = 4.0;

    public double cursorProjectionWindow = 2.0;

    public double arrivalRadius = 0.25;

    public int arcWindow = 40;
    public double minimumArcDist = 1.0;

    public int travelWindow = 40;
    public double minimumTravelDist = 1.0;
}
