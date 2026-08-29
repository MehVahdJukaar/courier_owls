package net.mehvahdjukaar.courier_owls.owls.client.particles;

import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.mehvahdjukaar.courier_owls.owls.particles.OwlFeather;
import net.mehvahdjukaar.courier_owls.owls.particles.OwlFeatherOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class OwlFeatherParticle extends SingleQuadParticle {
    private static final int GROUND_LIFETIME = 20;
    private static final int FADE_TICKS = 10;

    private static final double FLUTTER_DAMPING = 20;

    private final float rotSpeed;
    private final int spinPhaseOffset;

    private boolean fluttering = false;
    private int flutterStartAge;
    private final float rotOffset;
    private int groundTime = 0;

    private OwlFeatherParticle(ClientLevel level, OwlFeather feather, TextureAtlasSprite sprite,
                               double x, double y, double z,
                               double speedX, double speedY, double speedZ) {
        super(level, x, y, z, sprite);

        this.rotOffset = feather.tilt;
        this.quadSize *= (float) (1.3125F + this.random.nextFloat() * 0.15);
        this.lifetime = 360 + this.random.nextInt(60);

        this.rotSpeed = Mth.clamp(2f * (0.045f + this.random.nextFloat() * 0.08f) + ((float) speedY - 0.03f), 0.02f, 0.5f);
        this.spinPhaseOffset = (int) ((this.random.nextFloat() * ((float) Math.PI * 2F)) / this.rotSpeed);
        this.xd = speedX + (this.random.nextFloat() * 2.0D - 1.0D) * 0.008F;
        this.yd = speedY;
        this.zd = speedZ + (this.random.nextFloat() * 2.0D - 1.0D) * 0.008F;
        this.gravity = 0.007F;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (++this.age >= this.lifetime || this.groundTime > GROUND_LIFETIME) {
            this.remove();
        } else {
            this.yd -= 0.04D * (double) this.gravity;
            this.move(this.xd, this.yd, this.zd);

            this.xd *= this.friction;
            this.yd *= this.friction;
            this.zd *= this.friction;

            if (!this.onGround) {
                if (!this.fluttering) {
                    float rot = (float) (((this.age + this.spinPhaseOffset) * this.rotSpeed) % (2 * Math.PI));

                    if (this.yd <= 0 && rot > 0 && rot < 0.01 + this.rotSpeed * 2) {
                        this.fluttering = true;
                        this.flutterStartAge = this.age;
                    }

                    this.oRoll = this.roll;
                    this.roll = rot;
                } else {
                    int t = this.age - this.flutterStartAge;

                    double swingFreq = 1 - this.rotSpeed;
                    float minAmplitude = (float) (swingFreq / 2f);
                    float amplitude = (float) ((swingFreq - minAmplitude) * Math.exp(-t / FLUTTER_DAMPING)) + minAmplitude;
                    float angularSpeed = (float) (this.rotSpeed / swingFreq);

                    this.oRoll = this.roll;
                    this.roll = Mth.sin(t * angularSpeed) * amplitude;
                }
            } else {
                this.groundTime++;
                this.oRoll = this.roll;
                this.yd = 0.0D;
            }

            int ticksLeft = Math.min(this.lifetime - this.age, GROUND_LIFETIME - this.groundTime);
            this.alpha = ticksLeft >= FADE_TICKS ? 1 : Math.max(0, ticksLeft / (float) FADE_TICKS);
        }
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTicks) {
        Quaternionf quaternion;
        if (this.roll == 0.0F && this.rotOffset == 0.0F) {
            quaternion = camera.rotation();
        } else {
            quaternion = new Quaternionf(camera.rotation());
            float p = Mth.RAD_TO_DEG;

            float angle = Mth.rotLerp(partialTicks, (this.rotOffset + this.oRoll) * p,
                    (this.rotOffset + this.roll) * p);
            quaternion.mul(Axis.ZP.rotation(angle / p));
        }

        this.extractRotatedQuad(state, camera, quaternion, partialTicks);
    }

    @Override
    protected void extractRotatedQuad(QuadParticleRenderState state, Camera camera, Quaternionf quaternionf, float partialTicks) {
        Vec3 cam = camera.position();

        float lift = this.getQuadSize(partialTicks);

        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y()) + lift;
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        this.extractRotatedQuad(state, quaternionf, x, y, z, partialTicks);
    }

    public static class Factory implements ParticleProvider<OwlFeatherOptions> {
        private static final int LAST_SPRITE = OwlFeather.values().length - 1;

        private final SpriteSet spriteSet;

        public Factory(SpriteSet sprite) {
            this.spriteSet = sprite;
        }

        @Override
        public Particle createParticle(OwlFeatherOptions options, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
            OwlFeather feather = options.feather();

            TextureAtlasSprite sprite = spriteSet.get(feather.ordinal(), LAST_SPRITE);
            return new OwlFeatherParticle(level, feather, sprite, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
