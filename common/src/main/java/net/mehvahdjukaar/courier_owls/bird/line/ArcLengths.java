package net.mehvahdjukaar.courier_owls.bird.line;

import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ArcLengths {
    private final double[] distanceAt;

    private ArcLengths(double[] distanceAt) {
        this.distanceAt = distanceAt;
    }

    public static ArcLengths of(Vec3[] points) {
        double[] distanceAt = new double[points.length];
        for (int i = 1; i < points.length; i++) {
            distanceAt[i] = distanceAt[i - 1] + points[i].distanceTo(points[i - 1]);
        }
        return new ArcLengths(distanceAt);
    }

    public int nodeCount() {
        return this.distanceAt.length;
    }

    private int lastNode() {
        return this.distanceAt.length - 1;
    }

    public double length() {
        return this.distanceAt[this.lastNode()];
    }

    public double at(int node) {
        return this.distanceAt[Mth.clamp(node, 0, this.lastNode())];
    }

    public double legLength(int leg) {
        return this.at(leg + 1) - this.at(leg);
    }

    public int legAt(double distance) {
        return Math.max(0, Mth.binarySearch(0, this.nodeCount(), i -> this.distanceAt[i] > distance) - 1);
    }

    private double fractionAlong(int leg, double distance) {
        double legLength = this.distanceAt[leg + 1] - this.distanceAt[leg];
        return legLength < FlightMath.DEGENERATE_LEG ? 0.0 : (distance - this.distanceAt[leg]) / legLength;
    }

    public int nextNodeAfter(double distance) {
        if (distance < 0.0) {
            return 0;
        }
        return distance >= this.length() ? this.nodeCount() : this.legAt(distance) + 1;
    }

    public double lerpAt(double distance, double[] perNode) {
        double clamped = this.clamped(distance);
        int leg = this.legAt(clamped);
        return leg >= this.lastNode() ? perNode[this.lastNode()]
                : Mth.lerp(this.fractionAlong(leg, clamped), perNode[leg], perNode[leg + 1]);
    }

    public Vec3 pointAt(double distance, Vec3[] points) {
        double clamped = this.clamped(distance);
        int leg = this.legAt(clamped);
        if (leg >= this.lastNode()) {
            return points[this.lastNode()];
        }
        return points[leg].add(points[leg + 1].subtract(points[leg])
                .scale(this.fractionAlong(leg, clamped)));
    }

    private double clamped(double distance) {
        return Mth.clamp(distance, 0.0, this.length());
    }
}
