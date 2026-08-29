package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class NodePlacementUtil {
    public static double verticalOffset(Entity entity) {
        return (Mth.floor(entity.getBbHeight() + 1.0F) - entity.getBbHeight()) * 0.5;
    }

    public static double horizontalRoom(Entity entity) {
        return Math.max(0.0, (1.0 - entity.getBbWidth()) * 0.5);
    }

    public static Vec3 flownPointOf(Path path, Entity entity, int index) {
        return path.getEntityPosAtNode(entity, index).add(0.0, verticalOffset(entity), 0.0);
    }

    public static Vec3 flownPointOf(BlockPos cell, Entity entity) {
        double middle = (int) (entity.getBbWidth() + 1.0F) * 0.5;
        return new Vec3(cell.getX() + middle, cell.getY() + verticalOffset(entity), cell.getZ() + middle);
    }
}
