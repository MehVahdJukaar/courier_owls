package net.mehvahdjukaar.courier_owls.bird.brain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

public class BirdSightSensor extends NearestLivingEntitySensor<LivingEntity> {
    private static final int RADIUS_XZ = 48;
    private static final int REACH_UP = 16;
    private static final int REACH_DOWN = 32;

    @Override
    protected void doTick(ServerLevel level, LivingEntity bird) {
        AABB box = bird.getBoundingBox().inflate(RADIUS_XZ, 0.0, RADIUS_XZ).expandTowards(0.0, REACH_UP, 0.0)
                .expandTowards(0.0, -REACH_DOWN, 0.0);
        List<LivingEntity> seen = level.getEntitiesOfClass(LivingEntity.class, box,
                other -> other != bird && other.isAlive());
        seen.sort(Comparator.comparingDouble(bird::distanceToSqr));
        Brain<?> brain = bird.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, seen);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(level, bird, seen));
    }
}
