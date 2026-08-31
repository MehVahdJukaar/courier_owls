package net.mehvahdjukaar.courier_owls.bird.navigator.direct;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.CorridorRaycaster;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.NodePlacementUtil;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.ArrivalHeading;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.PathfindingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class DirectPathRouter {
    private final BaseBirdMob mob;
    private final DirectFlightSettings settings;
    private final PathfindingSettings search;

    private DirectVerdict verdict = DirectVerdict.NONE;

    public DirectPathRouter(BaseBirdMob mob) {
        this.mob = mob;
        this.settings = mob.settings().direct();
        this.search = mob.settings().search();
    }

    @Nullable
    public DirectPath routeTo(BlockPos target, Vec3 from, @Nullable Path current,
                              @Nullable ArrivalHeading arrival) {
        if (this.mob.trip().isDirectDemoted()) {
            this.verdict = DirectVerdict.demoted();
            return null;
        }

        if (current instanceof DirectPath flying && stillHeadingTo(flying, target)
                && arrivesRight(flying, arrival)) {
            return flying;
        }
        DirectPath whole = DirectPath.between(this.mob.blockPosition(), target);
        Vec3 to = NodePlacementUtil.flownPointOf(whole, this.mob, 1);
        double reach = from.distanceTo(to);
        if (reach < this.settings.minDistance) {
            this.verdict = DirectVerdict.tooClose(reach);
            return null;
        }

        if (arrival != null && arrival.isRequired() && (!arrivesRight(whole, arrival) || reach > this.search.searchRange)) {
            this.verdict = DirectVerdict.wrongArrival(reach);
            return null;
        }
        if (reach <= this.search.searchRange) {
            CorridorRaycaster.CorridorEnd corridor = CorridorRaycaster.clipCorridor(
                    this.mob, from, to, reach, this.settings.corridorMargin);
            if (corridor.open() >= reach) {
                this.verdict = DirectVerdict.flown(reach);
                return whole;
            }
            this.verdict = DirectVerdict.blocked(reach, corridor.open(), corridor.blockedAt());
            return null;
        }

        CorridorRaycaster.CorridorEnd corridor = CorridorRaycaster.clipCorridor(this.mob, from, to,
                this.settings.scanCap, this.settings.corridorMargin);
        if (corridor.open() < this.settings.minPartialLeg) {
            this.verdict = DirectVerdict.partialTooShort(reach, corridor.open(), corridor.blockedAt());
            return null;
        }
        this.verdict = DirectVerdict.partial(reach, corridor.open());

        Vec3 stopAt = from.add(to.subtract(from).normalize().scale(corridor.open() - 1.0));
        return DirectPath.partial(this.mob.blockPosition(), BlockPos.containing(stopAt), target);
    }

    private static boolean arrivesRight(DirectPath leg, @Nullable ArrivalHeading arrival) {
        return arrival == null || !arrival.isRequired()
                || (leg.finalDestination() == null && arrival.endsFacing(leg));
    }

    private static boolean stillHeadingTo(DirectPath flying, BlockPos target) {
        return !flying.isDone()
                && (flying.getTarget().equals(target) || target.equals(flying.finalDestination()));
    }
}
