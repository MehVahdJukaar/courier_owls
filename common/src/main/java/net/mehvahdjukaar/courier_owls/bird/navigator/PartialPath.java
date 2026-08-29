package net.mehvahdjukaar.courier_owls.bird.navigator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.List;

public class PartialPath extends Path {
    private final BlockPos finalDestination;

    private PartialPath(List<Node> nodes, BlockPos target, boolean reached, BlockPos finalDestination) {
        super(nodes, target, reached);
        this.finalDestination = finalDestination;
    }

    public static PartialPath of(Path searched, BlockPos finalDestination) {
        List<Node> nodes = new ArrayList<>(searched.getNodeCount());
        for (int i = 0; i < searched.getNodeCount(); i++) {
            nodes.add(searched.getNode(i));
        }
        return new PartialPath(nodes, searched.getTarget(), searched.canReach(), finalDestination);
    }

    public boolean headsTo(BlockPos destination) {
        return this.finalDestination.equals(destination);
    }
}
