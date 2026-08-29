package net.mehvahdjukaar.courier_owls.bird.brain;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.CorridorRaycaster;
import net.mehvahdjukaar.courier_owls.bird.envelope.FlightEnvelope;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DirectPursuit {
    private static final double ARC_FRACTION = 0.5;

    private static final double LINE_MARGIN = 0.25;

    public static boolean lineIsClear(BaseBirdMob bird, Vec3 target) {
        return CorridorRaycaster.isOpen(bird, bird.position(), target, LINE_MARGIN);
    }

    public static boolean steer(BaseBirdMob bird, Vec3 target) {
        if (!bird.isFlightTakenOver() && !bird.takeOverFlight()) {
            return false;
        }
        FlightEnvelope envelope = bird.activeEnvelope();
        double distance = target.distanceTo(bird.position());
        double cap = Mth.clamp(envelope.maxSpeedForArc(distance * ARC_FRACTION),
                envelope.minSpeed(), envelope.cruiseSpeed());
        bird.steerTowards(target, envelope, cap);
        return true;
    }
}
