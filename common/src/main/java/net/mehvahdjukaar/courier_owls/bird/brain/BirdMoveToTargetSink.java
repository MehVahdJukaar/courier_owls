package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class BirdMoveToTargetSink extends MoveToTargetSink {
    private static final int MAX_COOLDOWN_BEFORE_RETRYING = 40;

    private static final double TARGET_DRIFT_SQR = 4.0;

    private int cooldown;

    @Nullable
    private BlockPos cooldownTargetPos;
    @Nullable
    private BlockPos lastTargetPos;

    @Nullable
    private Path mirroredPath;

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Mob mob) {
        Brain<?> brain = mob.getBrain();
        WalkTarget target = brain.getMemory(MemoryModuleType.WALK_TARGET).get();

        if (((BaseBirdMob) mob).isDrivenByBehavior()) {
            return false;
        }

        if (!((BaseBirdMob) mob).canMoveOnItsOwn()) {
            return false;
        }

        if (this.cooldown > 0) {
            if (target.getTarget().currentBlockPosition().equals(this.cooldownTargetPos)) {
                this.cooldown--;
                return false;
            }
            this.cooldown = 0;
            this.cooldownTargetPos = null;
        }
        if (isWalkTargetSpectator(target)) {
            return false;
        }

        if (reachedTarget(mob, target)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel level, Mob mob, long gameTime) {
        this.startTripTo((BaseBirdMob) mob, mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET).get());
    }

    private void startTripTo(BaseBirdMob bird, WalkTarget target) {
        this.lastTargetPos = target.getTarget().currentBlockPosition();

        bird.trip().walkOrFlyTo(this.lastTargetPos, target.getCloseEnoughDist(), target.getSpeedModifier());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Mob mob, long gameTime) {
        Optional<WalkTarget> memory = mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        if (memory.isEmpty() || isWalkTargetSpectator(memory.get())) {
            return false;
        }
        BaseBirdMob bird = (BaseBirdMob) mob;
        return bird.canMoveOnItsOwn() && bird.trip().isTraveling() && !reachedTarget(mob, memory.get());
    }

    @Override
    protected void tick(ServerLevel level, Mob mob, long gameTime) {
        Brain<?> brain = mob.getBrain();
        this.mirrorPathMemory(mob, brain);
        WalkTarget target = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
        BlockPos pos = target.getTarget().currentBlockPosition();
        if (this.lastTargetPos == null || pos.distSqr(this.lastTargetPos) > TARGET_DRIFT_SQR) {
            this.startTripTo((BaseBirdMob) mob, target);
        }
    }

    @Override
    protected void stop(ServerLevel level, Mob mob, long gameTime) {
        BaseBirdMob bird = (BaseBirdMob) mob;
        Brain<?> brain = bird.getBrain();
        WalkTarget target = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        boolean arrived = target != null && reachedTarget(bird, target);
        if (!bird.trip().isTraveling() && bird.trip().lastTripFailed() && !arrived) {
            if (!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
                brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, gameTime);
            }
            this.cooldown = level.getRandom().nextInt(MAX_COOLDOWN_BEFORE_RETRYING);
            this.cooldownTargetPos = this.lastTargetPos;
        } else if (arrived) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }

        boolean ownsCurrentTrip = this.lastTargetPos == null
                || !bird.trip().isTraveling()
                || bird.trip().isTravelingTo(this.lastTargetPos);
        if (ownsCurrentTrip) {
            bird.trip().cancel();
            bird.getNavigation().stop();
        }
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        this.lastTargetPos = null;
        this.mirroredPath = null;
    }

    private void mirrorPathMemory(Mob mob, Brain<?> brain) {
        Path path = mob.getNavigation().getPath();
        if (path == this.mirroredPath) {
            return;
        }
        this.mirroredPath = path;
        if (path != null) {
            brain.setMemory(MemoryModuleType.PATH, path);
        } else {
            brain.eraseMemory(MemoryModuleType.PATH);
        }
    }

    private static boolean reachedTarget(Mob mob, WalkTarget target) {
        return !((BaseBirdMob) mob).trip().isTraveling()
                && target.getTarget().currentBlockPosition().distManhattan(mob.blockPosition()) <= target.getCloseEnoughDist();
    }

    private static boolean isWalkTargetSpectator(WalkTarget target) {
        return target.getTarget() instanceof EntityTracker tracker && tracker.getEntity().isSpectator();
    }
}
