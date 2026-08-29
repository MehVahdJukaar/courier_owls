package net.mehvahdjukaar.courier_owls.owls.entities;

import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class OwlPreySensor extends NearestVisibleLivingEntitySensor {
    private static final double PREY_RANGE_SQR = 32.0 * 32.0;

    @Override
    protected boolean isMatchingEntity(ServerLevel level, LivingEntity attacker, LivingEntity target) {
        return target.distanceToSqr(attacker) <= PREY_RANGE_SQR
                && !attacker.getBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN)
                && isPrey(level, attacker, target);
    }

    public static boolean isPrey(ServerLevel level, LivingEntity attacker, LivingEntity target) {
        return target.is(preyTagFor(attacker)) && Sensor.isEntityAttackable(level, attacker, target);
    }

    private static TagKey<EntityType<?>> preyTagFor(LivingEntity attacker) {
        return attacker instanceof OwlEntity owl && owl.isTame() ? OwlMod.OWL_TAME_PREY : OwlMod.OWL_PREY;
    }

    @Override
    protected MemoryModuleType<LivingEntity> getMemoryToSet() {
        return MemoryModuleType.NEAREST_ATTACKABLE;
    }
}
