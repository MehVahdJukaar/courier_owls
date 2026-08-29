package net.mehvahdjukaar.courier_owls.bird.client;

import net.minecraft.util.Mth;

public class TwoPieceWingBeat {
    public float inner;
    public float outer;

    public float flex;

    public void update(float flapPhase, float wingSpread, float glide, float strokeScale, float ageInTicks) {
        float beating = wingSpread * (1.0F - glide);

        float stroke = warpStroke(flapPhase * Mth.TWO_PI);
        float strokeLagged = warpStroke(flapPhase * Mth.TWO_PI
                - BirdAnimationConfig.spanwiseLagDegr * Mth.DEG_TO_RAD);

        float fold = BirdAnimationConfig.foldAmplitudeDegr * 0.5F
                * (1.0F - Mth.sin(stroke + BirdAnimationConfig.foldPhaseDegr * Mth.DEG_TO_RAD));

        float amplitude = BirdAnimationConfig.flapAmplitudeDegr * strokeScale;
        this.inner = amplitude * Mth.cos(stroke) * beating;
        this.outer = (amplitude * Mth.cos(strokeLagged) - fold) * beating;

        this.flex = Mth.sin(ageInTicks * BirdAnimationConfig.glideWobbleRate)
                * BirdAnimationConfig.glideWobble * glide * wingSpread;
    }

    public static float dihedralDegr(float glide) {
        return Mth.lerp(glide, BirdAnimationConfig.strokeDihedralDegr, BirdAnimationConfig.glideDihedralDegr);
    }

    public static float warpStroke(float strokeAngle) {
        float duty = BirdAnimationConfig.downstrokeDuty;
        if (Mth.abs(duty - 0.5F) < 1.0E-4F) return strokeAngle;
        float k = 1.0F / (float) Math.tan(Mth.HALF_PI * duty);
        float u = 0.5F * (Mth.positiveModulo(strokeAngle, Mth.TWO_PI) - Mth.PI * duty);
        return 2.0F * (float) Math.atan2(k * Mth.sin(u), Mth.cos(u)) + Mth.HALF_PI;
    }
}
