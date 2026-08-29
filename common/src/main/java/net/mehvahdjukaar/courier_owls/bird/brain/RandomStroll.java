package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class RandomStroll extends Behavior<BaseBirdMob> {
    private static final int HORIZONTAL_RANGE = 12;
    private static final int VERTICAL_RANGE = 7;
    private static final int CLOSE_ENOUGH = 1;

    private final float speedModifier;
    private final int urgeTicks;
    private final int wetUrgeTicks;
    private final boolean strollsWhenDry;

    public static RandomStroll wetOnly(float speedModifier, int wetUrgeTicks) {
        return new RandomStroll(speedModifier, 0, wetUrgeTicks, false);
    }

    public RandomStroll(float speedModifier, int urgeTicks, int wetUrgeTicks) {
        this(speedModifier, urgeTicks, wetUrgeTicks, true);
    }

    private RandomStroll(float speedModifier, int urgeTicks, int wetUrgeTicks, boolean strollsWhenDry) {
        super(Map.of(
                BirdMod.PERCH_POS.get(), MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.urgeTicks = urgeTicks;
        this.wetUrgeTicks = wetUrgeTicks;
        this.strollsWhenDry = strollsWhenDry;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, BaseBirdMob bird) {
        if (!bird.canMoveOnItsOwn() || bird.trip().isTraveling() || bird.isDrivenByBehavior()) {
            return false;
        }

        if (!bird.isOnFoot()) {
            return false;
        }
        if (!bird.isInWater()) {
            return this.strollsWhenDry && bird.getRandom().nextInt(this.urgeTicks) == 0;
        }
        return bird.getRandom().nextInt(this.wetUrgeTicks) == 0;
    }

    @Override
    protected void start(ServerLevel level, BaseBirdMob bird, long gameTime) {
        Vec3 spot = LandRandomPos.getPos(bird, HORIZONTAL_RANGE, VERTICAL_RANGE);
        if (spot != null) {
            bird.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(BlockPos.containing(spot), this.speedModifier, CLOSE_ENOUGH));
        }
    }
}
