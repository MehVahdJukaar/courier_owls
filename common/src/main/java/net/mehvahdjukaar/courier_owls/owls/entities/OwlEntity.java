package net.mehvahdjukaar.courier_owls.owls.entities;

import net.mehvahdjukaar.courier_owls.configs.CommonConfigs;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.particles.OwlFeather;
import net.mehvahdjukaar.courier_owls.owls.particles.OwlFeatherOptions;
import net.mehvahdjukaar.courier_owls.owls.delivery.Delivery;
import net.mehvahdjukaar.courier_owls.owls.delivery.Deliveries;
import net.mehvahdjukaar.courier_owls.owls.nest.NestClaim;
import net.mehvahdjukaar.courier_owls.bird.controller.GaitSettings;
import net.mehvahdjukaar.courier_owls.bird.entity.BirdSettings;
import net.mehvahdjukaar.courier_owls.bird.entity.TameableBird;
import net.mehvahdjukaar.courier_owls.owls.controller.OwlLookControl;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;


public class OwlEntity extends TameableBird {
    private static final EntityDataAccessor<OwlType> DATA_VARIANT_ID =
            SynchedEntityData.defineId(OwlEntity.class, OwlMod.OWL_TYPE_DATA.get());
    private static final EntityDataAccessor<Boolean> DATA_SLEEPING =
            SynchedEntityData.defineId(OwlEntity.class, EntityDataSerializers.BOOLEAN);

    public static final float CHICK_SCALE = 0.6F;

    private static final int PERCHED_HEAD_SWIVEL = 155;
    private static final int FLYING_HEAD_PITCH = 35;

    public static final BirdSettings SETTINGS = Util.make(() -> {
        GaitSettings gait = new GaitSettings();
        gait.perchPitch = 90.0F;
        gait.headHoldTicks = 20 * 6;
        BirdSettings defaults = BirdSettings.DEFAULTS;
        return new BirdSettings(defaults.envelope(), gait, defaults.search(), defaults.pursuit(),
                defaults.direct(), defaults.trim());
    });

    private long lastFedTime = -1000;

    private long stayOutOfNestUntil;
    @Nullable
    private Delivery delivery;

    public OwlEntity(EntityType<? extends OwlEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public BirdSettings settings() {
        return SETTINGS;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(OwlMod.OWL_FOOD);
    }

    @Override
    public boolean isTemptedBy(ItemStack stack) {
        return this.getTalonItem().isEmpty() && !this.isAttending()
                && !this.isVisitingNest()
                && super.isTemptedBy(stack);
    }

    @Override
    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        if (this.isTame() && this.isFood(stack)) {
            this.lastFedTime = this.level().getGameTime();
        }
        super.usePlayerItem(player, hand, stack);
    }

    public long lastFedTime() {
        return this.lastFedTime;
    }

    public void keepOutOfNestFor(int ticks) {
        this.stayOutOfNestUntil = this.level().getGameTime() + ticks;
    }

    public boolean hasToStaysOutOfNest() {
        return this.level().getGameTime() < this.stayOutOfNestUntil;
    }

