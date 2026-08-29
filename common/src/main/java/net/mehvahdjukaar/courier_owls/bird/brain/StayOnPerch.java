package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;

public class StayOnPerch extends Behavior<BaseBirdMob> {
    private static final int TRAVEL_BUDGET_TICKS = 1200;

    private static final int CLOSE_ENOUGH = 0;

    private static final int VALIDITY_CHECK_INTERVAL = 20;

    private static final int MAX_TIMES_KNOCKED_OFF = 2;

    private static final int KNOCKED_OFF_GRACE = 20;
    private static final int NOT_LANDED_YET = -1;

    private final Activity activity;
    private final float speedModifier;
    private final UniformInt stayDuration;
    private int ticksLeftOnPerch = NOT_LANDED_YET;
    private int ticksOffPerch;
    private int timesKnockedOff;

    public StayOnPerch(Activity activity, float speedModifier, UniformInt stayDuration) {
        super(Map.of(
                BirdMod.PERCH_POS.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED,
                MemoryModuleType.HURT_BY, MemoryStatus.VALUE_ABSENT),
                stayDuration.maxInclusive() + TRAVEL_BUDGET_TICKS);
        this.activity = activity;
        this.speedModifier = speedModifier;
        this.stayDuration = stayDuration;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, BaseBirdMob bird) {
        return this.freeToPerch(bird);
    }

    @Override
    protected void start(ServerLevel level, BaseBirdMob bird, long gameTime) {
        this.ticksLeftOnPerch = NOT_LANDED_YET;
        this.ticksOffPerch = 0;
        this.timesKnockedOff = 0;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, BaseBirdMob bird, long gameTime) {
        if (this.ticksLeftOnPerch == 0 || this.timesKnockedOff > MAX_TIMES_KNOCKED_OFF) {
            return false;
        }

        if (!bird.getBrain().isActive(this.activity)) {
            return false;
        }
        if (!this.freeToPerch(bird)) {
            return false;
        }
        Brain<?> brain = bird.getBrain();

        if (brain.hasMemoryValue(MemoryModuleType.HURT_BY)) {
            return false;
        }

        if (brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            return false;
        }
        BlockPos perch = brain.getMemory(BirdMod.PERCH_POS.get()).orElse(null);
        if (perch == null || walkTargetIsElsewhere(brain, perch)) {
            return false;
        }
        return gameTime % VALIDITY_CHECK_INTERVAL != 0 || PerchSearch.isPerchableSpot(bird, perch);
    }

    @Override
    protected void tick(ServerLevel level, BaseBirdMob bird, long gameTime) {
        Brain<?> brain = bird.getBrain();
        BlockPos perch = brain.getMemory(BirdMod.PERCH_POS.get()).orElseThrow();
        if (isStandingOnPerch(bird, perch)) {
            this.ticksOffPerch = 0;
            this.ticksLeftOnPerch = this.ticksLeftOnPerch == NOT_LANDED_YET
                    ? this.stayDuration.sample(bird.getRandom())
                    : this.ticksLeftOnPerch - 1;
        } else {
            boolean wasDown = this.ticksLeftOnPerch != NOT_LANDED_YET;
            if (wasDown && ++this.ticksOffPerch > KNOCKED_OFF_GRACE) {
                this.timesKnockedOff++;
                this.ticksLeftOnPerch = NOT_LANDED_YET;
                this.ticksOffPerch = 0;
            }
            if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                brain.setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(perch, this.speedModifier, CLOSE_ENOUGH));
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, BaseBirdMob bird, long gameTime) {
        this.ticksLeftOnPerch = NOT_LANDED_YET;
        this.ticksOffPerch = 0;
        this.timesKnockedOff = 0;
        Brain<?> brain = bird.getBrain();
        BlockPos perch = brain.getMemory(BirdMod.PERCH_POS.get()).orElse(null);

        if (perch != null && !walkTargetIsElsewhere(brain, perch)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        brain.eraseMemory(BirdMod.PERCH_POS.get());

        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    private boolean freeToPerch(BaseBirdMob bird) {
        return bird.canMoveOnItsOwn() && !bird.isDrivenByBehavior()
                && !bird.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    private static boolean walkTargetIsElsewhere(Brain<?> brain, BlockPos perch) {
        return brain.getMemory(MemoryModuleType.WALK_TARGET)
                .filter(target -> !target.getTarget().currentBlockPosition().equals(perch))
                .isPresent();
    }

    private static boolean isStandingOnPerch(BaseBirdMob bird, BlockPos perch) {
        return bird.isOnFoot() && bird.blockPosition().equals(perch);
    }
}
