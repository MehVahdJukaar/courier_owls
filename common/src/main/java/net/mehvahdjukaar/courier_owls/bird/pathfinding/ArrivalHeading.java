package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class ArrivalHeading {
    public static final int MAX_RUN = 3;

    private static final int SLACK = 1;

    private static final double LEVEL_SLOPE = 0.25;

    private static final double MAX_ENTRY_CLIMB_SLOPE = 0.3;

    private final int bin;
    private final int climb;
    private final int run;
    private final boolean required;

    private ArrivalHeading(int bin, int climb, int run, boolean required) {
        this.bin = bin;
        this.climb = climb;
        this.run = run;
        this.required = required;
    }

    @Nullable
    public static ArrivalHeading required(@Nullable Vec3 direction, int run) {
        return of(direction, run, true);
    }

    @Nullable
    public static ArrivalHeading preferred(@Nullable Vec3 direction, int run) {
        return of(direction, run, false);
    }

    @Nullable
    private static ArrivalHeading of(@Nullable Vec3 direction, int run, boolean required) {
        if (direction == null || direction.horizontalDistanceSqr() < 1.0E-6) {
            return null;
        }
        return new ArrivalHeading(BirdNodeEvaluator.headingBinOf(direction.x, direction.z),
                climbSignOf(direction), Mth.clamp(run, 1, MAX_RUN), required);
    }

    private static int climbSignOf(Vec3 direction) {
        double slope = direction.y / direction.horizontalDistance();
        return slope > LEVEL_SLOPE ? 1 : slope < -LEVEL_SLOPE ? -1 : 0;
    }

    public int run() {
        return this.run;
    }

    public boolean isRequired() {
        return this.required;
    }

    private boolean allows(int dx, int dy, int dz) {
        if (dx == 0 && dz == 0) {
            return false;
        }
        double ground = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (this.climb < 0 && dy / ground > MAX_ENTRY_CLIMB_SLOPE) {
            return false;
        }
        return BirdNodeEvaluator.turnAmount(BirdNodeEvaluator.headingBinOf(dx, dz), this.bin) <= SLACK;
    }

    boolean endsFacing(Node node) {
        Node step = node;
        for (int i = 0; i < this.run; i++) {
            Node from = step.cameFrom;
            if (from == null) {
                return true;
            }
            if (!this.allows(step.x - from.x, step.y - from.y, step.z - from.z)) {
                return false;
            }
            step = from;
        }
        return true;
    }

    public boolean endsFacing(Path path) {
        int last = path.getNodeCount() - 1;
        for (int i = 0; i < this.run && last - i > 0; i++) {
            Node to = path.getNode(last - i);
            Node from = path.getNode(last - i - 1);
            if (!this.allows(to.x - from.x, to.y - from.y, to.z - from.z)) {
                return false;
            }
        }
        return true;
    }

    int binsFrom(int heading) {
        return Math.max(0, BirdNodeEvaluator.turnAmount(heading, this.bin) - SLACK);
    }
}
