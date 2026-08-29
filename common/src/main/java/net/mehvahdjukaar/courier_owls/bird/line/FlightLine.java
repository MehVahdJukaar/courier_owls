package net.mehvahdjukaar.courier_owls.bird.line;

import net.mehvahdjukaar.courier_owls.bird.pathfinding.NodeClearance;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.NodePlacementUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public record FlightLine(Vec3[] points, double[] hold, NodeClearance[] clearance, ArcLengths arc,
                         double[] turnRadius, double[] nodeArcs) {
    public static final double STRAIGHT = Double.MAX_VALUE;

    public static FlightLine of(Path path, Entity entity) {
        int count = path.getNodeCount();
        Vec3[] points = new Vec3[count];
        double[] hold = new double[count];
        NodeClearance[] clearance = new NodeClearance[count];
        for (int i = 0; i < count; i++) {
            points[i] = NodePlacementUtil.flownPointOf(path, entity, i);
            clearance[i] = NodeClearance.of(path.getNode(i), entity);
            hold[i] = clearance[i].shell().fraction();
        }
        return raw(points, hold, clearance);
    }

    private static FlightLine raw(Vec3[] points, double[] hold, NodeClearance[] clearance) {
        ArcLengths arc = ArcLengths.of(points);
        double[] turnRadius = new double[points.length];
        Arrays.fill(turnRadius, STRAIGHT);
        double[] nodeArcs = new double[points.length];
        Arrays.setAll(nodeArcs, arc::at);
        return new FlightLine(points, hold, clearance, arc, turnRadius, nodeArcs);
    }

    public double length() {
        return this.arc.length();
    }

    public Vec3 end() {
        return this.points[this.points.length - 1];
    }

    public FlightLine withEndAt(Vec3 touchdown) {
        int last = this.points.length - 1;
        if (last < 1) {
            return this;
        }
        double[] landedHold = this.hold.clone();
        landedHold[last] = 1.0;
        NodeClearance[] landedClearance = this.clearance.clone();
        landedClearance[last] = landedClearance[last].withNoRoomBelow();
        return this.withEndMovedTo(touchdown, landedHold, landedClearance);
    }

    public FlightLine withEndLifted(double up) {
        int last = this.points.length - 1;
        if (up <= 0.0 || last < 1) {
            return this;
        }
        return this.withEndMovedTo(this.points[last].add(0.0, up, 0.0), this.hold, this.clearance);
    }

    private FlightLine withEndMovedTo(Vec3 end, double[] hold, NodeClearance[] clearance) {
        Vec3[] moved = this.points.clone();
        moved[moved.length - 1] = end;
        return raw(moved, hold, clearance);
    }

    public Vec3 pointAt(double distance) {
        return this.arc.pointAt(distance, this.points);
    }

    public double holdAt(double distance) {
        return this.arc.lerpAt(distance, this.hold);
    }

    public double pullInAhead(double from, double reach, double taper, double caution) {
        double worst = pullInDemand(this.holdAt(from), caution);
        double horizon = from + reach + Math.max(taper, 0.0);
        for (int i = this.arc.legAt(from) + 1; i < this.points.length && this.arc.at(i) <= horizon; i++) {
            double past = this.arc.at(i) - from - reach;
            double weight = past <= 0.0 ? 1.0 : taper > 0.0 ? 1.0 - past / taper : 0.0;
            worst = Math.max(worst, pullInDemand(this.hold[i], caution) * weight);
        }
        return worst;
    }

    private static double pullInDemand(double hold, double caution) {
        return Math.min(1.0, hold * caution);
    }

    public int nextOriginalNodeAfter(double distance) {
        return Mth.binarySearch(0, this.nodeArcs.length, i -> this.nodeArcs[i] > distance);
    }
}
