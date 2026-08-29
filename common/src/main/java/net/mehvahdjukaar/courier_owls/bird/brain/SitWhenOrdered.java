package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.bird.entity.TameableBird;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.Map;

public class SitWhenOrdered extends Behavior<TameableBird> {
    private static final int MAX_RUN_TICKS = 6000;

    private static final int CLOSE_ENOUGH = 0;
    private static final int RETRY_DELAY = 40;

    private final PerchSearch search;
    private final float speedModifier;
    private long nextSearchTime;

    public SitWhenOrdered(PerchSearch search, float speedModifier) {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), MAX_RUN_TICKS);
        this.search = search;
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, TameableBird bird) {
        return bird.isOrderedToSit();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, TameableBird bird, long gameTime) {
        return bird.isOrderedToSit();
    }

    @Override
    protected void tick(ServerLevel level, TameableBird bird, long gameTime) {
        Brain<?> brain = bird.getBrain();
        if (!bird.isOnFoot()) {
            bird.setInSittingPose(false);
            this.flyDownToPerch(bird, brain, gameTime);
            return;
        }
        bird.setInSittingPose(true);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }

    private void flyDownToPerch(TameableBird bird, Brain<?> brain, long gameTime) {
        if (brain.hasMemoryValue(MemoryModuleType.WALK_TARGET) || gameTime < this.nextSearchTime) {
            return;
        }
        this.nextSearchTime = gameTime + RETRY_DELAY;
        BlockPos perch = this.search.findPerch(bird);
        if (perch != null) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(perch, this.speedModifier, CLOSE_ENOUGH));
        }
    }

    @Override
    protected void stop(ServerLevel level, TameableBird bird, long gameTime) {
        bird.setInSittingPose(false);
    }
}
