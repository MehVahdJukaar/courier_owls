package net.mehvahdjukaar.courier_owls.owls.delivery;

import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PuffSpot {
    private static final double MIN_RADIUS = 6.0;
    private static final double MAX_RADIUS = 12.0;

    private static final double MIN_RISE = 3.0;
    private static final double MAX_RISE = 12.0;

    private static final int SAMPLES = 12;
    private static final int SEARCHES = 4;

    private static final int ACCURACY = 2;

    private static final int COLUMN_RISE = 12;
    private static final int LAST_DITCH_REACH = 4;

    @Nullable
    public static BlockPos toLeaveFrom(ServerLevel level, OwlEntity owl) {
        int searched = 0;
        for (BlockPos spot : openSpotsAround(level, owl, owl.position())) {
            if (++searched > SEARCHES) {
                break;
            }
            if (canFlyBetween(owl, spot)) {
                return spot;
            }
        }
        return null;
    }

    @Nullable
    public static BlockPos toArriveAt(ServerLevel level, OwlEntity owl, Vec3 destination) {
        BlockPos flownIn = flyableSpotAround(level, owl, destination);
        if (flownIn != null) {
            return flownIn;
        }
        BlockPos above = roomAbove(level, destination);
        return above != null ? above : anyRoomNear(level, owl, destination);
    }

    @Nullable
    private static BlockPos flyableSpotAround(ServerLevel level, OwlEntity owl, Vec3 destination) {
        BlockPos target = BlockPos.containing(destination);
        Vec3 wasAt = owl.position();
        int searched = 0;
        try {
            for (BlockPos spot : openSpotsAround(level, owl, destination)) {
                if (++searched > SEARCHES) {
                    break;
                }
                owl.snapTo(Vec3.atBottomCenterOf(spot));
                if (canFlyBetween(owl, target)) {
                    return spot;
                }
            }
            return null;
        } finally {
            owl.snapTo(wasAt);
        }
    }

    @Nullable
    private static BlockPos roomAbove(ServerLevel level, Vec3 destination) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(destination).mutable().move(0, COLUMN_RISE, 0);
        for (int i = 0; i < COLUMN_RISE; i++) {
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                return pos.immutable();
            }
            pos.move(0, -1, 0);
        }
        return null;
    }

    @Nullable
    private static BlockPos anyRoomNear(ServerLevel level, OwlEntity owl, Vec3 around) {
        BlockPos middle = BlockPos.containing(around);
        for (BlockPos spot : BlockPos.withinManhattan(middle, LAST_DITCH_REACH, LAST_DITCH_REACH, LAST_DITCH_REACH)) {
            if (hasRoom(level, owl, spot)) {
                return spot.immutable();
            }
        }
        return null;
    }

    private static List<BlockPos> openSpotsAround(ServerLevel level, OwlEntity owl, Vec3 around) {
        RandomSource random = owl.getRandom();
        List<BlockPos> underSky = new ArrayList<>();
        List<BlockPos> underCover = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double radius = Mth.lerp(random.nextDouble(), MIN_RADIUS, MAX_RADIUS);
            double rise = Mth.lerp(random.nextDouble(), MIN_RISE, MAX_RISE);
            BlockPos spot = BlockPos.containing(
                    around.x + Math.cos(angle) * radius,
                    around.y + rise,
                    around.z + Math.sin(angle) * radius);
            if (hasRoom(level, owl, spot)) {
                (level.canSeeSky(spot) ? underSky : underCover).add(spot);
            }
        }
        underSky.addAll(underCover);
        return underSky;
    }

    private static boolean hasRoom(ServerLevel level, OwlEntity owl, BlockPos spot) {
        return level.noCollision(owl, owl.getDimensions(owl.getPose())
                .makeBoundingBox(Vec3.atBottomCenterOf(spot)));
    }

    private static boolean canFlyBetween(OwlEntity owl, BlockPos target) {
        owl.flightNavigation().setArrivalHeading(null);
        Path path = owl.flightNavigation().createPath(target, ACCURACY);
        return path != null && path.canReach();
    }
}
