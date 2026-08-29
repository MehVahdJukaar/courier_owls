package net.mehvahdjukaar.courier_owls.owls.nest;

import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

import java.util.Map;

public class TendNest extends Behavior<OwlEntity> {
    private static final int CHECK_INTERVAL = 40;

    private static final int HOME_URGE_ODDS = 30;

    public TendNest() {
        super(Map.of(OwlMod.NEST_POS.get(), MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, OwlEntity owl) {
        return level.getGameTime() % CHECK_INTERVAL == 0;
    }

    @Override
    protected void start(ServerLevel level, OwlEntity owl, long gameTime) {
        NestClaim.validate(owl);
        BlockPos nest = NestClaim.of(owl);
        if (nest == null && !owl.isTame() && owl.getRandom().nextInt(HOME_URGE_ODDS) == 0) {
            nest = NestClaim.findFreeHollow(level, owl);
            if (nest != null && !NestClaim.claim(owl, nest)) {
                nest = null;
            }
            if (nest != null) {
                VisitNest.sendHome(owl);
            }
        }
        if (nest != null && !owl.isTame() && !owl.isVisitingNest() && !owl.hasToStaysOutOfNest()
                && level.isRainingAt(owl.blockPosition())) {
            VisitNest.sendHome(owl);
        }
    }
}
