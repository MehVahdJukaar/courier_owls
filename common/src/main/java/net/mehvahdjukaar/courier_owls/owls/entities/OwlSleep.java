package net.mehvahdjukaar.courier_owls.owls.entities;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.nest.NestClaim;
import net.mehvahdjukaar.courier_owls.owls.nest.VisitNest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;

public class OwlSleep extends Behavior<OwlEntity> {
    private static final int MAX_RUN_TICKS = 24000;

    private static final int STARTLED_TICKS = 400;

    private static final double OWNER_BEDSIDE_RANGE_SQR = 16.0 * 16.0;

    private final Activity activity;
    private long wakeUntil;

    public OwlSleep(Activity activity) {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), MAX_RUN_TICKS);
        this.activity = activity;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, OwlEntity owl) {
        return level.getGameTime() >= this.wakeUntil;
    }

    @Override
    protected void start(ServerLevel level, OwlEntity owl, long gameTime) {
        if (this.isTheDayShift() && !owl.isTame() && !owl.hasToStaysOutOfNest() && NestClaim.of(owl) != null) {
            VisitNest.sendHome(owl);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, OwlEntity owl, long gameTime) {
        if (!owl.getBrain().isActive(this.activity)) {
            return false;
        }
        if (owl.hurtTime > 0) {
            this.wakeUntil = gameTime + STARTLED_TICKS;
            return false;
        }
        return true;
    }

    @Override
    protected void tick(ServerLevel level, OwlEntity owl, long gameTime) {
        owl.setSleeping(this.mayDozeOff(owl) && isSettled(owl) && !somethingElseWantsTheOwl(owl));
    }

    private static boolean isSettled(OwlEntity owl) {
        Brain<?> brain = owl.getBrain();
        if (!owl.isOnFoot()) {
            brain.eraseMemory(OwlMod.ROOST_POS.get());
            return false;
        }

        if (owl.isTame() || !owl.canMoveByFlying()) {
            return true;
        }
        BlockPos here = owl.blockPosition();
        if (brain.getMemory(OwlMod.ROOST_POS.get()).filter(here::equals).isPresent()) {
            return true;
        }

        if (brain.getMemory(BirdMod.PERCH_POS.get()).filter(here::equals).isPresent()) {
            brain.setMemory(OwlMod.ROOST_POS.get(), here);
            return true;
        }
        brain.eraseMemory(OwlMod.ROOST_POS.get());
        return false;
    }

    private boolean isTheDayShift() {
        return this.activity == Activity.REST;
    }

    private boolean mayDozeOff(OwlEntity owl) {
        if (!owl.isTame() || owl.isBaby()) {
            return this.isTheDayShift();
        }
        return ownerIsInBed(owl) || (this.isTheDayShift() && owl.isOrderedToSit());
    }

    private static boolean ownerIsInBed(OwlEntity owl) {
        LivingEntity owner = owl.getOwner();
        return owner != null && owner.isSleeping()
                && owl.distanceToSqr(owner) <= OWNER_BEDSIDE_RANGE_SQR;
    }

    @Override
    protected void stop(ServerLevel level, OwlEntity owl, long gameTime) {
        owl.getBrain().eraseMemory(OwlMod.ROOST_POS.get());
        owl.setSleeping(false);
    }

    private static boolean somethingElseWantsTheOwl(OwlEntity owl) {
        Brain<?> brain = owl.getBrain();
        return owl.isAttending()
                || owl.isVisitingNest()
                || brain.getMemory(MemoryModuleType.IS_TEMPTED).orElse(false)
                || brain.hasMemoryValue(MemoryModuleType.WALK_TARGET);
    }
}
