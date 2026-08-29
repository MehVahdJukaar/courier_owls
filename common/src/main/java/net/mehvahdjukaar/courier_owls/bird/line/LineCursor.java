package net.mehvahdjukaar.courier_owls.bird.line;

import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class LineCursor {
    private static final double MAX_ARC_PER_GROUND = 2.0;

    private static final double PROJECTION_TIE = 1.0E-6;

    private final FlightLine line;

    private double distanceAlong;
    private double offRoute;
    @Nullable
    private Vec3 lastPosition;

    public LineCursor(FlightLine line) {
        this.line = line;
    }

    public FlightLine line() {
        return this.line;
    }

    public double distanceAlongArc() {
        return this.distanceAlong;
    }

    public double remaining() {
        return this.line.length() - this.distanceAlong;
    }

    public double offRoute() {
        return this.offRoute;
    }

    public void advanceTo(Vec3 pos, double window) {
        Vec3[] points = this.line.points();
        ArcLengths arc = this.line.arc();
        double highest = this.distanceAlong + window;
        double closestDistance = this.distanceAlong;
        double closestOffsetSqr = Double.MAX_VALUE;

        for (int i = arc.legAt(this.distanceAlong - window); i < points.length - 1 && arc.at(i) <= highest; i++) {
            Vec3 start = points[i];
            Vec3 leg = points[i + 1].subtract(start);
            double legLengthSqr = leg.lengthSqr();
            if (legLengthSqr < FlightMath.DEGENERATE_LEG_SQR) {
                continue;
            }
            double along = FlightMath.projectedFraction(start, leg, legLengthSqr, pos);
            double offsetSqr = start.add(leg.scale(along)).distanceToSqr(pos);
            double distance = arc.at(i) + along * Math.sqrt(legLengthSqr);
            if (offsetSqr < closestOffsetSqr - PROJECTION_TIE
                    || (offsetSqr < closestOffsetSqr + PROJECTION_TIE && distance > closestDistance)) {
                closestOffsetSqr = Math.min(closestOffsetSqr, offsetSqr);
                closestDistance = distance;
            }
        }

        double groundMoved = this.lastPosition == null ? 0.0 : pos.distanceTo(this.lastPosition);
        this.lastPosition = pos;
        double maxStep = groundMoved * MAX_ARC_PER_GROUND;
        this.distanceAlong = Mth.clamp(closestDistance, this.distanceAlong - maxStep, this.distanceAlong + maxStep);
        this.offRoute = closestOffsetSqr == Double.MAX_VALUE
                ? pos.distanceTo(this.line.pointAt(this.distanceAlong)) : Math.sqrt(closestOffsetSqr);
    }

    public Vec3 lookaheadPoint(double lookahead) {
        return this.line.pointAt(this.distanceAlong + lookahead);
    }

    public int nextNodeIndex() {
        return this.line.nextOriginalNodeAfter(this.distanceAlong);
    }
}
