package net.mehvahdjukaar.courier_owls.owls.delivery;

import net.mehvahdjukaar.courier_owls.bird.trip.ArrivalStyle;
import net.mehvahdjukaar.courier_owls.configs.CommonConfigs;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.mehvahdjukaar.courier_owls.owls.nest.NestClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DeliverItem extends Behavior<OwlEntity> {
    private static final float SPEED = 1.0F;
    private static final int CLOSE_ENOUGH = 1;

    private static final double HANDOVER_RANGE = 8.0;

    private static final double ARRIVED = 2.0;

    private static final int WAIT_TICKS = 20 * 20;

    private static final int LEG_TIMEOUT = 20 * 60 * 3;

    private static final double FLY_IT_RANGE = 64.0;

    private static final double SEEN_RANGE = 32.0;

    private static final int EXIT_TICKS = 20 * 10;

    private static final double CHASE_DRIFT = 4.0;

    public DeliverItem() {
        super(Map.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED,
                MemoryModuleType.HURT_BY_ENTITY, MemoryStatus.REGISTERED,
                OwlMod.ATTENDED_PLAYER.get(), MemoryStatus.REGISTERED,
                OwlMod.VISIT_NEST.get(), MemoryStatus.REGISTERED,
                OwlMod.NEST_POS.get(), MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, OwlEntity owl) {
        return onAnErrand(owl);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, OwlEntity owl, long gameTime) {
        return onAnErrand(owl);
    }

    private static boolean onAnErrand(OwlEntity owl) {
        return owl.isDelivering() && !owl.getTalonItem().isEmpty();
    }

    @Override
    protected void tick(ServerLevel level, OwlEntity owl, long gameTime) {
        Delivery delivery = owl.delivery();
        if (delivery == null) {
            return;
        }
        boolean stillOnTheirSide = delivery.leg() == Delivery.Leg.OUTBOUND
                || delivery.leg() == Delivery.Leg.ATTEND_DELIVERING;
        if (stillOnTheirSide && hurtByTheAddressee(level, owl, delivery)) {
            turnBack(owl, delivery, gameTime);
            return;
        }
        BlockPos exit = delivery.exit().orElse(null);
        if (exit != null) {
            tickLeaving(level, owl, delivery, exit, gameTime);
            return;
        }
        switch (delivery.leg()) {
            case ATTEND_CONFIRM -> tickConfirm(level, owl, delivery, gameTime);
            case OUTBOUND -> tickOutbound(level, owl, delivery, gameTime);
            case ATTEND_DELIVERING -> tickDelivering(level, owl, delivery, gameTime);
            case RETURNING -> tickReturning(level, owl, delivery, gameTime);
            case ATTEND_HOME -> tickHome(level, owl, delivery, gameTime);
        }
    }

    private static void tickConfirm(ServerLevel level, OwlEntity owl, Delivery delivery, long gameTime) {
        if (!delivery.legOver(gameTime)) {
            return;
        }
        beginLeg(owl, delivery, Delivery.Leg.OUTBOUND, gameTime);

        Player addressee = addressee(level, delivery);
        if (addressee != null) {
            Deliveries.announceDeparture(owl, addressee);
        }
    }

    private static void tickOutbound(ServerLevel level, OwlEntity owl, Delivery delivery, long gameTime) {
        if (delivery.addressee().isEmpty()) {
            tickOutboundHome(level, owl, delivery, gameTime);
            return;
        }
        Player addressee = addressee(level, delivery);
        if (addressee == null) {
            turnBack(owl, delivery, gameTime);
            return;
        }

        boolean showingOff = addressee == owner(owl) && !delivery.puffed();
        if (showingOff) {
            leaveAndPuff(level, owl, delivery, gameTime);
            return;
        }
        if (puffedTo(level, owl, delivery, addressee.position(), gameTime)) {
            return;
        }
        if (owl.closerThan(addressee, HANDOVER_RANGE)) {
            waitWith(owl, delivery, Delivery.Leg.ATTEND_DELIVERING, addressee, gameTime);
            Deliveries.announceArrival(owl, addressee);
        } else {
            flyTo(owl, BlockPos.containing(addressee.position()), CHASE_DRIFT);
        }
    }

    private static void tickOutboundHome(ServerLevel level, OwlEntity owl, Delivery delivery, long gameTime) {
        BlockPos nest = NestClaim.of(owl);
        if (nest == null) {
            turnBack(owl, delivery, gameTime);
            return;
        }
        if (puffedTo(level, owl, delivery, Vec3.atCenterOf(nest), gameTime)) {
            return;
        }
        if (stuck(owl, delivery, gameTime)) {
            turnBack(owl, delivery, gameTime);
            return;
        }
        if (owl.isVisitingNest()) {
            return;
        }
        if (delivery.triedHollow()) {
            turnBack(owl, delivery, gameTime);
            return;
        }
        owl.setDelivery(delivery.afterTryingHollow());
        owl.getBrain().setMemory(OwlMod.VISIT_NEST.get(), Unit.INSTANCE);
    }

    private static void tickDelivering(ServerLevel level, OwlEntity owl, Delivery delivery, long gameTime) {
        Player addressee = addressee(level, delivery);
        if (addressee == null || delivery.legOver(gameTime)) {
            turnBack(owl, delivery, gameTime);
            return;
        }
        owl.attend(addressee);
    }

    private static void tickReturning(ServerLevel level, OwlEntity owl, Delivery delivery, long gameTime) {
        Player owner = owner(owl);
        if (owner == null) {
            leaveAndPuff(level, owl, delivery, gameTime);
            return;
        }
        if (owl.closerThan(owner, HANDOVER_RANGE)) {
            waitWith(owl, delivery, Delivery.Leg.ATTEND_HOME, owner, gameTime);
            return;
        }
        if (!puffedTo(level, owl, delivery, owner.position(), gameTime)) {
            flyTo(owl, BlockPos.containing(owner.position()), CHASE_DRIFT);
        }
    }

    private static void tickHome(ServerLevel level, OwlEntity owl, Delivery delivery, long gameTime) {
        Player owner = owner(owl);
        if (owner == null) {
            leaveAndPuff(level, owl, delivery, gameTime);
            return;
        }
        if (delivery.legOver(gameTime)) {
            Deliveries.dropParcel(owl);
            owl.setDelivery(null);
            return;
        }
        owl.attend(owner);
    }

    private static boolean puffedTo(ServerLevel level, OwlEntity owl, Delivery delivery, Vec3 destination,
                                    long gameTime) {
        boolean canFlyIt = owl.position().closerThan(destination, FLY_IT_RANGE) && !stuck(owl, delivery, gameTime);
        if (delivery.puffed() || canFlyIt) {
            return false;
        }
        leaveAndPuff(level, owl, delivery, gameTime);
        return true;
    }

    public static void puffIfUnloading(ServerLevel level, OwlEntity owl) {
        Delivery delivery = owl.delivery();
        if (delivery == null || delivery.puffed() || owl.getTalonItem().isEmpty()) {
            return;
        }

        puffNow(level, owl, delivery);
    }

    private static void leaveAndPuff(ServerLevel level, OwlEntity owl, Delivery delivery, long gameTime) {
        BlockPos exit = level.getNearestPlayer(owl, SEEN_RANGE) == null
                ? null
                : PuffSpot.toLeaveFrom(level, owl);
        if (exit == null) {
            puffNow(level, owl, delivery);
            return;
        }
        letGo(owl);
        owl.setDelivery(delivery.leavingFrom(exit, gameTime + EXIT_TICKS));
    }

    private static void tickLeaving(ServerLevel level, OwlEntity owl, Delivery delivery, BlockPos exit, long gameTime) {
        if (gotTo(owl, Vec3.atBottomCenterOf(exit)) || stuck(owl, delivery, gameTime)) {
            puffNow(level, owl, delivery);
            return;
        }
        flyTo(owl, exit, 0.0);
    }

    private static void puffNow(ServerLevel level, OwlEntity owl, Delivery delivery) {
        DeliveringOwlsStorage storage = DeliveringOwlsStorage.of(level);
        Player person = personFor(level, owl, delivery);
        if (person != null) {
            long arrivesAt = filePuff(level, owl, delivery, person.position());
            storage.puffOutTo(owl, person, arrivesAt);
            return;
        }
        BlockPos place = placeFor(owl, delivery);
        if (place != null) {
            long arrivesAt = filePuff(level, owl, delivery, Vec3.atCenterOf(place));
            storage.puffOutTo(owl, place, arrivesAt);
            return;
        }
        waitForTheOwner(level, owl, delivery, storage);
    }

    @Nullable
    private static Player personFor(ServerLevel level, OwlEntity owl, Delivery delivery) {
        return switch (delivery.leg()) {
            case OUTBOUND, ATTEND_DELIVERING -> addressee(level, delivery);
            case ATTEND_CONFIRM, RETURNING, ATTEND_HOME -> owner(owl);
        };
    }

    @Nullable
    private static BlockPos placeFor(OwlEntity owl, Delivery delivery) {
        return delivery.leg() == Delivery.Leg.OUTBOUND ? NestClaim.of(owl) : null;
    }

    private static void waitForTheOwner(ServerLevel level, OwlEntity owl, Delivery delivery,
                                        DeliveringOwlsStorage storage) {
        EntityReference<LivingEntity> ownerRef = owl.getOwnerReference();
        if (ownerRef == null) {
            owl.setDelivery(null);
            return;
        }
        boolean canShelveIt = !delivery.triedHollow() && NestClaim.of(owl) != null;
        if (CommonConfigs.STRANDED_PARCEL_GOES_HOME.get() && canShelveIt) {
            owl.setDelivery(delivery.divertedHome(level.getGameTime() + LEG_TIMEOUT));
            return;
        }

        long arrivesAt = filePuff(level, owl, delivery, owl.position());
        storage.puffOutWaitingFor(owl, ownerRef.getUUID(), arrivesAt);
    }

    private static long filePuff(ServerLevel level, OwlEntity owl, Delivery delivery, Vec3 destination) {
        long arrivesAt = DeliveringOwlsStorage.arrivalTime(level, owl, destination);
        owl.setDelivery(delivery.puffedUntil(arrivesAt + LEG_TIMEOUT));
        return arrivesAt;
    }

    private static void waitWith(OwlEntity owl, Delivery delivery, Delivery.Leg leg, Player player, long gameTime) {
        owl.trip().cancel();
        owl.releaseHold();
        owl.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        owl.attend(player);
        owl.setDelivery(delivery.on(leg, gameTime + WAIT_TICKS));
    }

    private static void turnBack(OwlEntity owl, Delivery delivery, long gameTime) {
        beginLeg(owl, delivery, Delivery.Leg.RETURNING, gameTime);
    }

    private static boolean stuck(OwlEntity owl, Delivery delivery, long gameTime) {
        return owl.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                || delivery.legOver(gameTime);
    }

    private static void beginLeg(OwlEntity owl, Delivery delivery, Delivery.Leg leg, long gameTime) {
        letGo(owl);
        owl.setDelivery(delivery.on(leg, gameTime + LEG_TIMEOUT));
    }

    private static void letGo(OwlEntity owl) {
        Brain<?> brain = owl.getBrain();
        owl.stopAttending();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    private static void flyTo(OwlEntity owl, BlockPos target, double drift) {
        Brain<?> brain = owl.getBrain();
        WalkTarget current = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (current != null && current.getTarget().currentBlockPosition().distSqr(target) <= drift * drift) {
            return;
        }
        owl.trip().planArrival(target, ArrivalStyle.HOVER);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, SPEED, CLOSE_ENOUGH));
    }

    private static boolean gotTo(OwlEntity owl, Vec3 target) {
        return owl.position().closerThan(target, ARRIVED)
                || (owl.isHovering() && !owl.trip().isTraveling());
    }

    private static boolean hurtByTheAddressee(ServerLevel level, OwlEntity owl, Delivery delivery) {
        Player addressee = addressee(level, delivery);
        return addressee != null && owl.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY)
                .filter(addressee::equals).isPresent();
    }

    @Nullable
    private static Player addressee(ServerLevel level, Delivery delivery) {
        return delivery.addressee()
                .map(level::getPlayerByUUID)
                .filter(player -> player.isAlive() && !player.isSpectator())
                .orElse(null);
    }

    @Nullable
    private static Player owner(OwlEntity owl) {
        return owl.getOwner() instanceof Player player && player.isAlive() ? player : null;
    }

    @Override
    protected void stop(ServerLevel level, OwlEntity owl, long gameTime) {
        owl.stopAttending();
        owl.releaseHold();
        owl.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}
