package net.mehvahdjukaar.courier_owls.bird.navigator.direct;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DirectPath extends Path {
    @Nullable
    private final BlockPos finalDestination;

    static DirectPath between(BlockPos from, BlockPos to) {
        return new DirectPath(List.of(openNode(from), openNode(to)), to, null);
    }

    static DirectPath partial(BlockPos from, BlockPos stopAt, BlockPos destination) {
        return new DirectPath(List.of(openNode(from), openNode(stopAt)), stopAt, destination);
    }

    @Nullable
    public BlockPos finalDestination() {
        return this.finalDestination;
    }

    private DirectPath(List<Node> nodes, BlockPos target, @Nullable BlockPos destination) {
        super(nodes, target, true);
        this.finalDestination = destination;
    }

    private static Node openNode(BlockPos pos) {
        Node node = new Node(pos.getX(), pos.getY(), pos.getZ());

        node.type = PathType.OPEN;
        return node;
    }
}
