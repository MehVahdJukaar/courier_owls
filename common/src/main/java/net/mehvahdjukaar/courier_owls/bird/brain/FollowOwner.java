package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.bird.entity.TameableBird;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.Map;

public class FollowOwner extends Behavior<TameableBird> {
    private static final int MAX_RUN_TICKS = 6000;

    private static final double TELEPORT_DISTANCE_SQ = 16.0 * 16.0;
    private static final int TELEPORT_INTERVAL = 10;

    private static final int TRAVEL_GRACE_TICKS = 200;
    private static final long NOT_FAR = -1L;

    private final float speedModifier;
    private final float startDistance;
    private final float stopDistance;
    private long farSince = NOT_FAR;

    public FollowOwner(float speedModifier, float startDistance, float stopDistance) {
        super(Map.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
                MAX_RUN_TICKS);
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, TameableBird bird) {
        return this.isTooFar(bird, this.startDistance);
    }

    @Override
    protected void start(ServerLevel level, TameableBird bird, long gameTime) {
        this.farSince = NOT_FAR;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, TameableBird bird, long gameTime) {
        return this.isTooFar(bird, this.stopDistance);
    }

    private boolean isTooFar(TameableBird bird, float distance) {
        if (bird.isDrivenByBehavior() || bird.isOnErrand()) {
            return false;
        }
        LivingEntity owner = bird.getOwner();
        return owner != null && !bird.unableToMoveToOwner()
                && bird.distanceToSqr(owner) > distance * distance;
    }

    @Override
    protected void tick(ServerLevel level, TameableBird bird, long gameTime) {
        LivingEntity owner = bird.getOwner();
        if (owner == null) {
            return;
        }
        Brain<?> brain = bird.getBrain();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(owner, true));
        if (bird.distanceToSqr(owner) < TELEPORT_DISTANCE_SQ) {
            this.farSince = NOT_FAR;
        } else {
            if (this.farSince == NOT_FAR) {
                this.farSince = gameTime;
            }
            if (this.cannotFlyThere(brain, gameTime) && gameTime % TELEPORT_INTERVAL == 0) {
                bird.tryToTeleportToOwner();

                brain.eraseMemory(MemoryModuleType.WALK_TARGET);

                brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                this.farSince = NOT_FAR;
                return;
            }
        }
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new EntityTracker(owner, false), this.speedModifier, (int) this.stopDistance));
    }

    private boolean cannotFlyThere(Brain<?> brain, long gameTime) {
        return brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                || gameTime - this.farSince > TRAVEL_GRACE_TICKS;
    }

    @Override
    protected void stop(ServerLevel level, TameableBird bird, long gameTime) {
        bird.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}
