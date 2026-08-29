package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import io.netty.buffer.ByteBuf;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;

public record NodeClearance(Enclosure shell, double horizontal, double up, double down) {
    public static final StreamCodec<ByteBuf, NodeClearance> STREAM_CODEC = StreamCodec.composite(
            Enclosure.STREAM_CODEC, NodeClearance::shell,
            ByteBufCodecs.FLOAT, (NodeClearance c) -> (float) c.horizontal(),
            ByteBufCodecs.FLOAT, (NodeClearance c) -> (float) c.up(),
            ByteBufCodecs.FLOAT, (NodeClearance c) -> (float) c.down(),
            (Enclosure shell, Float horizontal, Float up, Float down) ->
                    new NodeClearance(shell, horizontal, up, down));

    public static final double CELL_ROOM = 1.0;

    public static NodeClearance of(Node node, Entity mob) {
        double inCellHorizontal = NodePlacementUtil.horizontalRoom(mob);
        double inCellVertical = NodePlacementUtil.verticalOffset(mob);

        Enclosure shell = node instanceof BirdNode bird ? bird.enclosure : Enclosure.OPEN;
        return new NodeClearance(shell,
                room(shell.blockedFraction(0), inCellHorizontal),
                room(shell.blockedFraction(1), inCellVertical),
                room(shell.blockedFraction(-1), inCellVertical));
    }

    private static double room(double blockedFraction, double inCellRoom) {
        return Mth.lerp(Mth.clamp(blockedFraction, 0.0, 1.0), CELL_ROOM, inCellRoom);
    }

    public NodeClearance withNoRoomBelow() {
        return new NodeClearance(this.shell, this.horizontal, this.up, 0.0);
    }

    public double getRoomAlong(Vec3 direction) {
        double length = direction.length();
        if (length < FlightMath.NEAR_ZERO) {
            return 0.0;
        }
        double vertical = direction.y >= 0.0 ? this.up : this.down;
        return 1.0 / Math.sqrt(axisTerm(direction.horizontalDistance() / length, this.horizontal)
                + axisTerm(direction.y / length, vertical));
    }

    private static double axisTerm(double component, double radius) {
        if (component == 0.0) {
            return 0.0;
        }
        double ratio = component / radius;
        return ratio * ratio;
    }
}
