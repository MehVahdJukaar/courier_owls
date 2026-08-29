package net.mehvahdjukaar.courier_owls.bird.envelope;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

public record FlightEnvelope(
        double turnPush,
        double hoverTurnRate,
        double maxHorizontalSpeed,
        double cruiseSpeed,
        double minSpeed,
        double maxTouchdownSpeed,
        double drag,
        double levelThrustAccel,
        double verticalClimbAccel,
        double verticalTuckAccel,
        double verticalSpreadAccel,
        double peakTakeOffAccel,
        double wingBrakeAccel,
        double gravity,
        double corridorMargin,
        double clearanceSpentFraction
) {
    private static final int MAX_SIMULATED_TICKS = 512;

    public static FlightEnvelope forMob(Mob mob, EnvelopeSettings settings) {
        return of(settings, mob.getAttributeValue(Attributes.FLYING_SPEED),
                mob.getAttributeValue(Attributes.GRAVITY));
    }

    public static FlightEnvelope of(EnvelopeSettings settings, double flyingSpeedAttr, double mobGravity) {
        double airDrag = settings.airDrag;
        double accel = settings.maxThrustAccel * Mth.clamp(flyingSpeedAttr, 0.0, 1.0);
        double maxSpeed = terminalSpeed(accel, airDrag);

        double verticalClimbAccel = accel * Math.max(0.0, settings.verticalClimbSpeedFraction) + mobGravity;

        double peakTakeOffAccel = accel * Math.max(1.0, settings.takeOffThrustMultiplier);
        return new FlightEnvelope(
                turnPushFor(maxSpeed, settings.turnRadiusAtTopSpeed),
                settings.hoverTurnPerTick * Mth.DEG_TO_RAD,
                maxSpeed,
                maxSpeed * settings.cruiseFraction,
                maxSpeed * settings.minSpeedFraction,
                settings.maxTouchdownSpeed,
                airDrag,
                accel,
                verticalClimbAccel,
                mobGravity * Math.max(0.0, settings.tuckedDiveSpeedMultiplier - 1.0),
                mobGravity * Mth.clamp(1.0 - settings.spreadDiveSpeedMultiplier, 0.0, 1.0),
                peakTakeOffAccel,
                accel * Math.max(0.0, settings.brakeThrustMultiplier),
                mobGravity,
                settings.corridorMargin,
                settings.clearanceSpentFraction);
    }

    private static double turnPushFor(double maxSpeed, double radiusAtTopSpeed) {
        if (radiusAtTopSpeed <= 1.0E-6) {
            return Double.POSITIVE_INFINITY;
        }
        return maxSpeed * maxSpeed / radiusAtTopSpeed;
    }

    private static double terminalSpeed(double accel, double drag) {
        return drag >= 1.0 ? Double.MAX_VALUE : accel * drag / (1.0 - drag);
    }

    public double turnPerTickAt(double speed) {
        if (speed <= 1.0E-9) {
            return this.hoverTurnRate;
        }
        return Math.min(this.hoverTurnRate, this.turnPush / speed);
    }

    public double maxSpeedForArc(double radius) {
        if (radius <= 0.0) {
            return 0.0;
        }
        return Math.min(Math.sqrt(this.turnPush * radius), this.hoverTurnRate * radius);
    }

    public double arcRadiusAt(double speed) {
        double turn = this.turnPerTickAt(speed);
        return turn <= 1.0E-9 ? Double.MAX_VALUE : speed / turn;
    }

    public double gravityAlong(double pitch) {
        return -this.gravity * Math.sin(pitch);
    }

    public double bankAngleFor(double speed, double yawRate) {
        if (this.gravity <= 1.0E-9) return 0.0;
        return Math.atan(speed * yawRate / this.gravity);
    }

    public double sustainedThrustAt(double pitch) {
        double sin = Math.sin(pitch);
        return sin <= 0.0 ? this.levelThrustAccel : Mth.lerp(sin, this.levelThrustAccel, this.verticalClimbAccel);
    }

    public double tuckAlong(double pitch) {
        return Math.max(0.0, -Math.sin(pitch)) * this.verticalTuckAccel;
    }

    public double spreadAlong(double pitch) {
        return Math.max(0.0, -Math.sin(pitch)) * this.verticalSpreadAccel;
    }

    public double fallPushToReach(double targetSpeed, double currentSpeed, double pitch) {
        if (this.drag <= 0.0) {
            return 0.0;
        }
        double needed = this.accelToReach(targetSpeed, currentSpeed, pitch);
        return Mth.clamp(needed, -this.spreadAlong(pitch), this.tuckAlong(pitch));
    }

    public double sustainedBudgetAt(double pitch) {
        double sin = Math.sin(pitch);
        if (sin >= 0.0) {
            return this.sustainedThrustAt(pitch) + this.gravityAlong(pitch);
        }
        return Math.max(this.levelThrustAccel, this.gravityAlong(pitch) + this.tuckAlong(pitch));
    }

    public double coastBrakeAt(double pitch) {
        return this.spreadAlong(pitch);
    }

    public double maxSpeedAt(double pitch) {
        double budget = this.sustainedBudgetAt(pitch);
        return budget <= 0.0 ? this.minSpeed : Math.max(this.minSpeed, terminalSpeed(budget, this.drag));
    }

    public double thrustCapAt(double speed, double pitch) {
        double sustained = this.sustainedThrustAt(pitch);
        double towardsTop = this.maxHorizontalSpeed <= 0.0 ? 1.0 : speed / this.maxHorizontalSpeed;
        return Mth.clampedLerp(Math.max(this.peakTakeOffAccel, sustained), sustained, towardsTop);
    }

    public double thrustToReach(double targetSpeed, double currentSpeed, double pitch) {
        if (this.drag <= 0.0) {
            return this.levelThrustAccel;
        }
        double needed = this.accelToReach(targetSpeed, currentSpeed, pitch);
        return Mth.clamp(needed, -this.wingBrakeAccel, this.thrustCapAt(currentSpeed, pitch));
    }

    private double accelToReach(double targetSpeed, double currentSpeed, double pitch) {
        return targetSpeed / this.drag - currentSpeed - this.gravityAlong(pitch);
    }

    public double coastingDistance(double speed) {
        return this.drag >= 1.0 ? Double.MAX_VALUE : speed / (1.0 - this.drag);
    }

    public double maxEntrySpeed(double exitSpeed, double distance, double pitch) {
        return this.maxEntrySpeed(exitSpeed, distance, pitch, this.wingBrakeAccel);
    }

    public double maxEntrySpeed(double exitSpeed, double distance, double pitch, double brake) {
        double net = this.gravityAlong(pitch) - brake;
        boolean settlesAboveExit = net > 0.0 && exitSpeed <= terminalSpeed(net, this.drag);
        if (this.drag <= 0.0 || net == 0.0 || settlesAboveExit) {
            return exitSpeed + distance * (1.0 - this.drag);
        }
        double speed = exitSpeed;
        double covered = 0.0;
        for (int tick = 0; tick < MAX_SIMULATED_TICKS && covered < distance; tick++) {
            double moving = speed / this.drag;
            covered += moving;
            speed = moving - net;
        }
        return speed;
    }

    public double speedAfterAccelerating(double entrySpeed, double distance, double pitch) {
        double cap = this.maxSpeedAt(pitch);
        double gravity = this.gravityAlong(pitch);
        double wings = this.sustainedThrustAt(pitch);
        if (wings + gravity <= 0.0) {
            return Math.min(entrySpeed, cap);
        }

        double wingsChase = Math.min(cap, this.maxHorizontalSpeed);
        double speed = entrySpeed;
        double covered = 0.0;
        for (int tick = 0; tick < MAX_SIMULATED_TICKS && covered < distance; tick++) {
            double tuck = Mth.clamp(this.accelToReach(cap, speed, pitch), 0.0, this.tuckAlong(pitch));
            double thrust = Mth.clamp(this.accelToReach(wingsChase, speed, pitch) - tuck, 0.0, wings);
            double moving = speed + gravity + tuck + thrust;
            covered += moving;
            speed = this.drag * moving;
        }
        return speed;
    }
}
