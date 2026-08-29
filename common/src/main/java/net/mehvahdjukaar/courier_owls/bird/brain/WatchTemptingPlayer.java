package net.mehvahdjukaar.courier_owls.bird.brain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

import java.util.Map;

public class WatchTemptingPlayer extends Behavior<LivingEntity> {
    private static final int MAX_RUN_TICKS = 600;

    public WatchTemptingPlayer() {
        super(Map.of(
                MemoryModuleType.TEMPTING_PLAYER, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
                MAX_RUN_TICKS);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, LivingEntity bird, long gameTime) {
        return bird.getBrain().hasMemoryValue(MemoryModuleType.TEMPTING_PLAYER);
    }

    @Override
    protected void tick(ServerLevel level, LivingEntity bird, long gameTime) {
        Brain<?> brain = bird.getBrain();
        brain.getMemory(MemoryModuleType.TEMPTING_PLAYER)
                .ifPresent(player -> brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true)));
    }
}
