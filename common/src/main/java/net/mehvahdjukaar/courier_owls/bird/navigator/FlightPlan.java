package net.mehvahdjukaar.courier_owls.bird.navigator;

import net.mehvahdjukaar.courier_owls.bird.controller.Landing;
import net.mehvahdjukaar.courier_owls.bird.trip.ArrivalStyle;
import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.entity.BirdSettings;
import net.mehvahdjukaar.courier_owls.bird.line.FlightLine;
import net.mehvahdjukaar.courier_owls.bird.line.LineCursor;
import net.mehvahdjukaar.courier_owls.bird.line.PathSmoother;
import net.mehvahdjukaar.courier_owls.bird.envelope.FlightEnvelope;
import net.mehvahdjukaar.courier_owls.bird.envelope.ThrottlePlanner;
import net.mehvahdjukaar.courier_owls.bird.envelope.ThrottleProfile;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public record FlightPlan(Path path, LineCursor cursor, FlightEnvelope envelope,
                         ThrottleProfile throttle, Landing landing, PursuitFlightSettings pursuit) {
    static FlightPlan of(Path path, Mob mob, BirdSettings settings) {
        FlightEnvelope envelope = FlightEnvelope.forMob(mob, settings.envelope());
        FlightLine line = FlightLine.of(path, mob).withEndLifted(settings.gait().arrivalLift);

        ArrivalStyle arrival = mob instanceof BaseBirdMob bird ? bird.trip().arrivalStyle() : ArrivalStyle.LAND;
        boolean staysAirborne = arrival != ArrivalStyle.LAND;
        Landing landing = staysAirborne ? Landing.SETTLE : Landing.of(mob, line, settings.gait());
        if (landing.isImpact()) {
            line = line.withEndAt(landing.touchdown());
        }
        line = PathSmoother.smooth(line, envelope.corridorMargin(), envelope.clearanceSpentFraction(),
                mob.getBbWidth() * 0.5, mob.getBbHeight());

        double arrivalSpeed = arrival == ArrivalStyle.IN_FLIGHT
                ? envelope.cruiseSpeed() : envelope.maxTouchdownSpeed();
        ThrottleProfile throttle = ThrottlePlanner.plan(line, envelope, arrivalSpeed);
        LineCursor cursor = new LineCursor(line);
        return new FlightPlan(path, cursor, envelope, throttle, landing, settings.pursuit());
    }

    public FlightLine line() {
        return this.cursor.line();
    }

    public boolean hasArrived(boolean onGround) {
        double remaining = this.cursor.remaining();
        if (this.landing.isImpact()) {
            return remaining <= 0.0 || (onGround && remaining <= this.pursuit.arrivalRadius);
        }
        return remaining <= this.pursuit.arrivalRadius;
    }

    public double speedLimitFor(Vec3 mobPos, double currentSpeed, Carrot aim) {
        double lookahead = aim.lookahead();
        double groundPerArc = lookahead > 0.0
                ? Math.min(1.0, mobPos.distanceTo(aim.point()) / lookahead) : 1.0;

        double window = this.envelope.coastingDistance(currentSpeed);
        double tightest = this.throttle.limitAheadOf(this.cursor.distanceAlongArc(), window,
                this.envelope, groundPerArc);

        double endLimit = this.throttle.limitAtNode(this.throttle.nodeCount() - 1);
        double floor = Math.min(this.offRouteSpeedFloor(this.cursor.offRoute()),
                this.envelope.maxEntrySpeed(endLimit, this.cursor.remaining(), 0.0));
        return Math.max(tightest, floor);
    }

    private double offRouteSpeedFloor(double offRoute) {
        double lost = Mth.clamp((offRoute - this.pursuit.rejoinRampStart)
                / (this.pursuit.rejoinRampEnd - this.pursuit.rejoinRampStart), 0.0, 1.0);
        return lost * this.envelope.cruiseSpeed();
    }
}
