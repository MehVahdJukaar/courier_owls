package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.entity.TameableBird;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

public class FindPerch extends Behavior<BaseBirdMob> {
    private static final int STARTLE_COOLDOWN_TICKS = 100;

    private final PerchSearch search;
    private final int retryDelayTicks;

    private final int groundedUrgeTicks;
    @Nullable
    private final Function<BaseBirdMob, BlockPos> homeAnchor;
    private final double homeRange;
    private final double homeHop;
    private long nextSearchTime;

    public FindPerch(PerchSearch search, int retryDelayTicks, int groundedUrgeTicks) {
        this(search, retryDelayTicks, groundedUrgeTicks, null, 0.0, 0.0);
    }

    public FindPerch(PerchSearch search, int retryDelayTicks, int groundedUrgeTicks,
                     @Nullable Function<BaseBirdMob, BlockPos> homeAnchor, double homeRange, double homeHop) {
        super(Map.of(
                BirdMod.PERCH_POS.get(), MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.search = search;
        this.retryDelayTicks = retryDelayTicks;
        this.groundedUrgeTicks = groundedUrgeTicks;
        this.homeAnchor = homeAnchor;
        this.homeRange = homeRange;
        this.homeHop = homeHop;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, BaseBirdMob bird) {
        if (!bird.canMoveOnItsOwn() || bird.trip().isTraveling() || bird.isDrivenByBehavior()) {
            return false;
        }
        if (isTamed(bird)) {
            return false;
        }

        if (!bird.canMoveByFlying()) {
            return false;
        }
        if (bird.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return false;
        }
        if (bird.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)) {
            this.nextSearchTime = level.getGameTime() + STARTLE_COOLDOWN_TICKS;
            return false;
        }
        if (level.getGameTime() < this.nextSearchTime) {
            return false;
        }
        if (bird.isOnFoot()) {
            return bird.getRandom().nextInt(this.groundedUrgeTicks) == 0;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel level, BaseBirdMob bird, long gameTime) {
        BlockPos perch = this.findPerch(bird);
        if (perch != null) {
            bird.getBrain().setMemory(BirdMod.PERCH_POS.get(), perch);
        }
        this.nextSearchTime = gameTime + this.retryDelayTicks;
    }

    @Nullable
    private BlockPos findPerch(BaseBirdMob bird) {
        BlockPos towardHome = this.hopTowardHome(bird);
        if (towardHome != null) {
            BlockPos perch = this.search.findPerch(bird, towardHome);
            if (perch != null) {
                return perch;
            }
        }
        return this.search.findPerch(bird);
    }

    @Nullable
    private BlockPos hopTowardHome(BaseBirdMob bird) {
        BlockPos anchor = this.homeAnchor == null ? null : this.homeAnchor.apply(bird);
        if (anchor == null) {
            return null;
        }
        Vec3 toHome = Vec3.atCenterOf(anchor).subtract(bird.position()).multiply(1, 0, 1);
        double distance = toHome.length();
        if (distance <= this.homeRange) {
            return null;
        }
        Vec3 hop = toHome.scale(Math.min(this.homeHop, distance) / distance);
        return BlockPos.containing(bird.position().add(hop));
    }

    private static boolean isTamed(BaseBirdMob bird) {
        return bird instanceof TameableBird tamed && tamed.isTame();
    }
}
