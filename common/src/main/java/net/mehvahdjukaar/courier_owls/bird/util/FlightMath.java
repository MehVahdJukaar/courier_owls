package net.mehvahdjukaar.courier_owls.bird.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class FlightMath {
    public static final double NEAR_ZERO = 1.0E-4;

    public static final double DEGENERATE_LEG = 1.0E-9;

    public static final double DEGENERATE_LEG_SQR = 1.0E-9;

    public static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    public static double pitchOf(Vec3 vector) {
        return Mth.atan2(vector.y, vector.horizontalDistance());
    }

    public static float xRotOf(Vec3 vector) {
        return (float) -(pitchOf(vector) * Mth.RAD_TO_DEG);
    }

    public static float yawTowards(double dx, double dz) {
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
    }

    public static double projectedFraction(Vec3 origin, Vec3 span, double spanLengthSqr, Vec3 point) {
        return Mth.clamp(point.subtract(origin).dot(span) / spanLengthSqr, 0.0, 1.0);
    }

    public static Vec3 rotateAround(Vec3 vector, Vec3 axis, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return vector.scale(cos)
                .add(axis.cross(vector).scale(sin))
                .add(axis.scale(axis.dot(vector) * (1.0 - cos)));
    }
}
