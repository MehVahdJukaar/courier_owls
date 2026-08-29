package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.bird.entity.TameableBird;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.Map;

public class LandOnOwnersShoulder extends Behavior<TameableBird> {
    private static final int MAX_RUN_TICKS = 600;

    private static final int CLOSE_ENOUGH = 0;
    private static final double TOUCH_SLACK = 0.3;

    private final float speedModifier;
    private final float range;

    public LandOnOwnersShoulder(float speedModifier, float range) {
        super(Map.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
                MAX_RUN_TICKS);
        this.speedModifier = speedModifier;
        this.range = range;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, TameableBird bird) {
        return this.wantsToRide(bird);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, TameableBird bird, long gameTime) {
        return this.wantsToRide(bird);
    }

    private boolean wantsToRide(TameableBird bird) {
        if (bird.isOrderedToSit() || bird.isLeashed() || !bird.canSitOnShoulder()) {
            return false;
        }

        if (bird.isDrivenByBehavior() || bird.isOnErrand() || !bird.getMainHandItem().isEmpty()) {
            return false;
        }
        ServerPlayer owner = ownerAbleToCarry(bird);

        return owner != null && bird.distanceToSqr(owner) <= this.range * this.range;
    }

    @Override
    protected void tick(ServerLevel level, TameableBird bird, long gameTime) {
        ServerPlayer owner = ownerAbleToCarry(bird);
        if (owner == null) {
            return;
        }
        Brain<?> brain = bird.getBrain();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(owner, true));
        if (bird.getBoundingBox().inflate(TOUCH_SLACK).intersects(owner.getBoundingBox())) {
            bird.setEntityOnShoulder(owner);
            return;
        }
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new EntityTracker(owner, false), this.speedModifier, CLOSE_ENOUGH));
    }

    @Override
    protected void stop(ServerLevel level, TameableBird bird, long gameTime) {
        bird.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static ServerPlayer ownerAbleToCarry(TameableBird bird) {
        LivingEntity owner = bird.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return null;
        }
        boolean canCarry = !player.isSpectator() && !player.getAbilities().flying
                && !player.isInWater() && !player.isInPowderSnow;
        return canCarry ? player : null;
    }
}
