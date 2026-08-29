package net.mehvahdjukaar.courier_owls.owls.delivery;

import net.mehvahdjukaar.courier_owls.bird.brain.DirectPursuit;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.ArrivalHeading;
import net.mehvahdjukaar.courier_owls.bird.trip.ArrivalStyle;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public class AttendPlayer extends Behavior<OwlEntity> {
    private static final float PIPELINE_SPEED = 1.0F;

    private static final double CAPTURE = 0.8;

    private static final double DRIFTED = 1.75;

    private static final double KEEP_SPOT_RANGE = 3.0;

    private static final float TURN_PER_TICK = 6.0F;

    private static final int PIPELINE_RETRY_DELAY = 20;

    private static final int PIPELINE_STALL_TICKS = 60;

    private static final int LINE_RECHECK = 5;

    private static final int ARRIVAL_RUN = 2;

    private enum Mode {IDLE, PIPELINE, PURSUIT, HOVER}

    @Nullable
    private Vec3 spot;
    private Mode mode = Mode.IDLE;
    private long nextPipelineTime;
    private int pipelineIdleTicks;
    private int ticksToLineRecheck;

    public AttendPlayer() {
        super(Map.of(
                OwlMod.ATTENDED_PLAYER.get(), MemoryStatus.VALUE_PRESENT,

                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, OwlEntity owl) {
        return !owl.isOrderedToSit() && !owl.isBaby() && !owl.isDrivenByBehavior()
                && this.attended(owl).isPresent();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, OwlEntity owl, long gameTime) {
        return !owl.isOrderedToSit() && !owl.isBaby() && this.attended(owl).isPresent();
    }

    @Override
    protected void start(ServerLevel level, OwlEntity owl, long gameTime) {
        this.nextPipelineTime = 0L;
        Player player = this.attended(owl).orElse(null);
        if (player != null) {
            this.spot = AttendSpot.pick(player, owl);
            if (!owl.isOnFoot() && DirectPursuit.lineIsClear(owl, this.spot)) {
                this.toPursuit(owl);
            } else {
                this.toPipeline(owl, player);
            }
        }
    }

    @Override
    protected void tick(ServerLevel level, OwlEntity owl, long gameTime) {
        Player player = this.attended(owl).orElse(null);
        if (player == null) {
            return;
        }
        boolean playerWithinReach = owl.position().closerThan(player.position(), KEEP_SPOT_RANGE);
        boolean spotStale = this.spot == null || (!playerWithinReach && !AttendSpot.stillBeside(this.spot, player, owl));
        switch (this.mode) {
            case PIPELINE -> this.tickPipeline(owl, player, spotStale);
            case PURSUIT -> this.tickPursuit(owl, player, spotStale);
            case HOVER -> this.tickHover(owl, player, spotStale, gameTime);
        }
    }

    @Override
    protected void stop(ServerLevel level, OwlEntity owl, long gameTime) {
        this.leaveMode(owl);
        this.spot = null;
    }

    private void leaveMode(OwlEntity owl) {
        switch (this.mode) {
            case PIPELINE -> owl.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            case PURSUIT -> owl.releaseFlight();
            case HOVER -> owl.releaseHold();

            case IDLE -> {
            }
        }
        this.mode = Mode.IDLE;
    }

    private void toPipeline(OwlEntity owl, Player player) {
        this.leaveMode(owl);
        this.mode = Mode.PIPELINE;
        this.pipelineIdleTicks = 0;
        this.sendToSpot(owl, player);
    }

    private void sendToSpot(OwlEntity owl, Player player) {
        Vec3 facing = player.position().subtract(this.spot).multiply(1.0, 0.0, 1.0);
        owl.trip().planArrival(BlockPos.containing(this.spot), ArrivalStyle.IN_FLIGHT,
                ArrivalHeading.required(facing, ARRIVAL_RUN));
        owl.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(this.spot, PIPELINE_SPEED, 0));
    }

    private void tickPipeline(OwlEntity owl, Player player, boolean spotStale) {
        if (spotStale) {
            this.spot = AttendSpot.pick(player, owl);
            this.sendToSpot(owl, player);
            return;
        }
        boolean sinkDone = !owl.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET);
        boolean stalled = !owl.trip().isTraveling() && ++this.pipelineIdleTicks > PIPELINE_STALL_TICKS;
        if (owl.trip().isTraveling()) {
            this.pipelineIdleTicks = 0;
        }
        if (sinkDone || stalled || owl.position().closerThan(this.spot, CAPTURE)) {
            this.toHover(owl);
        }
    }

    private void toPursuit(OwlEntity owl) {
        this.leaveMode(owl);
        this.mode = Mode.PURSUIT;
        this.ticksToLineRecheck = LINE_RECHECK;
        DirectPursuit.steer(owl, this.spot);
    }

    private void tickPursuit(OwlEntity owl, Player player, boolean spotStale) {
        if (spotStale) {
            this.spot = AttendSpot.pick(player, owl);
            this.ticksToLineRecheck = 0;
        }
        if (owl.position().closerThan(this.spot, CAPTURE)) {
            this.toHover(owl);
            return;
        }
        if (--this.ticksToLineRecheck <= 0) {
            this.ticksToLineRecheck = LINE_RECHECK;
            if (!DirectPursuit.lineIsClear(owl, this.spot)) {
                this.toPipeline(owl, player);
                return;
            }
        }
        if (!DirectPursuit.steer(owl, this.spot)) {
            this.toPipeline(owl, player);
        }
    }

    private void toHover(OwlEntity owl) {
        this.leaveMode(owl);
        this.mode = Mode.HOVER;
        owl.holdPosition(this.spot);
    }

    private void tickHover(OwlEntity owl, Player player, boolean spotStale, long gameTime) {
        boolean drifted = this.spot != null && !owl.position().closerThan(this.spot, DRIFTED);
        if (spotStale || drifted) {
            Vec3 fresh = AttendSpot.pick(player, owl);
            if (DirectPursuit.lineIsClear(owl, fresh)) {
                this.spot = fresh;
                this.toPursuit(owl);
                return;
            }
            if (gameTime >= this.nextPipelineTime) {
                this.nextPipelineTime = gameTime + PIPELINE_RETRY_DELAY;
                this.spot = fresh;
                this.toPipeline(owl, player);
                return;
            }
        }

        owl.holdPosition(this.spot);
        this.watch(owl, player);
    }

    private void watch(OwlEntity owl, Player player) {
        owl.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
        owl.turnToFace(player.position(), TURN_PER_TICK);
    }

    private Optional<Player> attended(OwlEntity owl) {
        return owl.getBrain().getMemory(OwlMod.ATTENDED_PLAYER.get())
                .filter(player -> player.isAlive() && !player.isRemoved() && !player.isSpectator());
    }
}
