package net.mehvahdjukaar.courier_owls.owls.swoop;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.CorridorRaycaster;
import net.mehvahdjukaar.courier_owls.bird.envelope.FlightEnvelope;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class SwoopGeometry {
    private static final double MIN_LEVEL_HEADING = 1.0E-3;

    private static final double STRIKE_FLOOR_LIFT = 0.05;

    static Vec3 strikePoint(BaseBirdMob mob, LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        double feetY = Math.max(center.y - mob.getBbHeight() * 0.5, target.getY() + STRIKE_FLOOR_LIFT);
        return new Vec3(center.x, feetY, center.z);
    }

    static double diveSpeedLimit(FlightEnvelope envelope, double out, double up) {
        double height = Math.max(0.0, up);
        double run = Math.max(0.0, out);
        double line = Math.sqrt(run * run + height * height);
        if (line < 1.0E-6) {
            return 0.0;
        }
        double sin = height / line;
        double radius = Double.MAX_VALUE;
        double drop = 1.0 - run / line;
        if (drop > 1.0E-6) {
            radius = Math.min(radius, height / drop);
        }
        if (sin > 1.0E-6) {
            radius = Math.min(radius, run / sin);
        }
        return Math.min(envelope.maxSpeedAt(-Math.asin(sin)), envelope.maxSpeedForArc(radius));
    }

    static double turnOnSpeedLimit(FlightEnvelope envelope, Vec3 velocity, Vec3 line) {
        double speed = velocity.length();
        double length = line.length();
        if (speed < 1.0E-4 || length < 1.0E-4) {
            return Double.MAX_VALUE;
        }
        double angle = Math.acos(Mth.clamp(velocity.dot(line) / (speed * length), -1.0, 1.0));
        double sin = Math.sin(Math.min(angle, Math.PI / 2.0));
        if (sin < 1.0E-3) {
            return Double.MAX_VALUE;
        }
        return envelope.maxSpeedForArc(length / (2.0 * sin));
    }

    static Vec3 levelHeading(Vec3 heading) {
        Vec3 level = new Vec3(heading.x, 0.0, heading.z);
        return level.lengthSqr() < MIN_LEVEL_HEADING * MIN_LEVEL_HEADING ? Vec3.ZERO : level.normalize();
    }

    static boolean diveClear(BaseBirdMob mob, Vec3 from, LivingEntity target) {
        Vec3 to = target.getBoundingBox().getCenter();
        return CorridorRaycaster.isOpen(mob, from, to, mob.getBbWidth() * 0.5);
    }
}
