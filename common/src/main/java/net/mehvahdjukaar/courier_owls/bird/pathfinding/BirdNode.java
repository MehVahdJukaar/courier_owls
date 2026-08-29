package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import net.minecraft.world.level.pathfinder.Node;

public class BirdNode extends Node {
    public final int heading;

    public final int climb;

    public final boolean freeHeading;

    public final Enclosure enclosure;

    public boolean startInFlight;

    public BirdNode(int x, int y, int z, int heading, int climb, boolean freeHeading, Enclosure enclosure) {
        super(x, y, z);
        this.heading = heading;
        this.climb = climb;
        this.freeHeading = freeHeading;
        this.enclosure = enclosure;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BirdNode node && super.equals(other) && this.heading == node.heading
                && this.climb == node.climb && this.freeHeading == node.freeHeading;
    }

    @Override
    public int hashCode() {
        return ((super.hashCode() * 31 + this.heading) * 31 + this.climb) * 31 + (this.freeHeading ? 1 : 0);
    }
}
