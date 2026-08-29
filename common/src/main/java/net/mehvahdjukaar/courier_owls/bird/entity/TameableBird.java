package net.mehvahdjukaar.courier_owls.bird.entity;

import net.mehvahdjukaar.courier_owls.bird.trip.LocomotionMode;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.util.ProblemReporter;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public abstract class TameableBird extends BaseBirdMob implements OwnableEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID =
            SynchedEntityData.defineId(TameableBird.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNERUUID_ID =
            SynchedEntityData.defineId(TameableBird.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    private static final EntityDataAccessor<Boolean> DATA_INTERESTED =
            SynchedEntityData.defineId(TameableBird.class, EntityDataSerializers.BOOLEAN);

    private static final byte SITTING_FLAG = 1;
    private static final byte TAME_FLAG = 4;

    private static final int TAMING_PARTICLES = 7;
    private static final float MAX_LEASH_DISTANCE_WHILE_SITTING = 10.0F;

    private static final int RIDE_COOLDOWN = 100;

    private static final int TELEPORT_ATTEMPTS = 10;

    private boolean orderedToSit;
    private int ticksSinceRide;

    protected TameableBird(EntityType<? extends TameableBird> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte) 0);
        builder.define(DATA_OWNERUUID_ID, Optional.empty());
        builder.define(DATA_INTERESTED, false);
    }

    @Override
    public boolean isInterested() {
        return this.entityData.get(DATA_INTERESTED);
    }

    @Override
    public boolean canMoveOnItsOwn() {
        return !this.isInSittingPose();
    }

    protected abstract boolean isTempted();

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);

        boolean perched = this.getMode() == LocomotionMode.PERCHED;
        this.entityData.set(DATA_INTERESTED, perched && this.isTempted());
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference.store(this.getOwnerReference(), output, "Owner");
        output.putBoolean("Sitting", this.orderedToSit);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        EntityReference<LivingEntity> owner = EntityReference.readWithOldOwnerConversion(input, "Owner", this.level());
        if (owner != null) {
            try {
                this.setOwnerReference(owner);
                this.setTame(true, false);
            } catch (Throwable ignored) {
                this.setTame(false, true);
            }
        }
        this.setOrderedToSit(input.getBooleanOr("Sitting", false));
        this.setInSittingPose(this.orderedToSit);
    }

    public boolean isTameFood(ItemStack stack) {
        return this.isFood(stack);
    }

    public boolean isTemptedBy(ItemStack stack) {
        return this.isTame() ? this.isFood(stack) : this.isTameFood(stack);
    }

    protected int tamingOdds() {
        return 3;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.level().isClientSide()) {
            boolean reacts = this.isTame() ? this.isOwnedBy(player) : this.isTameFood(stack);

            return reacts ? InteractionResult.SUCCESS : super.mobInteract(player, hand);
        }

        if (this.isTame() && this.isOwnedBy(player) && this.isFood(stack) && !this.isBaby()) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            this.usePlayerItem(player, hand, stack);
            if (food != null && this.getHealth() < this.getMaxHealth()) {
                this.heal(food.nutrition());
            }
            if (this.getAge() == 0 && this.canFallInLove()) {
                this.setInLove(player);
            }
            return InteractionResult.CONSUME;
        }
        if (!this.isTame() && this.isTameFood(stack)) {
            this.usePlayerItem(player, hand, stack);
            this.tryTame(player);
            this.setPersistenceRequired();
            return InteractionResult.CONSUME;
        }

        InteractionResult result = super.mobInteract(player, hand);

        if ((!result.consumesAction() || this.isBaby()) && this.isOwnedBy(player)) {
            this.setOrderedToSit(!this.isOrderedToSit());
            this.getNavigation().stop();
            return InteractionResult.SUCCESS.withoutItem();
        }
        return result;
    }

    @Override
    public boolean canFallInLove() {
        return this.isTame() && super.canFallInLove();
    }

    protected void tryTame(Player player) {
        if (this.random.nextInt(this.tamingOdds()) == 0) {
            this.tame(player);
            this.level().broadcastEntityEvent(this, EntityEvent.TAMING_SUCCEEDED);
        } else {
            this.level().broadcastEntityEvent(this, EntityEvent.TAMING_FAILED);
        }
    }

    public void tame(Player player) {
        this.setTame(true, true);
        this.setOwner(player);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger(serverPlayer, this);
        }
    }

    protected void spawnTamingParticles(boolean tamed) {
        ParticleOptions particle = tamed ? ParticleTypes.HEART : ParticleTypes.SMOKE;
        for (int i = 0; i < TAMING_PARTICLES; ++i) {
            double dx = this.random.nextGaussian() * 0.02;
            double dy = this.random.nextGaussian() * 0.02;
            double dz = this.random.nextGaussian() * 0.02;
            this.level().addParticle(particle, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), dx, dy, dz);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.TAMING_SUCCEEDED) {
            this.spawnTamingParticles(true);
        } else if (id == EntityEvent.TAMING_FAILED) {
            this.spawnTamingParticles(false);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        ++this.ticksSinceRide;
        super.tick();
    }

    public boolean canSitOnShoulder() {
        return this.ticksSinceRide > RIDE_COOLDOWN;
    }

    public boolean isOnErrand() {
        return false;
    }

    public boolean setEntityOnShoulder(ServerPlayer player) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, this.registryAccess());
            this.saveWithoutId(output);
            output.putString("id", this.getEncodeId());
            if (player.setEntityOnShoulder(output.buildResult())) {
                this.discard();
                return true;
            }
        }
        return false;
    }

    public final boolean unableToMoveToOwner() {
        LivingEntity owner = this.getOwner();
        return this.isOrderedToSit() || this.isPassenger() || this.mayBeLeashed()
                || (owner != null && owner.isSpectator());
    }

    public void tryToTeleportToOwner() {
        LivingEntity owner = this.getOwner();
        if (owner != null) {
            this.teleportToAroundBlockPos(owner.blockPosition());
        }
    }

    private void teleportToAroundBlockPos(BlockPos pos) {
        for (int i = 0; i < TELEPORT_ATTEMPTS; ++i) {
            int dx = this.random.nextIntBetweenInclusive(-3, 3);
            int dz = this.random.nextIntBetweenInclusive(-3, 3);

            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            int dy = this.random.nextIntBetweenInclusive(-1, 1);
            if (this.maybeTeleportTo(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz)) {
                return;
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);

        if (WalkNodeEvaluator.getPathTypeStatic(this, pos) != PathType.WALKABLE) {
            return false;
        }
        if (!this.level().noCollision(this, this.getBoundingBox().move(pos.subtract(this.blockPosition())))) {
            return false;
        }
        this.snapTo(x + 0.5, y, z + 0.5, this.getYRot(), this.getXRot());
        this.getNavigation().stop();
        return true;
    }

    public boolean isTame() {
        return (this.entityData.get(DATA_FLAGS_ID) & TAME_FLAG) != 0;
    }

    public void setTame(boolean tame, boolean applyTamingSideEffects) {
        this.setFlag(TAME_FLAG, tame);
        if (applyTamingSideEffects) {
            this.applyTamingSideEffects();
        }
    }

    protected void applyTamingSideEffects() {
    }

    public boolean isInSittingPose() {
        return (this.entityData.get(DATA_FLAGS_ID) & SITTING_FLAG) != 0;
    }

    public void setInSittingPose(boolean sitting) {
        this.setFlag(SITTING_FLAG, sitting);
    }

    private void setFlag(byte flag, boolean value) {
        byte flags = this.entityData.get(DATA_FLAGS_ID);
        this.entityData.set(DATA_FLAGS_ID, (byte) (value ? flags | flag : flags & ~flag));
    }

    public boolean isOrderedToSit() {
        return this.orderedToSit;
    }

    public void setOrderedToSit(boolean orderedToSit) {
        this.orderedToSit = orderedToSit;
    }

    @Nullable
    @Override
    public EntityReference<LivingEntity> getOwnerReference() {
        return this.entityData.get(DATA_OWNERUUID_ID).orElse(null);
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.entityData.set(DATA_OWNERUUID_ID, Optional.ofNullable(owner).map(EntityReference::of));
    }

    public void setOwnerReference(@Nullable EntityReference<LivingEntity> owner) {
        this.entityData.set(DATA_OWNERUUID_ID, Optional.ofNullable(owner));
    }

    public boolean isOwnedBy(LivingEntity entity) {
        return entity == this.getOwner();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isOwnedBy(target) && super.canAttack(target);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        }
        this.setOrderedToSit(false);
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    @Override
    public double leashSnapDistance() {
        return this.isInSittingPose() ? MAX_LEASH_DISTANCE_WHILE_SITTING : super.leashSnapDistance();
    }

    @Override
    public boolean checkElasticInteractions(Entity leashHolder, Leashable.LeashData leashData) {
        return !this.isInSittingPose() && super.checkElasticInteractions(leashHolder, leashData);
    }

    @Override
    public PlayerTeam getTeam() {
        if (this.isTame()) {
            LivingEntity owner = this.getOwner();
            if (owner != null) {
                return owner.getTeam();
            }
        }
        return super.getTeam();
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        if (this.isTame()) {
            LivingEntity owner = this.getOwner();
            if (entity == owner) {
                return true;
            }
            if (owner != null) {
                return owner.isAlliedTo(entity);
            }
        }
        return super.considersEntityAsAlly(entity);
    }

    @Override
    public void die(DamageSource cause) {
        if (this.level() instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES)
                && this.getOwner() instanceof ServerPlayer owner) {
            owner.sendSystemMessage(this.getCombatTracker().getDeathMessage());
        }
        super.die(cause);
    }
}
