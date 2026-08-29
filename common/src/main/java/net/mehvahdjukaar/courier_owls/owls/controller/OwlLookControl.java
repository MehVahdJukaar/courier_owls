package net.mehvahdjukaar.courier_owls.owls.controller;

import net.mehvahdjukaar.courier_owls.bird.controller.BirdLookControl;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

public class OwlLookControl extends BirdLookControl {
    private final OwlEntity owl;

    public OwlLookControl(OwlEntity owl) {
        super(owl);
        this.owl = owl;
    }

    @Override
    protected boolean headBelongsToGoals() {
        return this.isLookingAtTarget() && this.worthLookingAway() && this.gazeWithinSwivel();
    }

    private boolean worthLookingAway() {
        return !this.owl.getMode().isHeadingCommanded()
                || this.owl.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    private boolean gazeWithinSwivel() {
        Vec3 toGaze = new Vec3(this.getWantedX(), this.getWantedY(), this.getWantedZ())
                .subtract(this.mob.getEyePosition());
        if (toGaze.horizontalDistanceSqr() < 1.0E-4) {
            return false;
        }
        float off = Mth.degreesDifference(this.mob.yBodyRot, FlightMath.yawTowards(toGaze.x, toGaze.z));
        return Math.abs(off) <= this.owl.getMaxHeadYRot();
    }
}
