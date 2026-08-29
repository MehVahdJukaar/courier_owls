package net.mehvahdjukaar.courier_owls.bird.line;

import net.mehvahdjukaar.courier_owls.bird.pathfinding.NodeClearance;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class PathSmoother {
    private static final double SAMPLE_SPACING = 0.25;

    private static final double MIN_TURN_ANGLE = Math.toRadians(1.0);

    private static final double MAX_TURN_ANGLE = Math.toRadians(175.0);
    private static final double MIN_TANGENT_LENGTH = 1.0E-4;
    private static final double DUPLICATE_SAMPLE_SQR = 1.0E-12;

    private static final double JOG_FRACTION = 0.35;

    private static final double MIN_ARC_SCALE = 0.2;

    private static final double TOUCH_EPSILON = 1.0E-3;

    private static final double HUGGED_CORNER_HOLD = 0.4;

    private final FlightLine raw;
    private final Vec3[] points;
    private final NodeClearance[] clearance;
    private final double maxDeviation;
    private final double roomFraction;
    private final double halfWidth;
    private final double height;
    private final Corner[] corners;
    private final Bends bends;

    public static FlightLine smooth(FlightLine raw, double maxDeviation, double roomFraction,
                                    double halfWidth, double height) {
        if (raw.points().length < 3 || maxDeviation <= 0.0) {
            return raw;
        }
        return new PathSmoother(raw, maxDeviation, roomFraction, halfWidth, height).smooth();
    }

    private PathSmoother(FlightLine raw, double maxDeviation, double roomFraction, double halfWidth, double height) {
        this.raw = raw;
        this.points = raw.points();
        this.clearance = raw.clearance();
        this.maxDeviation = maxDeviation;
        this.roomFraction = roomFraction;
        this.halfWidth = halfWidth;
        this.height = height;
        this.corners = new Corner[points.length];
        for (int i = 1; i < points.length - 1; i++) {
            corners[i] = Corner.at(points[i - 1], points[i], points[i + 1]);
        }
        this.bends = Bends.of(corners);
    }

    private FlightLine smooth() {
        int count = points.length;
        Fillet[] fillets = new Fillet[count];
        for (int i = 1; i < count - 1; i++) {
            if (corners[i] != null) {
                fillets[i] = filletAt(i);
            }
        }

        Builder out = new Builder(count);
        out.addStraight(points[0], 0);
        for (int i = 1; i < count; i++) {
            if (fillets[i] != null) {
                emitFillet(out, fillets[i], i);
                continue;
            }

            boolean swallowed = swallowedBy(fillets, bends.previous[i], i) || swallowedBy(fillets, bends.next[i], i);
            if (!swallowed) {
                out.addStraight(points[i], i);
            }
        }
        return out.toLine(raw);
    }

    private boolean swallowedBy(Fillet[] fillets, int bend, int i) {
        Fillet fillet = fillets[bend];
        return fillet != null && fillet.tangentLength > points[i].distanceTo(points[bend]);
    }

    private Fillet filletAt(int i) {
        Corner corner = corners[i];
        double insideBudget = Math.min(maxDeviation, clearance[i].getRoomAlong(corner.inside) * roomFraction);
        if (corner.isNearReversal()) {
            return corner.asPoint(corner.radiusForDeviation(insideBudget), 0.0);
        }
        double run = bends.shortestRunAt(points, i);

        double tangentCap = 0.5 * run;

        double insideScale = 0.0;
        Fillet insideArc = null;
        for (double scale = 1.0; scale > MIN_ARC_SCALE; scale *= 0.5) {
            Fillet candidate = corner.fillet(insideBudget * scale, tangentCap, 0.0);
            if (!candidate.isRounded() || !arcTouchesBlockedCell(candidate, i)) {
                insideScale = scale;
                insideArc = candidate;
                break;
            }
        }
        double insideDeviation = insideBudget * insideScale;

        Fillet wideEntry = wideEntryAt(i, run, insideDeviation);
        if (wideEntry != null) {
            return wideEntry;
        }
        if (insideArc != null) {
            boolean shrunk = insideArc.isRounded() && insideScale < 1.0;
            return shrunk ? insideArc.withHoldFloor(HUGGED_CORNER_HOLD) : insideArc;
        }

        return corner.asPoint(corner.radiusForDeviation(insideBudget * MIN_ARC_SCALE), HUGGED_CORNER_HOLD);
    }

    private Fillet wideEntryAt(int i, double run, double insideDeviation) {
        Corner corner = corners[i];
        double openDeviation = Math.min(maxDeviation, NodeClearance.CELL_ROOM * roomFraction);
        double starvation = openDeviation <= 0.0 ? 0.0 : Mth.clamp(1.0 - insideDeviation / openDeviation, 0.0, 1.0);
        double sharpness = Math.min(1.0, corner.turnAngle / Mth.HALF_PI);
        Vec3 outside = corner.inside.reverse();
        double outRoom = Math.min(clearance[i].getRoomAlong(outside),
                Math.min(clearance[i - 1].getRoomAlong(outside), clearance[i + 1].getRoomAlong(outside)));
        double outwardBudget = starvation * sharpness
                * Mth.clamp(maxDeviation - insideDeviation, 0.0, outRoom * roomFraction);
        if (outwardBudget <= 0.0) {
            return null;
        }
        double pushCap = JOG_FRACTION * run * corner.halfAngleSecant();
        for (double scale = 1.0; scale > MIN_ARC_SCALE; scale *= 0.5) {
            double push = Math.min(outwardBudget * scale, pushCap);

            Corner pushed = Corner.at(points[i - 1], corner.point.add(outside.scale(push)), points[i + 1]);
            if (pushed == null || pushed.isNearReversal()) {
                return null;
            }

            Fillet candidate = pushed.fillet(0.5 * (push + insideDeviation), 0.5 * pushed.shorterLeg, HUGGED_CORNER_HOLD);
            if (candidate.isRounded() && !arcTouchesBlockedCell(candidate, i)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean arcTouchesBlockedCell(Fillet fillet, int node) {
        for (Vec3 sample : fillet.arcSamples()) {
            if (boxTouchesBlockedCell(sample, node)) {
                return true;
            }
        }
        return false;
    }

    private boolean boxTouchesBlockedCell(Vec3 sample, int node) {
        int minX = Mth.floor(sample.x - halfWidth + TOUCH_EPSILON);
        int maxX = Mth.floor(sample.x + halfWidth - TOUCH_EPSILON);
        int minY = Mth.floor(sample.y + TOUCH_EPSILON);
        int maxY = Mth.floor(sample.y + height - TOUCH_EPSILON);
        int minZ = Mth.floor(sample.z - halfWidth + TOUCH_EPSILON);
        int maxZ = Mth.floor(sample.z + halfWidth - TOUCH_EPSILON);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (cellBlocked(x, y, z, node)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean cellBlocked(int x, int y, int z, int node) {
        boolean blocked = false;
        for (int j = Math.max(0, node - 1); j <= Math.min(points.length - 1, node + 1); j++) {
            int dx = x - Mth.floor(points[j].x);
            int dy = y - Mth.floor(points[j].y);
            int dz = z - Mth.floor(points[j].z);
            if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || Math.abs(dz) > 1) {
                continue;
            }
            if (dx == 0 && dy == 0 && dz == 0) {
                return false;
            }
            blocked |= clearance[j].shell().blockedAt(dx, dy, dz);
        }
        return blocked;
    }

    private void emitFillet(Builder out, Fillet fillet, int node) {
        if (!fillet.isRounded()) {
            out.addOnFillet(fillet.corner.point, fillet, node);
            out.nodeArcs[node] = out.runningArc;
            return;
        }
        Vec3[] arc = fillet.arcSamples();
        out.addOnFillet(arc[0], fillet, node);

        double arcStartArc = out.runningArc;
        for (int j = bends.previous[node - 1] + 1; j < node; j++) {
            double behindCorner = points[j].distanceTo(points[node]);
            if (behindCorner < fillet.tangentLength) {
                out.nodeArcs[j] = arcStartArc + (fillet.tangentLength - behindCorner);
            }
        }
        int midStep = (arc.length - 1) / 2;
        for (int k = 1; k < arc.length; k++) {
            out.addOnFillet(arc[k], fillet, node);
            if (k == midStep) {
                out.nodeArcs[node] = out.runningArc;
            }
        }

        double arcEndArc = out.runningArc;
        for (int j = node + 1; j < bends.next[node + 1]; j++) {
            double pastCorner = points[j].distanceTo(points[node]);
            if (pastCorner < fillet.tangentLength) {
                out.nodeArcs[j] = arcEndArc - (fillet.tangentLength - pastCorner);
            }
        }
    }

    private record Bends(int[] previous, int[] next) {
        static Bends of(Corner[] corners) {
            int count = corners.length;
            int[] previous = new int[count];
            int[] next = new int[count];
            for (int i = 0, last = 0; i < count; i++) {
                if (isBend(corners, i)) last = i;
                previous[i] = last;
            }
            for (int i = count - 1, following = count - 1; i >= 0; i--) {
                if (isBend(corners, i)) following = i;
                next[i] = following;
            }
            return new Bends(previous, next);
        }

        private static boolean isBend(Corner[] corners, int i) {
            return i == 0 || i == corners.length - 1 || corners[i] != null;
        }

        double shortestRunAt(Vec3[] points, int i) {
            return Math.min(points[i].distanceTo(points[previous[i - 1]]),
                    points[i].distanceTo(points[next[i + 1]]));
        }
    }

    private record Corner(Vec3 point, Vec3 arriving, Vec3 leaving, Vec3 inside, double turnAngle, double shorterLeg) {
        static Corner at(Vec3 before, Vec3 point, Vec3 after) {
            Vec3 in = point.subtract(before);
            Vec3 out = after.subtract(point);
            if (in.lengthSqr() < FlightMath.DEGENERATE_LEG_SQR || out.lengthSqr() < FlightMath.DEGENERATE_LEG_SQR) {
                return null;
            }
            Vec3 arriving = in.normalize();
            Vec3 leaving = out.normalize();
            double turnAngle = Math.acos(Mth.clamp(arriving.dot(leaving), -1.0, 1.0));
            if (turnAngle < MIN_TURN_ANGLE) {
                return null;
            }
            Vec3 inside = leaving.subtract(arriving).normalize();
            return new Corner(point, arriving, leaving, inside, turnAngle, Math.min(in.length(), out.length()));
        }

        boolean isNearReversal() {
            return turnAngle > MAX_TURN_ANGLE;
        }

        double halfAngleSecant() {
            return 1.0 / Math.cos(Math.min(turnAngle, MAX_TURN_ANGLE) / 2.0);
        }

        double radiusForDeviation(double deviation) {
            return deviation / (halfAngleSecant() - 1.0);
        }

        Fillet fillet(double deviation, double tangentCap, double holdFloor) {
            double halfAngleTangent = Math.tan(turnAngle / 2.0);
            double radius = radiusForDeviation(deviation);
            double tangentLength = radius * halfAngleTangent;
            if (tangentLength > tangentCap) {
                tangentLength = tangentCap;
                radius = tangentLength / halfAngleTangent;
            }
            if (tangentLength < MIN_TANGENT_LENGTH) {
                return asPoint(radius, holdFloor);
            }
            return new Fillet(this, radius, tangentLength, holdFloor);
        }

        Fillet asPoint(double radius, double holdFloor) {
            return new Fillet(this, radius, 0.0, holdFloor);
        }
    }

    private record Fillet(Corner corner, double radius, double tangentLength, double holdFloor) {
        boolean isRounded() {
            return tangentLength > 0.0;
        }

        Fillet withHoldFloor(double floor) {
            return new Fillet(corner, radius, tangentLength, floor);
        }

        Vec3[] arcSamples() {
            Vec3 center = corner.point.add(corner.inside.scale(radius * corner.halfAngleSecant()));
            Vec3 arcStart = corner.point.subtract(corner.arriving.scale(tangentLength));
            Vec3 fromCenter = arcStart.subtract(center);
            Vec3 axis = corner.arriving.cross(corner.leaving).normalize();
            Vec3 sideways = axis.cross(fromCenter);
            int steps = Math.max(2, Mth.ceil(radius * corner.turnAngle / SAMPLE_SPACING));
            Vec3[] samples = new Vec3[steps + 1];
            for (int k = 0; k <= steps; k++) {
                double swept = corner.turnAngle * k / steps;
                samples[k] = center.add(fromCenter.scale(Math.cos(swept))).add(sideways.scale(Math.sin(swept)));
            }
            return samples;
        }
    }

    private record Sample(Vec3 point, double radius, int node, double holdFloor) {
    }

    private static final class Builder {
        private final List<Sample> samples = new ArrayList<>();
        final double[] nodeArcs;
        double runningArc;

        Builder(int originalCount) {
            this.nodeArcs = new double[originalCount];
        }

        void addStraight(Vec3 point, int node) {
            this.add(point, FlightLine.STRAIGHT, node, 0.0);
            this.nodeArcs[node] = this.runningArc;
        }

        void addOnFillet(Vec3 point, Fillet fillet, int node) {
            this.add(point, fillet.radius, node, fillet.holdFloor);
        }

        private void add(Vec3 point, double radius, int node, double holdFloor) {
            if (!this.samples.isEmpty()) {
                Sample last = this.samples.getLast();
                double stepSqr = point.distanceToSqr(last.point);
                if (stepSqr < DUPLICATE_SAMPLE_SQR) {
                    this.samples.set(this.samples.size() - 1, new Sample(last.point,
                            Math.min(last.radius, radius), last.node, Math.max(last.holdFloor, holdFloor)));
                    return;
                }
                this.runningArc += Math.sqrt(stepSqr);
            }
            this.samples.add(new Sample(point, radius, node, holdFloor));
        }

        FlightLine toLine(FlightLine raw) {
            Vec3[] points = this.samples.stream().map(Sample::point).toArray(Vec3[]::new);

            double[] hold = this.samples.stream()
                    .mapToDouble(s -> Math.max(raw.hold()[s.node], s.holdFloor)).toArray();
            NodeClearance[] clearance = this.samples.stream()
                    .map(s -> raw.clearance()[s.node]).toArray(NodeClearance[]::new);
            double[] radii = this.samples.stream().mapToDouble(Sample::radius).toArray();
            return new FlightLine(points, hold, clearance, ArcLengths.of(points), radii, this.nodeArcs);
        }
    }
}
