package net.mehvahdjukaar.courier_owls.bird.client;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.minecraft.util.Mth;

public record PoseInputs(double wingThrust, float bodyPitch, boolean onFoot, float landingProgress,
                         boolean sitting, boolean carrying, boolean interested,
                         double yawRate, double horizontalSpeed) {
    public static PoseInputs of(BaseBirdMob mob) {
        return new PoseInputs(mob.getWingThrust(), mob.getBodyPitch(), mob.isOnFoot(),
                mob.getLandingProgress(), !mob.canMoveOnItsOwn(), !mob.getMainHandItem().isEmpty(),
                mob.isInterested(), Mth.degreesDifference(mob.yRotO, mob.getYRot()) * Mth.DEG_TO_RAD,
                mob.getDeltaMovement().horizontalDistance());
    }
}
