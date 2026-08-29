package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record PerchSearch(int horizontalRange, int riseAbove, int dropBelow, int columnSamples) {
    public static final PerchSearch DEFAULT = new PerchSearch(8, 16, 12, 20);

    @Nullable
    public BlockPos findPerch(BaseBirdMob bird) {
        return this.findPerch(bird, bird.blockPosition());
    }

    @Nullable
    public BlockPos findPerch(BaseBirdMob bird, BlockPos center) {
        Level level = bird.level();
        RandomSource random = bird.getRandom();
        BlockPos birdPos = bird.blockPosition();
        int bandTop = birdPos.getY() + this.riseAbove;
        int bandBottom = Math.max(birdPos.getY() - this.dropBelow, level.getMinY() + 1);
        PathfindingContext context = new PathfindingContext(level, bird);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        List<BlockPos> aboveBird = new ArrayList<>();
        List<BlockPos> atOrBelowBird = new ArrayList<>();
        for (int i = 0; i < this.columnSamples; i++) {
            int x = center.getX() + random.nextIntBetweenInclusive(-this.horizontalRange, this.horizontalRange);
            int z = center.getZ() + random.nextIntBetweenInclusive(-this.horizontalRange, this.horizontalRange);
            BlockPos perch = highestPerchInColumn(bird, context, cursor, x, z, bandTop, bandBottom);
            if (perch == null || perch.equals(birdPos)) {
                continue;
            }
            (perch.getY() > birdPos.getY() ? aboveBird : atOrBelowBird).add(perch);
        }
        List<BlockPos> preferred = aboveBird.isEmpty() ? atOrBelowBird : aboveBird;
        return preferred.isEmpty() ? null : preferred.get(random.nextInt(preferred.size()));
    }

    @Nullable
    private BlockPos highestPerchInColumn(BaseBirdMob bird, PathfindingContext context,
                                          BlockPos.MutableBlockPos cursor, int x, int z,
                                          int bandTop, int bandBottom) {
        Level level = bird.level();
        cursor.set(x, bandBottom, z);
        if (!level.hasChunkAt(cursor)) {
            return null;
        }

        int surfaceStand = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (surfaceStand < bandBottom) {
            return null;
        }
        for (int y = Math.min(surfaceStand, bandTop); y >= bandBottom; y--) {
            cursor.set(x, y, z);
            if (isPerchableSpot(bird, context, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    public static boolean isPerchableSpot(BaseBirdMob bird, BlockPos pos) {
        return isPerchableSpot(bird, new PathfindingContext(bird.level(), bird), pos.mutable());
    }

    private static boolean isPerchableSpot(BaseBirdMob bird, PathfindingContext context,
                                           BlockPos.MutableBlockPos pos) {
        PathType type = WalkNodeEvaluator.getPathTypeStatic(context, pos);
        return type == PathType.WALKABLE
                && bird.getPathfindingMalus(type) >= 0.0F
                && bird.isWithinHome(pos);
    }
}
