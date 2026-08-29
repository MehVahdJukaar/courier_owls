package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class CorridorRaycaster {
    public record CorridorEnd(double open, @Nullable AABB blockedAt) {
    }

    public static CorridorEnd clipCorridor(Mob mob, Vec3 from, Vec3 to, double maxScan, double margin) {
        Vec3 away = to.subtract(from);
        double span = Math.min(away.length(), maxScan);
        if (span <= 0.0) {
            return new CorridorEnd(0.0, null);
        }
        Vec3 heading = away.normalize();

        AABB corridor = mob.getBoundingBox().inflate(margin, 0.0, margin);

        double reachAlongHeading = Math.abs(heading.x) * corridor.getXsize()
                + Math.abs(heading.y) * corridor.getYsize()
                + Math.abs(heading.z) * corridor.getZsize();
        double stride = reachAlongHeading * 0.5;
        Vec3 boxAt = mob.position();
        double open = 0.0;
        while (open < span) {
            double along = Math.min(open + stride, span);
            Vec3 probe = from.add(heading.scale(along));
            AABB sample = corridor.move(probe.subtract(boxAt));
            if (!mob.level().noBlockCollision(mob, sample)) {
                return new CorridorEnd(open, sample);
            }
            open = along;
        }
        return new CorridorEnd(span, null);
    }

    public static boolean isOpen(Mob mob, Vec3 from, Vec3 to, double margin) {
        double reach = to.distanceTo(from);
        return clipCorridor(mob, from, to, reach, margin).open() >= reach;
    }

    public static boolean fitsAt(Mob mob, Vec3 at) {
        AABB box = mob.getBoundingBox().move(at.subtract(mob.position()));
        return mob.level().noBlockCollision(mob, box);
    }
}
