package net.mehvahdjukaar.courier_owls.bird.envelope;

import net.mehvahdjukaar.courier_owls.bird.line.ArcLengths;
import net.minecraft.util.Mth;

public final class ThrottleProfile {
    private final ArcLengths arc;
    private final double[] nodeLimits;

    ThrottleProfile(ArcLengths arc, double[] nodeLimits) {
        this.arc = arc;
        this.nodeLimits = nodeLimits;
    }

    public int nodeCount() {
        return this.arc.nodeCount();
    }

    public double arcAtNode(int index) {
        return this.arc.at(index);
    }

    public double limitAtNode(int index) {
        return this.nodeLimits[Mth.clamp(index, 0, this.nodeLimits.length - 1)];
    }

    public double speedLimitAt(double distance) {
        return this.arc.lerpAt(distance, this.nodeLimits);
    }

    public double limitAheadOf(double along, double distanceAhead, FlightEnvelope envelope, double groundPerArc) {
        double window = along + distanceAhead;
        int last = this.nodeCount() - 1;
        double tightest = this.speedLimitAt(along);
        for (int i = this.arc.nextNodeAfter(along); i <= last && this.arcAtNode(i) <= window; i++) {
            double ground = (this.arcAtNode(i) - along) * groundPerArc;
            double brake = i == last ? envelope.wingBrakeAccel() : 0.0;
            tightest = Math.min(tightest, envelope.maxEntrySpeed(this.limitAtNode(i), ground, 0.0, brake));
        }
        return tightest;
    }
}
