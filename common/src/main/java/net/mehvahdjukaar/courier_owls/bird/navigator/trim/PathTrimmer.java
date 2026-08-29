package net.mehvahdjukaar.courier_owls.bird.navigator.trim;

import net.mehvahdjukaar.courier_owls.configs.AblationSwitches;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.NodePlacementUtil;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.CorridorRaycaster;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.ArrivalHeading;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.BirdNode;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PathTrimmer {
    private final Path path;
    private final Mob mob;
    private final PathTrimSettings settings;
    private final double corridorMargin;

    private final int goalRail;
    private final int count;
    private final boolean[] keep;

    public PathTrimmer(Path path, Mob mob, PathTrimSettings settings, double corridorMargin,
                       @Nullable ArrivalHeading arrival) {
        this.path = path;
        this.mob = mob;
        this.settings = settings;
        this.corridorMargin = corridorMargin;
        this.goalRail = arrival == null ? settings.goalRailNodes
                : Math.max(settings.goalRailNodes, arrival.run());
        this.count = path.getNodeCount();
        this.keep = new boolean[this.count];
        Arrays.fill(this.keep, true);
    }

    public Path trim() {
        if (!AblationSwitches.trimSearchedPaths || this.count < this.settings.minChordInterior + 2) {
            return this.path;
        }
        boolean trimmed = false;

        int lastPrunableEnd = this.count - 1 - this.goalRail;
        int i = 0;
        while (i < this.count) {
            if (!readsOpen(this.path.getNode(i))) {
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < this.count && readsOpen(this.path.getNode(j + 1))) {
                j++;
            }

            int from = i == 0 ? 0 : bufferedStart(i, j);
            int to = Math.min(j == this.count - 1 ? j : bufferedEnd(i, j), lastPrunableEnd);
            if (to - from - 1 >= this.settings.minChordInterior) {
                trimmed |= chordRun(from, to);
            }
            i = j + 1;
        }
        if (!trimmed) {
            return this.path;
        }
        List<Node> nodes = new ArrayList<>(this.count);
        for (int k = 0; k < this.count; k++) {
            if (this.keep[k]) {
                nodes.add(this.path.getNode(k));
            }
        }
        return new Path(nodes, this.path.getTarget(), this.path.canReach());
    }

    private boolean chordRun(int from, int to) {
        Vec3 start = flownPoint(from);
        Vec3 end = flownPoint(to);
        double span = start.distanceTo(end);
        double detour = detourOf(from, to);

        if (detour < this.settings.minChordDetour) {
            return false;
        }
        CorridorRaycaster.CorridorEnd whole = null;
        if (detour <= maxDetourFor(from, to) && !spiralsDown(from, to)) {
            whole = CorridorRaycaster.clipCorridor(this.mob, start, end, span, this.corridorMargin);
            if (whole.open() >= span) {
                prune(from, to);
                return true;
            }
        }
        if (span <= this.settings.splitMinSpan || !fullyOpenRun(from, to)) {
            return false;
        }

        int split = splitNodeFor(from, to, start, end,
                whole != null ? whole.open() : 0.0);
        boolean cut = false;
        if (split - from - 1 >= this.settings.minChordInterior
                && worthCasting(from, split)
                && chordIsOpen(from, split)) {
            prune(from, split);
            cut = true;
        }
        if (to - split - 1 >= this.settings.minChordInterior
                && worthCasting(split, to)
                && chordIsOpen(split, to)) {
            prune(split, to);
            cut = true;
        }
        return cut;
    }

    private boolean worthCasting(int from, int to) {
        double detour = detourOf(from, to);
        return detour >= this.settings.minChordDetour
                && detour <= maxDetourFor(from, to);
    }

    private double maxDetourFor(int from, int to) {
        boolean climbs = flownPoint(to).y > flownPoint(from).y;
        return climbs ? this.settings.maxClimbDetour : this.settings.maxChordDetour;
    }

    private boolean spiralsDown(int from, int to) {
        if (flownPoint(from).y - flownPoint(to).y <= 0.0) {
            return false;
        }
        double sweep = 0.0;
        double headingX = 0.0;
        double headingZ = 0.0;
        boolean hasHeading = false;
        Vec3 previous = flownPoint(from);
        for (int k = from + 1; k <= to; k++) {
            Vec3 next = flownPoint(k);
            double stepX = next.x - previous.x;
            double stepZ = next.z - previous.z;
            previous = next;

            if (stepX * stepX + stepZ * stepZ < 1.0E-8) {
                continue;
            }
            if (hasHeading) {
                sweep += Math.atan2(headingX * stepZ - headingZ * stepX,
                        headingX * stepX + headingZ * stepZ);
            }
            headingX = stepX;
            headingZ = stepZ;
            hasHeading = true;
        }
        return Math.abs(Math.toDegrees(sweep)) > this.settings.maxDescentWinding;
    }

    private double detourOf(int from, int to) {
        Vec3 start = flownPoint(from);
        double railLength = 0.0;
        Vec3 previous = start;
        for (int k = from + 1; k <= to; k++) {
            Vec3 next = flownPoint(k);
            railLength += previous.distanceTo(next);
            previous = next;
        }
        double span = start.distanceTo(previous);
        return span < 1.0E-6 ? Double.MAX_VALUE : railLength / span;
    }

    private int splitNodeFor(int from, int to,
                                    Vec3 start, Vec3 end, double openedFor) {
        Vec3 axis = end.subtract(start).normalize();
        int best = -1;
        double bestDeviation = -1.0;
        int fallback = from + 1;
        double fallbackDeviation = -1.0;
        for (int k = from + 1; k < to; k++) {
            Vec3 away = flownPoint(k).subtract(start);
            double along = away.dot(axis);
            double deviation = away.subtract(axis.scale(along)).length();
            if (deviation > fallbackDeviation) {
                fallback = k;
                fallbackDeviation = deviation;
            }
            if (along >= openedFor && deviation > bestDeviation) {
                best = k;
                bestDeviation = deviation;
            }
        }
        return best >= 0 ? best : fallback;
    }

    private void prune(int from, int to) {
        for (int k = from + 1; k < to; k++) {
            this.keep[k] = false;
        }
    }

    private boolean fullyOpenRun(int from, int to) {
        for (int k = from; k <= to; k++) {
            if (!fullyOpen(this.path.getNode(k))) {
                return false;
            }
        }
        return true;
    }

    private int bufferedStart(int runStart, int runEnd) {
        int cleanRail = 0;
        for (int k = runStart; k <= runEnd; k++) {
            cleanRail = fullyOpen(this.path.getNode(k)) ? cleanRail + 1 : 0;
            if (cleanRail >= this.settings.bufferNodes) {
                return k + 1;
            }
        }
        return runEnd + 1;
    }

    private int bufferedEnd(int runStart, int runEnd) {
        int cleanRail = 0;
        for (int k = runEnd; k >= runStart; k--) {
            cleanRail = fullyOpen(this.path.getNode(k)) ? cleanRail + 1 : 0;
            if (cleanRail >= this.settings.bufferNodes) {
                return k - 1;
            }
        }
        return runStart - 1;
    }

    private boolean readsOpen(Node node) {
        return !(node instanceof BirdNode bird)
                || bird.enclosure.fraction() <= this.settings.openAirEnclosure;
    }

    private boolean fullyOpen(Node node) {
        return !(node instanceof BirdNode bird) || bird.enclosure.fraction() <= 0.0F;
    }

    private boolean chordIsOpen(int from, int to) {
        if (spiralsDown(from, to)) {
            return false;
        }
        Vec3 start = flownPoint(from);
        Vec3 end = flownPoint(to);
        double span = start.distanceTo(end);
        return CorridorRaycaster.clipCorridor(this.mob, start, end, span, this.corridorMargin)
                .open() >= span;
    }

    private Vec3 flownPoint(int index) {
        return NodePlacementUtil.flownPointOf(this.path, this.mob, index);
    }
}
