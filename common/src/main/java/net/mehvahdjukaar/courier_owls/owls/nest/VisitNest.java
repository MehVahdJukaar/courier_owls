package net.mehvahdjukaar.courier_owls.owls.nest;

import net.mehvahdjukaar.courier_owls.bird.pathfinding.ArrivalHeading;
import net.mehvahdjukaar.courier_owls.bird.trip.ArrivalStyle;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.blocks.BirdNestBlock;
import net.mehvahdjukaar.courier_owls.owls.blocks.BirdNestBlockEntity;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class VisitNest extends Behavior<OwlEntity> {
    private static final int MAX_RUN_TICKS = 1200;

    private static final int CLOSE_ENOUGH = 0;

    private static final int DOORWAY_DWELL_TICKS = 25;

    private static final double ENTER_RANGE = 1.0;

    private static final double RESCUE_RANGE = 5.0;

    private static final double HOLD_RANGE = 2.0;

    private static final int MAX_TRIPS = 3;

    private static final float TURN_PER_TICK = 12.0F;

    private static final int HEADING_RUN = 2;

    private final float speedModifier;

    @Nullable
    private BlockPos targetedDoorway;

    private int ticksInDoorway;

    private int tripsAsked;

    public VisitNest(float speedModifier) {
        super(Map.of(
                OwlMod.VISIT_NEST.get(), MemoryStatus.VALUE_PRESENT,
                OwlMod.NEST_POS.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED),
                MAX_RUN_TICKS);
        this.speedModifier = speedModifier;
    }

    public static void sendHome(OwlEntity owl) {
        owl.getBrain().setMemory(OwlMod.VISIT_NEST.get(), Unit.INSTANCE);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, OwlEntity owl) {
        return canGoHome(owl);
    }

    @Override
    protected void start(ServerLevel level, OwlEntity owl, long gameTime) {
        this.ticksInDoorway = 0;
        this.tripsAsked = 0;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, OwlEntity owl, long gameTime) {
        return canGoHome(owl)
                && owl.getBrain().hasMemoryValue(OwlMod.NEST_POS.get())
                && (!owl.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                    || this.closeEnoughToHoverIn(owl));
    }

    private boolean closeEnoughToHoverIn(OwlEntity owl) {
        return this.targetedDoorway != null
                && owl.position().closerThan(Vec3.atBottomCenterOf(this.targetedDoorway), RESCUE_RANGE);
    }

    private static boolean canGoHome(OwlEntity owl) {
        return !owl.isOrderedToSit() && !owl.isAttending();
    }

    @Override
    protected void tick(ServerLevel level, OwlEntity owl, long gameTime) {
        BlockPos pos = NestClaim.of(owl);
        BirdNestBlockEntity nest = pos == null ? null : NestClaim.nestAt(level, pos);
        if (nest == null) {
            return;
        }

        Direction facing = level.getBlockState(pos).getValue(BirdNestBlock.FACING);
        BlockPos doorway = pos.relative(facing);
        if (!level.getBlockState(doorway).getCollisionShape(level, doorway).isEmpty()) {
            NestClaim.abandon(owl);
            return;
        }
        this.targetedDoorway = doorway;
        this.watchTheHole(owl, pos);
        Vec3 doorstep = Vec3.atBottomCenterOf(doorway);
        if (owl.position().closerThan(doorstep, ENTER_RANGE)) {
            if (nest.isOccupied()) {
                NestClaim.abandon(owl);
                return;
            }
            this.keepDoorwayNamed(owl, doorway);
            if (++this.ticksInDoorway >= DOORWAY_DWELL_TICKS && nest.tryEnter(owl)) {
                NestClaim.moveIn(level, pos);
            }
            return;
        }
        this.ticksInDoorway = 0;
        if (owl.isHovering()) {
            return;
        }
        boolean flightIsOver = !owl.trip().isTraveling() && this.closeEnoughToHoverIn(owl);
        boolean nothingLeftToFly = this.tripsAsked >= MAX_TRIPS || owl.position().closerThan(doorstep, HOLD_RANGE);
        if (flightIsOver && nothingLeftToFly) {
            owl.holdPosition(doorstep);
            return;
        }
        Brain<?> brain = owl.getBrain();
        WalkTarget current = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (current == null || !current.getTarget().currentBlockPosition().equals(doorway)) {
            Vec3 intoTheHole = Vec3.atLowerCornerOf(facing.getOpposite().getUnitVec3i());
            owl.trip().planArrival(doorway, ArrivalStyle.LAND_OR_HOVER,
                    ArrivalHeading.preferred(intoTheHole, HEADING_RUN));
            brain.setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(doorway, this.speedModifier, CLOSE_ENOUGH));
            this.tripsAsked++;
        }
    }

    private void watchTheHole(OwlEntity owl, BlockPos nest) {
        owl.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(nest));
        boolean standingStill = owl.isHovering() || (owl.isOnFoot() && !owl.trip().isTraveling());
        if (standingStill) {
            owl.turnToFace(Vec3.atCenterOf(nest), TURN_PER_TICK);
        }
    }

    private void keepDoorwayNamed(OwlEntity owl, BlockPos doorway) {
        if (!owl.blockPosition().equals(doorway) && !owl.isDrivenByBehavior()) {
            return;
        }
        owl.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(doorway, this.speedModifier, CLOSE_ENOUGH));
    }

    @Override
    protected void stop(ServerLevel level, OwlEntity owl, long gameTime) {
        owl.releaseHold();
        Brain<?> brain = owl.getBrain();
        brain.eraseMemory(OwlMod.VISIT_NEST.get());

        WalkTarget current = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        boolean someoneElseTookOver = current != null
                && !current.getTarget().currentBlockPosition().equals(this.targetedDoorway);
        if (!someoneElseTookOver) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
        this.targetedDoorway = null;
    }
}
