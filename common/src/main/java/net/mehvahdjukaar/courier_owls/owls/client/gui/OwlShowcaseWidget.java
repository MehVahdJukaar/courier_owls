package net.mehvahdjukaar.courier_owls.owls.client.gui;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.bird.client.BirdAnimation;
import net.mehvahdjukaar.courier_owls.bird.client.PoseInputs;
import net.mehvahdjukaar.courier_owls.bird.controller.GaitSettings;
import net.mehvahdjukaar.courier_owls.bird.envelope.FlightEnvelope;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.client.renderers.OwlRenderState;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import net.mehvahdjukaar.courier_owls.owls.particles.OwlFeather;
import net.mehvahdjukaar.moonlight.api.client.gui.particle.ScreenParticle;
import net.mehvahdjukaar.moonlight.api.client.gui.particle.ScreenParticleEngine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class OwlShowcaseWidget extends AbstractWidget {
    private static final List<OwlType> SKINS = Stream.of(OwlType.values())
            .filter(type -> type != OwlType.MOON && type != OwlType.DUO).toList();

    private static final float FEATHER_SPREAD = 0.4F;
    private static final float FEATHER_REACH = 0.85F;
    private static final int FEATHERS_PER_CLICK = 22;

    private static final float BLOCKS_TALL = 1.55F;
    private static final float GROUND_MARGIN = 3.0F;

    private static final float BASE_YAW = 28.0F;

    private static final float VIEW_TILT = 12.0F;

    private static final float CRUISE_ALTITUDE = 0.25F;
    private static final float CLIMB_PER_TICK = 0.05F;
    private static final float DESCENT_PER_TICK = 0.045F;

    private static final float LANDING_POSE_ALTITUDE = 0.3F;

    private static final float CLIMB_SLOPE = -30.0F;
    private static final float CRUISE_SLOPE = 0.0F;
    private static final float GLIDE_SLOPE = 20.0F;

    private static final float FLARE_START = 0.5F;
    private static final float FLARE_BRAKE = 0.7F;

    private static final float CIRCLE_RATE = 0.02F;
    private static final float MAX_HEADING = 40.0F;
    private static final float DRIFT_BLOCKS = 0.22F;
    private static final float SWEEP_PER_TICK = 0.02F;

    private static final float LOOK_DEGREES_PER_PIXEL = 0.9F;
    private static final float MAX_LOOK_YAW = 65.0F;

    private static final Map<OwlFeather, Identifier> FEATHERS = Util.make(new EnumMap<>(OwlFeather.class),
            m -> {
                for (OwlFeather feather : OwlFeather.values()) {
                    m.put(feather, BirdMod.res("particle/feather_" + feather.getSerializedName()));
                }
            });

    private enum Phase {PERCHED, SLEEPING, TAKEOFF, CRUISE, APPROACH, LAND}

    private final OwlRenderState owl = new OwlRenderState();
    private final BirdAnimation animation = new BirdAnimation();
    private final FlightEnvelope envelope;
    private final ScreenParticleEngine particles = new ScreenParticleEngine();
    private final RandomSource random = RandomSource.create();

    private OwlType skin = OwlType.HORNED;
    private Phase phase = Phase.PERCHED;
    private int phaseLength = 1;
    private int phaseTicks = 1;
    private boolean onFoot = true;
    private boolean interested;
    private float landing;

    private float bodyPitch;

    private float altitude;
    private float altitudeO;
    private float drift;
    private float driftO;
    private float heading;
    private float headingO;

    private float sweep;
    private float circle;

    private float headYaw;
    private int ticksAlive;
    private float sinceLastTick;
    private long lastMs = -1;

    public OwlShowcaseWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("entity.courier_owls.owl"));
        this.owl.entityType = OwlMod.OWL.get();
        AttributeSupplier attributes = OwlEntity.makeAttributes().build();
        this.envelope = FlightEnvelope.of(OwlEntity.SETTINGS.envelope(),
                attributes.getValue(Attributes.FLYING_SPEED), attributes.getValue(Attributes.GRAVITY));
        this.bodyPitch = -this.gait().perchPitch;
        this.rollPhaseLength();

        this.tickOwl();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        this.advance(mouseX);

        float pt = this.sinceLastTick;
        float scale = this.height / BLOCKS_TALL;
        float bodyYaw = BASE_YAW + Mth.lerp(pt, this.headingO, this.heading);

        this.owl.skin = this.skin;
        this.owl.sleeping = this.phase == Phase.SLEEPING;
        this.owl.onFoot = this.onFoot;
        this.owl.ageInTicks = this.ticksAlive + pt;
        this.owl.yRot = this.headYaw;

        this.owl.bodyRot = 180.0F - bodyYaw;
        this.owl.bodyPitch = this.animation.bodyPitch(pt) + this.gait().perchPitch;
        this.owl.bank = this.animation.bank(pt);
        this.owl.flapPhase = this.animation.flapPhase(pt);
        this.owl.wingSpread = this.animation.wingSpread(pt);
        this.owl.glide = this.animation.glide(pt);
        this.owl.strokeScale = this.animation.strokeScale(pt);
        this.owl.strokeTilt = this.animation.strokeTilt(pt);
        this.owl.landingProgress = this.animation.landingProgress(pt);
        this.owl.headTilt = this.animation.headTilt(pt);
        this.owl.sit = this.animation.sit(pt);
        this.owl.carry = 0.0F;
        this.owl.strokeBob = this.animation.strokeBob(pt);

        Quaternionf rotation = new Quaternionf()
                .rotateZ(Mth.PI)
                .rotateX(-VIEW_TILT * Mth.DEG_TO_RAD);

        Vector3f translation = rotation.transform(new Vector3f(-Mth.lerp(pt, this.driftO, this.drift),
                Mth.lerp(pt, this.altitudeO, this.altitude), 0.0F));
        translation.add(0.0F, (this.height / 2f - GROUND_MARGIN) / scale, 0.0F);

        graphics.entity(this.owl, scale, translation, rotation, null,
                this.getX(), this.getY(), this.getRight(), this.getBottom());

        this.particles.renderAndTick(graphics);
    }

    private void advance(int mouseX) {
        long now = Util.getMillis();
        float dt = this.lastMs < 0 ? 0 : Math.min((now - this.lastMs) / 1000f, 0.1f);
        this.lastMs = now;
        this.sinceLastTick += dt * 20f;
        while (this.sinceLastTick >= 1f) {
            this.sinceLastTick -= 1f;
            this.tickOwl();
        }
        this.trackCursor(mouseX, dt);
    }

    private void trackCursor(int mouseX, float dt) {
        float targetYaw = 0;

        if (this.phase == Phase.PERCHED) {
            float headX = this.getX() + this.width / 2f;
            float aim = Mth.clamp((mouseX - headX) * LOOK_DEGREES_PER_PIXEL, -MAX_LOOK_YAW, MAX_LOOK_YAW);

            targetYaw = BASE_YAW + this.heading - aim;
        }
        float ease = Math.min(1f, dt * 6f);
        this.headYaw = Mth.lerp(ease, this.headYaw, targetYaw);
    }

    private void tickOwl() {
        this.ticksAlive++;
        this.altitudeO = this.altitude;
        this.driftO = this.drift;
        this.headingO = this.heading;

        boolean touchedDown = this.phase == Phase.APPROACH && this.altitude <= 0.0F;
        if (touchedDown || --this.phaseTicks <= 0) {
            this.enterNextPhase();
        }
        float progress = 1f - (float) this.phaseTicks / this.phaseLength;

        double thrust;
        float slope;
        switch (this.phase) {
            case TAKEOFF -> {
                thrust = Mth.lerp(progress, this.envelope.peakTakeOffAccel(), this.envelope.levelThrustAccel());
                slope = Mth.lerp(progress, CLIMB_SLOPE, CRUISE_SLOPE);
                this.altitude = easeToward(this.altitude, CRUISE_ALTITUDE, CLIMB_PER_TICK);
                this.landing = 0;
            }
            case CRUISE -> {
                thrust = this.envelope.levelThrustAccel();
                slope = CRUISE_SLOPE;
                this.altitude = easeToward(this.altitude, CRUISE_ALTITUDE, CLIMB_PER_TICK);
                this.landing = 0;
            }
            case APPROACH -> {
                this.altitude = easeToward(this.altitude, 0, DESCENT_PER_TICK);
                this.landing = Mth.clamp(1f - this.altitude / LANDING_POSE_ALTITUDE, 0, 1);
                boolean flaring = this.landing > FLARE_START;
                thrust = flaring ? -this.envelope.wingBrakeAccel() * FLARE_BRAKE : 0.0;
                slope = GLIDE_SLOPE;
            }
            default -> {
                thrust = 0;
                slope = 0;
                this.altitude = 0;
                this.landing = 0;
            }
        }

        GaitSettings gait = this.gait();
        float wantedPitch = this.onFoot ? -gait.perchPitch : Mth.lerp(this.landing, slope, -gait.flutterPitch);
        this.bodyPitch = Mth.approachDegrees(this.bodyPitch, wantedPitch, gait.maxPitchPerTick);

        this.sweep = Mth.approach(this.sweep, this.phase == Phase.CRUISE ? 1f : 0f, SWEEP_PER_TICK);
        this.circle += CIRCLE_RATE;
        float swing = Mth.sin(this.circle) * this.sweep;
        this.heading = MAX_HEADING * swing;
        this.drift = DRIFT_BLOCKS * swing;
        double yawRate = MAX_HEADING * Mth.DEG_TO_RAD * Mth.cos(this.circle) * CIRCLE_RATE * this.sweep;

        this.animation.tick(this.envelope, new PoseInputs(thrust, this.bodyPitch, this.onFoot, this.landing,
                this.phase == Phase.SLEEPING, false, this.interested, yawRate,
                this.envelope.cruiseSpeed()), this.random);
        this.animation.tickAfterMove(this.bodyPitch);
    }

    private GaitSettings gait() {
        return OwlEntity.SETTINGS.gait();
    }

    private static float easeToward(float value, float target, float maxStep) {
        float step = Math.min(maxStep, Math.abs(target - value) * 0.12f + 0.004f);
        return Mth.approach(value, target, step);
    }

    private void enterNextPhase() {
        this.phase = switch (this.phase) {
            case PERCHED -> this.random.nextBoolean() ? Phase.SLEEPING : Phase.TAKEOFF;
            case TAKEOFF -> Phase.CRUISE;
            case CRUISE -> Phase.APPROACH;
            case APPROACH -> Phase.LAND;
            default -> Phase.PERCHED;
        };
        this.onFoot = this.phase != Phase.TAKEOFF && this.phase != Phase.CRUISE && this.phase != Phase.APPROACH;
        this.interested = this.phase == Phase.PERCHED && this.random.nextBoolean();
        this.rollPhaseLength();
    }

    private void rollPhaseLength() {
        this.phaseLength = switch (this.phase) {
            case PERCHED -> Mth.randomBetweenInclusive(this.random, 60, 180);
            case SLEEPING -> Mth.randomBetweenInclusive(this.random, 200, 500);
            case TAKEOFF -> 22;
            case CRUISE -> Mth.randomBetweenInclusive(this.random, 120, 260);
            case APPROACH -> 200;
            case LAND -> 20;
        };
        this.phaseTicks = this.phaseLength;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.skin = SKINS.get((SKINS.indexOf(this.skin) + 1) % SKINS.size());

        float scale = this.height / BLOCKS_TALL;
        float feetX = this.getX() + this.width / 2f + this.drift * scale;
        float feetY = this.getY() + this.height - GROUND_MARGIN - this.altitude * scale;
        for (int i = 0; i < FEATHERS_PER_CLICK; i++) {
            float x = feetX + Mth.randomBetween(this.random, -FEATHER_SPREAD, FEATHER_SPREAD) * scale;
            float y = feetY - this.random.nextFloat() * FEATHER_REACH * scale;
            this.particles.add(ScreenParticle.sprite(FEATHERS.get(this.skin.feather), x, y)
                    .velocity(Mth.randomBetween(this.random, -40, 40), Mth.randomBetween(this.random, -50, -5))
                    .gravity(40)
                    .drag(2.2f)
                    .size(Mth.randomBetween(this.random, 9f, 14f))
                    .rotation(this.random.nextFloat() * 360)
                    .spin(Mth.randomBetween(this.random, -180, 180))
                    .fadeOut(0.6f)
                    .lifetime(Mth.randomBetween(this.random, 0.9f, 1.6f)));
        }

        if (this.onFoot) {
            this.phase = Phase.TAKEOFF;
            this.onFoot = false;
            this.interested = false;
            this.rollPhaseLength();
        }
    }

    @Override
    public void playDownSound(SoundManager handler) {
        handler.play(SimpleSoundInstance.forUI(SoundEvents.PARROT_FLY, 1f));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }
}
