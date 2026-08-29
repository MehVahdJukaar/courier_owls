package net.mehvahdjukaar.courier_owls.bird.controller;

import net.mehvahdjukaar.courier_owls.bird.line.FlightLine;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record Landing(@Nullable Vec3 touchdown) {
    public static final Landing SETTLE = new Landing(null);

    private static final double SAME_FACE = 1.0E-4;

    public static Landing of(Mob mob, FlightLine line, GaitSettings gait) {
        if (!gait.crashLanding || line.points().length < 2) {
            return SETTLE;
        }
        Vec3 lineEnd = line.end();
        Vec3 touchdown = touchdownBelow(mob, lineEnd, gait);
        if (touchdown == null) {
            return SETTLE;
        }
        return new Landing(pulledBack(mob, touchdown, lineEnd, approachHeading(line), gait));
    }

    @Nullable
    private static Vec3 touchdownBelow(Mob mob, Vec3 from, GaitSettings gait) {
        double maxDrop = gait.perchProbeDepth;
        AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(from);
        Vec3 drop = new Vec3(0.0, -maxDrop, 0.0);
        double fell = Entity.collideBoundingBox(mob, drop, box, mob.level(), List.of()).y;
        return fell <= -maxDrop ? null : from.add(0.0, fell, 0.0);
    }

    private static Vec3 pulledBack(Mob mob, Vec3 touchdown, Vec3 lineEnd, @Nullable Vec3 approach,
                                   GaitSettings gait) {
        if (approach == null || gait.impactAimBack <= 0.0) {
            return touchdown;
        }
        Vec3 aim = lineEnd.subtract(approach.scale(gait.impactAimBack));
        Vec3 shifted = touchdownBelow(mob, aim, gait);
        return shifted != null && Math.abs(shifted.y - touchdown.y) < SAME_FACE ? shifted : touchdown;
    }

    @Nullable
    private static Vec3 approachHeading(FlightLine line) {
        Vec3[] points = line.points();
        Vec3 leg = points[points.length - 1].subtract(points[points.length - 2]);
        Vec3 horizontal = new Vec3(leg.x, 0.0, leg.z);
        return horizontal.lengthSqr() < FlightMath.DEGENERATE_LEG_SQR ? null : horizontal.normalize();
    }

    public boolean isImpact() {
        return this.touchdown != null;
    }

    public static boolean groundWithinReach(Mob mob, double depth) {
        return !mob.level().noCollision(mob, mob.getBoundingBox().expandTowards(0.0, -depth, 0.0));
    }
}
