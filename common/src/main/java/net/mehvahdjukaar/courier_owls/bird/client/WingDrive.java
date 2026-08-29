package net.mehvahdjukaar.courier_owls.bird.client;

import net.minecraft.util.Mth;

public record WingDrive(double forwardThrust, double upThrust, boolean grounded) {
    public static WingDrive live(double thrust, float bodyPitchDegrees, boolean grounded) {
        float pitch = bodyPitchDegrees * Mth.DEG_TO_RAD;
        return new WingDrive(thrust * Mth.cos(pitch), -thrust * Mth.sin(pitch), grounded);
    }

    public double thrust() {
        return Math.sqrt(this.forwardThrust * this.forwardThrust + this.upThrust * this.upThrust);
    }
}
