package net.mehvahdjukaar.courier_owls.bird.envelope;

import net.mehvahdjukaar.courier_owls.bird.line.ArcLengths;
import net.mehvahdjukaar.courier_owls.bird.line.FlightLine;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.minecraft.world.phys.Vec3;

public final class ThrottlePlanner {
    private static final double NO_LIMIT = Double.MAX_VALUE;

    public static ThrottleProfile plan(FlightLine line, FlightEnvelope envelope, double arrivalSpeed) {
        Vec3[] points = line.points();
        if (points.length == 0) {
            return new ThrottleProfile(ArcLengths.of(new Vec3[]{Vec3.ZERO}), new double[]{0.0});
        }
        ArcLengths arc = line.arc();
        double[] legPitch = legPitches(points);
        double[] limit = cornerLimits(line.turnRadius(), envelope);
        coastingRamps(arc, legPitch, limit, envelope);
        flareRamp(arc, legPitch, limit, envelope, arrivalSpeed);
        accelerationLimits(arc, legPitch, limit, envelope);
        return new ThrottleProfile(arc, limit);
    }

    private static double[] legPitches(Vec3[] points) {
        double[] pitch = new double[points.length - 1];
        for (int i = 0; i < pitch.length; i++) {
            pitch[i] = FlightMath.pitchOf(points[i + 1].subtract(points[i]));
        }
        return pitch;
    }

    private static double[] cornerLimits(double[] turnRadius, FlightEnvelope envelope) {
        double[] limit = new double[turnRadius.length];
        for (int i = 0; i < limit.length; i++) {
            double radius = turnRadius[i];
            limit[i] = radius == FlightLine.STRAIGHT ? NO_LIMIT : Math.max(envelope.maxSpeedForArc(radius), envelope.minSpeed());
        }

        limit[0] = Math.min(limit[0], envelope.maxHorizontalSpeed());
        return limit;
    }

    private static void coastingRamps(ArcLengths arc, double[] legPitch, double[] limit, FlightEnvelope envelope) {
        for (int i = limit.length - 2; i >= 0; i--) {
            double coastBrake = envelope.coastBrakeAt(legPitch[i]);
            double entry = envelope.maxEntrySpeed(limit[i + 1], arc.legLength(i), legPitch[i], coastBrake);
            limit[i] = Math.min(limit[i], entry);
        }
    }

    private static void flareRamp(ArcLengths arc, double[] legPitch, double[] limit,
                                  FlightEnvelope envelope, double arrivalSpeed) {
        int last = limit.length - 1;
        limit[last] = Math.min(limit[last], arrivalSpeed);
        double entry = limit[last];
        for (int i = last - 1; i >= 0; i--) {
            entry = envelope.maxEntrySpeed(entry, arc.legLength(i), legPitch[i]);
            limit[i] = Math.min(limit[i], entry);
        }
    }

    private static void accelerationLimits(ArcLengths arc, double[] legPitch, double[] limit, FlightEnvelope envelope) {
        for (int i = 1; i < limit.length; i++) {
            double reachable = envelope.speedAfterAccelerating(limit[i - 1], arc.legLength(i - 1), legPitch[i - 1]);
            limit[i] = Math.min(limit[i], reachable);
        }
    }
}