    @Override
    protected void applyTamingSideEffects() {
        NestClaim.abandon(this);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            NestClaim.abandon(this);
        }
        super.die(source);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult handoff = Deliveries.onRightClick(this, player, hand);
        return handoff != null ? handoff : super.mobInteract(player, hand);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions dimensions = super.getDefaultDimensions(pose);
        return this.isBaby() ? dimensions.scale(CHICK_SCALE) : dimensions;
    }

    @Override
    protected boolean isTempted() {
        return this.getBrain().hasMemoryValue(MemoryModuleType.TEMPTING_PLAYER);
    }

    @Override
    public boolean isSleeping() {
        return this.entityData.get(DATA_SLEEPING);
    }

    public void setSleeping(boolean sleeping) {
        this.entityData.set(DATA_SLEEPING, sleeping);
    }

    @Override
    public boolean canMoveOnItsOwn() {
        return super.canMoveOnItsOwn() && !this.isSleeping();
    }

    @Override
    protected LookControl createLookControl() {
        return new OwlLookControl(this);
    }

    public void puff() {
        this.level().playSound(null, this.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL,
                1.0F, 0.6F);
        int count = 20;
        ServerLevel server = (ServerLevel) this.level();
        RandomSource random = this.getRandom();
        OwlFeatherOptions feather = new OwlFeatherOptions(
                this.isBaby() ? OwlFeather.BABY : OwlType.skinOf(this).feather);
        for (int i = 0; i < count; i++) {
            double x = this.getRandomX(0.5);
            double y = this.getRandomY();
            double z = this.getRandomZ(0.5);

            double dx = random.nextGaussian() * 0.004;
            double dy = random.nextDouble() * 0.004;
            double dz = random.nextGaussian() * 0.004;

            server.sendParticles(feather, x, y, z, 0, dx, dy, dz, 1);
        }
    }

    @Override
    public int getMaxHeadYRot() {
        return this.isStandingStill() ? PERCHED_HEAD_SWIVEL : super.getMaxHeadYRot();
    }

    private boolean isStandingStill() {
        return this.getMode().isOnFoot() && this.getNavigation().isDone();
    }

    @Override
    public int getMaxHeadXRot() {
        return this.getMode().isOnFoot() ? super.getMaxHeadXRot() : FLYING_HEAD_PITCH;
    }

    @Override
    public int getHeadRotSpeed() {
        return this.getMaxHeadYRot() / 2;
    }

    public static AttributeSupplier.Builder makeAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.JUMP_STRENGTH, 0.64)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT_ID, OwlType.HORNED);
        builder.define(DATA_SLEEPING, false);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        OwlType.save(output, this.getOwlType());
        output.storeNullable("Delivery", Delivery.CODEC, this.delivery);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setOwlType(OwlType.load(input));
        this.delivery = input.read("Delivery", Delivery.CODEC).orElse(null);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        this.setOwlType(OwlType.forSpawn(level, this.blockPosition(), level.getBiome(this.blockPosition()), level.getRandom()));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    public OwlType getOwlType() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    public void setOwlType(OwlType variant) {
        this.entityData.set(DATA_VARIANT_ID, variant);
    }

    @Override
    public boolean canSitOnShoulder() {
        return this.isBaby() && super.canSitOnShoulder();
    }

    @Override
    public boolean canMoveByFlying() {
        return !this.isBaby() || !CommonConfigs.CHICKS_STAY_GROUNDED.get();
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        OwlEntity chick = OwlMod.OWL.get().create(level, EntitySpawnReason.BREEDING);
        if (chick != null) {
            chick.setOwlType(this.random.nextBoolean() || !(partner instanceof OwlEntity other)
                    ? this.getOwlType() : other.getOwlType());
            EntityReference<LivingEntity> owner = this.getOwnerReference();
            if (owner != null) {
                chick.setOwnerReference(owner);
                chick.setTame(true, true);
            }
        }
        return chick;
    }

    @Override
    protected Brain<OwlEntity> makeBrain(Brain.Packed packed) {
        return OwlAi.makeBrain(this, packed);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<OwlEntity> getBrain() {
        return (Brain<OwlEntity>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("owlBrain");
        this.getBrain().tick(level, this);
        OwlAi.updateActivity(this);
        profiler.pop();
        Deliveries.dropParcelIfSatDown(this);
        Deliveries.endErrandIfEmptyHanded(this);
        super.customServerAiStep(level);
    }

    @Override
    public void setOrderedToSit(boolean orderedToSit) {
        if (orderedToSit && !this.getTalonItem().isEmpty()) {
            return;
        }
        super.setOrderedToSit(orderedToSit);
    }

    public ItemStack getTalonItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public void setTalonItem(ItemStack stack) {
        this.setItemSlot(EquipmentSlot.MAINHAND, stack);

        this.setDropChance(EquipmentSlot.MAINHAND, 2.0F);
    }

    public void attend(Player player) {
        if (this.isBaby()) {
            return;
        }
        this.getBrain().setMemory(OwlMod.ATTENDED_PLAYER.get(), player);
    }

    public void attend(Player player, int ticks) {
        if (this.isBaby()) {
            return;
        }
        this.getBrain().setMemoryWithExpiry(OwlMod.ATTENDED_PLAYER.get(), player, ticks);
    }

    public void stopAttending() {
        this.getBrain().eraseMemory(OwlMod.ATTENDED_PLAYER.get());
    }

    public boolean isAttending() {
        return this.getBrain().hasMemoryValue(OwlMod.ATTENDED_PLAYER.get());
    }

    @Nullable
    public Delivery delivery() {
        return this.delivery;
    }

    public void setDelivery(@Nullable Delivery delivery) {
        this.delivery = delivery;
    }

    public boolean isDelivering() {
        return this.delivery != null;
    }

    public boolean isVisitingNest() {
        return this.getBrain().hasMemoryValue(OwlMod.VISIT_NEST.get());
    }

    @Override
    public boolean isOnErrand() {
        return this.isVisitingNest() || this.isDelivering();
    }
}
