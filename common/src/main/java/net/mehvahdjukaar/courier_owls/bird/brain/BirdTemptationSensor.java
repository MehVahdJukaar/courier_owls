package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.bird.entity.TameableBird;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.Set;

public class BirdTemptationSensor extends Sensor<TameableBird> {
    private static final double TEMPTATION_RANGE = 10.0;
    private static final TargetingConditions TEMPT_TARGETING =
            TargetingConditions.forNonCombat().range(TEMPTATION_RANGE).ignoreLineOfSight();

    @Override
    protected void doTick(ServerLevel level, TameableBird bird) {
        Brain<?> brain = bird.getBrain();

        if (bird.isOnErrand()) {
            brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
            return;
        }
        Player nearest = level.players().stream()
                .filter(EntitySelector.NO_SPECTATORS)
                .filter(player -> TEMPT_TARGETING.test(level, bird, player))
                .filter(player -> holdingSomethingTheBirdWants(bird, player))
                .filter(player -> !bird.hasPassenger(player))
                .min(Comparator.comparingDouble(bird::distanceToSqr))
                .orElse(null);
        if (nearest == null) {
            brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        } else {
            brain.setMemory(MemoryModuleType.TEMPTING_PLAYER, nearest);
        }
    }

    private static boolean holdingSomethingTheBirdWants(TameableBird bird, Player player) {
        return bird.isTemptedBy(player.getMainHandItem()) || bird.isTemptedBy(player.getOffhandItem());
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(MemoryModuleType.TEMPTING_PLAYER);
    }
}
