package net.mehvahdjukaar.courier_owls.owls.entities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Predicate;

public class WatchNearby extends Behavior<OwlEntity> {
    private static final int MAX_RUN_TICKS = 400;

    private final float range;
    private final UniformInt watchDuration;
    private final int playerNoticeTicks;
    private final int preyNoticeTicks;
    private final int otherNoticeTicks;
    @Nullable
    private LivingEntity watched;
    private int ticksLeft;

    public WatchNearby(float range, UniformInt watchDuration, int playerNoticeTicks,
                       int preyNoticeTicks, int otherNoticeTicks) {
        super(Map.of(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
                MAX_RUN_TICKS);
        this.range = range;
        this.watchDuration = watchDuration;
        this.playerNoticeTicks = playerNoticeTicks;
        this.preyNoticeTicks = preyNoticeTicks;
        this.otherNoticeTicks = otherNoticeTicks;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, OwlEntity owl) {
        if (!this.canWatch(owl)) {
            return false;
        }
        this.watched = this.pick(level, owl);
        return this.watched != null;
    }

    @Override
    protected void start(ServerLevel level, OwlEntity owl, long gameTime) {
        this.ticksLeft = this.watchDuration.sample(owl.getRandom());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, OwlEntity owl, long gameTime) {
        return this.ticksLeft > 0
                && this.watched != null
                && this.watched.isAlive()
                && this.canWatch(owl)
                && this.inRange(owl, this.watched);
    }

    @Override
    protected void tick(ServerLevel level, OwlEntity owl, long gameTime) {
        this.ticksLeft--;
        owl.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(this.watched, true));
    }

    @Override
    protected void stop(ServerLevel level, OwlEntity owl, long gameTime) {
        this.watched = null;
        this.ticksLeft = 0;
    }

    private boolean canWatch(OwlEntity owl) {
        return owl.isOnFoot()
                && !owl.isSleeping()
                && !owl.isOnErrand()
                && !owl.isAttending()
                && !owl.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Nullable
    private LivingEntity pick(ServerLevel level, OwlEntity owl) {
        LivingEntity player = this.rolledFor(owl, this.playerNoticeTicks, WatchNearby::isPlayer);
        if (player != null) {
            return player;
        }
        LivingEntity prey = this.rolledFor(owl, this.preyNoticeTicks, candidate -> isPrey(level, owl, candidate));
        if (prey != null) {
            return prey;
        }
        return this.rolledFor(owl, this.otherNoticeTicks,
                candidate -> !isPlayer(candidate) && !isPrey(level, owl, candidate));
    }

    @Nullable
    private LivingEntity rolledFor(OwlEntity owl, int oneIn, Predicate<LivingEntity> wanted) {
        return owl.getRandom().nextInt(oneIn) == 0 ? this.nearest(owl, wanted) : null;
    }

    private static boolean isPlayer(LivingEntity candidate) {
        return candidate instanceof Player;
    }

    private static boolean isPrey(ServerLevel level, OwlEntity owl, LivingEntity candidate) {
        return OwlPreySensor.isPrey(level, owl, candidate);
    }

    @Nullable
    private LivingEntity nearest(OwlEntity owl, Predicate<LivingEntity> wanted) {
        NearestVisibleLivingEntities seen = owl.getBrain()
                .getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(null);
        if (seen == null) {
            return null;
        }
        return seen.findClosest(candidate -> this.inRange(owl, candidate) && wanted.test(candidate))
                .orElse(null);
    }

    private boolean inRange(OwlEntity owl, LivingEntity target) {
        return target.distanceToSqr(owl) <= this.range * this.range;
    }
}
