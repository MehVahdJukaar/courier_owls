package net.mehvahdjukaar.courier_owls.owls.entities;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.bird.brain.BirdMoveToTargetSink;
import net.mehvahdjukaar.courier_owls.bird.brain.FindPerch;
import net.mehvahdjukaar.courier_owls.bird.brain.FollowOwner;
import net.mehvahdjukaar.courier_owls.bird.brain.LandOnOwnersShoulder;
import net.mehvahdjukaar.courier_owls.bird.brain.PatrolFlight;
import net.mehvahdjukaar.courier_owls.bird.brain.PerchSearch;
import net.mehvahdjukaar.courier_owls.bird.brain.RandomStroll;
import net.mehvahdjukaar.courier_owls.bird.brain.StayOnPerch;
import net.mehvahdjukaar.courier_owls.bird.brain.SitWhenOrdered;
import net.mehvahdjukaar.courier_owls.bird.brain.WatchTemptingPlayer;
import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.swoop.PrepareSwoop;
import net.mehvahdjukaar.courier_owls.owls.delivery.AttendPlayer;
import net.mehvahdjukaar.courier_owls.owls.delivery.DeliverItem;
import net.mehvahdjukaar.courier_owls.owls.nest.TendNest;
import net.mehvahdjukaar.courier_owls.owls.nest.VisitNest;
import net.mehvahdjukaar.courier_owls.owls.swoop.SwoopDiveAttack;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BabyFollowAdult;
import net.minecraft.world.entity.ai.behavior.FollowTemptation;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class OwlAi {
    private static final float SWOOP_SPEED = 0.8F;

    private static final long HUNTING_COOLDOWN_TICKS = 2400L;

    private static final float WATCH_RANGE = 40.0F;

    private static final UniformInt WATCH_TICKS = UniformInt.of(60, 200);

    private static final int PLAYER_NOTICE_TICKS = 40;
    private static final int PREY_NOTICE_TICKS = 20;
    private static final int OTHER_NOTICE_TICKS = 300;

    private static final float TEMPTATION_SPEED = 0.7F;

    private static final int PERCH_SEARCH_RETRY_TICKS = 60;

    private static final int PERCH_URGE_AVERAGE_TICKS = 600;

    private static final float BREEDING_SPEED = 0.9F;

    private static final int BREEDING_CLOSE_ENOUGH = 2;
    private static final float CHICK_FOLLOW_SPEED = 1.1F;
    private static final UniformInt CHICK_FOLLOW_RANGE = UniformInt.of(3, 10);

    private static final float OWNER_FOLLOW_SPEED = 1.0F;
    private static final float OWNER_FOLLOW_START = 10.0F;
    private static final float OWNER_FOLLOW_STOP = 2.0F;

    private static final float SHOULDER_RANGE = 12.0F;
    private static final float SHOULDER_APPROACH_SPEED = 1.1F;

    private static final float PATROL_SPEED = 0.85F;
    private static final UniformInt PATROL_DURATION = UniformInt.of(600, 1400);

    private static final int PATROL_URGE_AVERAGE_TICKS = 200;

    private static final int PATROL_COOLDOWN_TICKS = 600;
    private static final double PATROL_RANGE = 32.0;

    private static final double HOME_RANGE = 64.0;
    private static final double HOME_HOP = 16.0;

    private static final float STROLL_SPEED = 0.9F;
    private static final int STROLL_URGE_AVERAGE_TICKS = 1200;
    private static final int STROLL_WET_URGE_AVERAGE_TICKS = 60;

    private static final float PERCH_APPROACH_SPEED = 1.0F;
    private static final float NEST_APPROACH_SPEED = 1.0F;
    private static final UniformInt PERCH_STAY_TICKS = UniformInt.of(300, 1200);

    private static final UniformInt ROOST_STAY_TICKS = UniformInt.of(6000, 12000);

    private static final int ROOST_URGE_AVERAGE_TICKS = 100;

    static final List<MemoryModuleType<?>> MEMORY_TYPES = List.of(
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.PATH,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.NEAREST_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_ATTACKABLE,
            MemoryModuleType.ATTACK_TARGET,
            MemoryModuleType.HAS_HUNTING_COOLDOWN,
            MemoryModuleType.TEMPTING_PLAYER,
            MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
            MemoryModuleType.IS_TEMPTED,
            MemoryModuleType.BREED_TARGET,
            MemoryModuleType.NEAREST_VISIBLE_ADULT,

            MemoryModuleType.IS_PANICKING,

            MemoryModuleType.HURT_BY,
            BirdMod.PERCH_POS.get(),
            OwlMod.SWOOP_GO.get(),
            OwlMod.ATTENDED_PLAYER.get(),
            OwlMod.NEST_POS.get(),
            OwlMod.VISIT_NEST.get(),
            OwlMod.ROOST_POS.get());

    static final List<SensorType<? extends Sensor<? super OwlEntity>>> SENSOR_TYPES = List.of(

            BirdMod.BIRD_SIGHT.get(),
            SensorType.NEAREST_ADULT,
            SensorType.HURT_BY,
            BirdMod.BIRD_TEMPTATION_SENSOR.get(),
            OwlMod.OWL_PREY_SENSOR.get());

    private static final int DUSK = 12000;

    private static final int OWN_DAWN_SPREAD = 2400;

    private static final Brain.Provider<OwlEntity> BRAIN_PROVIDER =
            Brain.provider(MEMORY_TYPES, SENSOR_TYPES, owl -> activities());

    static Brain<OwlEntity> makeBrain(OwlEntity owl, Brain.Packed packed) {
        Brain<OwlEntity> brain = BRAIN_PROVIDER.makeBrain(owl, packed);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static List<ActivityData<OwlEntity>> activities() {
        return List.of(coreActivity(), deliveryActivity(), idleActivity(), restActivity(), fightActivity());
    }

    private static ActivityData<OwlEntity> coreActivity() {
        return ActivityData.create(Activity.CORE, ImmutableList.of(
                Pair.of(0, new SitWhenOrdered(PerchSearch.DEFAULT, PERCH_APPROACH_SPEED)),
                Pair.of(1, new LookAtTargetSink(45, 90)),
                Pair.of(2, new BirdMoveToTargetSink()),

                Pair.of(3, new AttendPlayer())));
    }

    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super OwlEntity>>> allHoursBehaviors() {
        return ImmutableList.of(

                Pair.of(9, new WatchNearby(WATCH_RANGE, WATCH_TICKS, PLAYER_NOTICE_TICKS,
                        PREY_NOTICE_TICKS, OTHER_NOTICE_TICKS)),

                Pair.of(10, new TendNest()),
                Pair.of(11, new WatchTemptingPlayer()),
                Pair.of(12, new FollowTemptation(owl -> TEMPTATION_SPEED)),
                Pair.of(13, new AnimalMakeLove(OwlMod.OWL.get(), BREEDING_SPEED, BREEDING_CLOSE_ENOUGH)),
                Pair.of(15, new FollowOwner(OWNER_FOLLOW_SPEED, OWNER_FOLLOW_START, OWNER_FOLLOW_STOP)),

                Pair.of(16, new LandOnOwnersShoulder(SHOULDER_APPROACH_SPEED, SHOULDER_RANGE)),

                Pair.of(17, new VisitNest(NEST_APPROACH_SPEED)));
    }

    private static ActivityData<OwlEntity> idleActivity() {
        return ActivityData.create(Activity.IDLE, ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super OwlEntity>>>builder()
                .addAll(allHoursBehaviors())
                .add(
                Pair.of(0, StartAttacking.create(
                        (level, owl) -> !owl.isAttending() && !owl.isVisitingNest(), OwlAi::findNearestPrey)),

                Pair.of(14, BabyFollowAdult.create(CHICK_FOLLOW_RANGE, CHICK_FOLLOW_SPEED)),

                Pair.of(18, new PatrolFlight(PerchSearch.DEFAULT, Activity.IDLE, PATROL_SPEED, PATROL_DURATION,
                        PATROL_URGE_AVERAGE_TICKS, PATROL_COOLDOWN_TICKS, PATROL_RANGE,
                        OwlAi::nestAnchor)),
                Pair.of(19, new FindPerch(PerchSearch.DEFAULT,
                        PERCH_SEARCH_RETRY_TICKS, PERCH_URGE_AVERAGE_TICKS,
                        OwlAi::nestAnchor, HOME_RANGE, HOME_HOP)),
                Pair.of(20, new StayOnPerch(Activity.IDLE, PERCH_APPROACH_SPEED, PERCH_STAY_TICKS)),

                Pair.of(21, new RandomStroll(STROLL_SPEED, STROLL_URGE_AVERAGE_TICKS,
                        STROLL_WET_URGE_AVERAGE_TICKS)),
                Pair.of(22, new OwlSleep(Activity.IDLE)))
                .build());
    }

    private static ActivityData<OwlEntity> restActivity() {
        return ActivityData.create(Activity.REST, ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super OwlEntity>>>builder()
                .addAll(allHoursBehaviors())
                .add(

                Pair.of(19, new FindPerch(PerchSearch.DEFAULT,
                        PERCH_SEARCH_RETRY_TICKS, ROOST_URGE_AVERAGE_TICKS,
                        OwlAi::nestAnchor, HOME_RANGE, HOME_HOP)),
                Pair.of(20, new StayOnPerch(Activity.REST, PERCH_APPROACH_SPEED, ROOST_STAY_TICKS)),

                Pair.of(21, RandomStroll.wetOnly(STROLL_SPEED, STROLL_WET_URGE_AVERAGE_TICKS)),

                Pair.of(22, new OwlSleep(Activity.REST)))
                .build());
    }

    private static ActivityData<OwlEntity> deliveryActivity() {
        return ActivityData.create(OwlMod.DELIVERY.get(), ImmutableList.of(
                Pair.of(0, new DeliverItem()),

                Pair.of(1, new VisitNest(NEST_APPROACH_SPEED))));
    }

    private static ActivityData<OwlEntity> fightActivity() {
        return ActivityData.create(Activity.FIGHT, ImmutableList.of(
                Pair.of(0, StopAttackingIfTargetInvalid.create()),
                Pair.of(1, new PrepareSwoop(SWOOP_SPEED)),
                Pair.of(2, new SwoopDiveAttack())),
                Set.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)));
    }

    static void updateActivity(OwlEntity owl) {
        Brain<OwlEntity> brain = owl.getBrain();
        Activity before = brain.getActiveNonCoreActivity().orElse(null);
        Activity scheduled = scheduledActivity(owl);

        brain.setActiveActivityToFirstValid(owl.isDelivering()
                ? ImmutableList.of(OwlMod.DELIVERY.get(), Activity.FIGHT, scheduled, Activity.IDLE)
                : ImmutableList.of(Activity.FIGHT, scheduled, Activity.IDLE));
        Activity after = brain.getActiveNonCoreActivity().orElse(null);
        if (before == Activity.FIGHT && after != Activity.FIGHT) {
            brain.setMemoryWithExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN, true, HUNTING_COOLDOWN_TICKS);
        }
    }

    private static Activity scheduledActivity(OwlEntity owl) {
        int time = timeOfDay(owl.level());
        boolean night = time >= DUSK || time < ownDawn(owl);
        return night ? Activity.IDLE : Activity.REST;
    }

    private static int ownDawn(OwlEntity owl) {
        return Math.floorMod(owl.getUUID().hashCode(), OWN_DAWN_SPREAD);
    }

    private static final int TICKS_PER_DAY = 24000;

    private static int timeOfDay(Level level) {
        return (int) (level.getOverworldClockTime() % TICKS_PER_DAY);
    }

    public static boolean isSleepTime(Level level) {
        return timeOfDay(level) < DUSK;
    }

    public static boolean isRainingAround(Level level, BlockPos pos) {
        return level.isRaining() && level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.RAIN;
    }

    @Nullable
    private static BlockPos nestAnchor(BaseBirdMob bird) {
        return bird.getBrain().getMemory(OwlMod.NEST_POS.get()).orElse(null);
    }

    private static Optional<? extends LivingEntity> findNearestPrey(ServerLevel level, OwlEntity owl) {
        return owl.getBrain().getMemory(MemoryModuleType.NEAREST_ATTACKABLE);
    }
}
