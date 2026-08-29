package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.pathfinder.Node;

public record EdgeCost(float distance, float malus, float enclosure, float vertical, float turn, float pitch) {
    public static final EdgeCost NONE = new EdgeCost(0, 0, 0, 0, 0, 0);

    public static final StreamCodec<ByteBuf, EdgeCost> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, EdgeCost::distance,
            ByteBufCodecs.FLOAT, EdgeCost::malus,
            ByteBufCodecs.FLOAT, EdgeCost::enclosure,
            ByteBufCodecs.FLOAT, EdgeCost::vertical,
            ByteBufCodecs.FLOAT, EdgeCost::turn,
            ByteBufCodecs.FLOAT, EdgeCost::pitch,
            EdgeCost::new);

    public static EdgeCost between(Node from, Node to, PathfindingSettings settings) {
        float enclosure = (to instanceof BirdNode bird ? bird.enclosure.fraction() : 0)
                * settings.enclosureCost;

        float vertical = from.x == to.x && from.z == to.z && from.y != to.y
                ? settings.straightVerticalCost : 0;

        float turn = 0;
        float pitch = 0;
        if (from instanceof BirdNode a && to instanceof BirdNode b && !a.freeHeading) {
            turn = ladderCost(a.startInFlight ? settings.startTurnCostByBin : settings.turnCostByBin,
                    BirdNodeEvaluator.turnAmount(a.heading, b.heading));
            pitch = ladderCost(settings.pitchCostByBin,
                    BirdNodeEvaluator.pitchAmount(a.climb, b.climb));
        }
        return new EdgeCost(from.distanceTo(to), to.costMalus, enclosure, vertical, turn, pitch);
    }

    private static float ladderCost(float[] ladder, int bins) {
        return ladder.length == 0 ? 0.0F : ladder[Math.min(bins, ladder.length - 1)];
    }

    static float cheapestTurnPerBin(PathfindingSettings settings) {
        float[] ladder = settings.turnCostByBin;
        if (ladder.length == 0) {
            return 0.0F;
        }
        float cheapest = Float.MAX_VALUE;
        for (int bins = 1; bins <= BirdNodeEvaluator.HEADING_BINS / 2; bins++) {
            cheapest = Math.min(cheapest, ladderCost(ladder, bins) / bins);
        }
        return cheapest;
    }

    public float extras() {
        return this.enclosure + this.vertical + this.turn + this.pitch;
    }

    public float total() {
        return this.distance + this.malus + this.extras();
    }

    public EdgeCost plus(EdgeCost other) {
        return new EdgeCost(this.distance + other.distance, this.malus + other.malus,
                this.enclosure + other.enclosure, this.vertical + other.vertical,
                this.turn + other.turn, this.pitch + other.pitch);
    }
}
